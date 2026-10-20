package com.akash.agni.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.akash.agni.data.FoodLog
import com.akash.agni.data.Meal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Health Connect is the single sync point: Samsung Health (with your Galaxy Watch) and Google Fit
 * both write into it. Every call is defensive: if Health Connect is missing or a permission was
 * revoked, the call returns null/false instead of crashing.
 */
class HealthConnectManager(private val context: Context) {

    enum class Status { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
    )

    fun status(): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.UNAVAILABLE
    }

    private val client: HealthConnectClient? by lazy {
        if (status() == Status.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Opens the Play Store page to install or update Health Connect (needed on Android 13 and lower). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    suspend fun granted(): Set<String> =
        runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull() ?: emptySet()

    suspend fun hasAll(): Boolean = granted().containsAll(permissions)

    private suspend fun has(permission: String) = granted().contains(permission)

    data class Today(val steps: Long, val activeKcal: Double)

    suspend fun today(): Today? {
        val c = client ?: return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val canSteps = has(HealthPermission.getReadPermission(StepsRecord::class))
        val canKcal = has(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
        if (!canSteps && !canKcal) return null
        return runCatching {
            val metrics = buildSet<AggregateMetric<*>> {
                if (canSteps) add(StepsRecord.COUNT_TOTAL)
                if (canKcal) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
            val r = c.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, Instant.now())))
            Today(
                steps = if (canSteps) r[StepsRecord.COUNT_TOTAL] ?: 0L else 0L,
                activeKcal = if (canKcal) r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0 else 0.0,
            )
        }.getOrNull()
    }

    /**
     * Latest heart-rate sample in the last few minutes. Samsung Health syncs watch data to Health
     * Connect in batches, so during a workout this can lag behind the watch by a few minutes.
     */
    suspend fun latestHeartRate(withinMinutes: Long = 10): Int? {
        val c = client ?: return null
        if (!has(HealthPermission.getReadPermission(HeartRateRecord::class))) return null
        return runCatching {
            val now = Instant.now()
            val res = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(withinMinutes, ChronoUnit.MINUTES), now),
                )
            )
            res.records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        }.getOrNull()
    }

    data class SessionStats(val avgHr: Int?, val activeKcal: Double?)

    suspend fun sessionStats(start: Instant, end: Instant): SessionStats {
        val c = client ?: return SessionStats(null, null)
        val canHr = has(HealthPermission.getReadPermission(HeartRateRecord::class))
        val canKcal = has(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
        if (!canHr && !canKcal) return SessionStats(null, null)
        return runCatching {
            val metrics = buildSet<AggregateMetric<*>> {
                if (canHr) add(HeartRateRecord.BPM_AVG)
                if (canKcal) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
            val r = c.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, end)))
            SessionStats(
                avgHr = if (canHr) r[HeartRateRecord.BPM_AVG]?.toInt() else null,
                activeKcal = if (canKcal) r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories else null,
            )
        }.getOrDefault(SessionStats(null, null))
    }

    /** Most recent weight from any app (e.g. a Samsung Health scale entry), last 60 days. */
    suspend fun latestWeight(): Pair<LocalDate, Double>? {
        val c = client ?: return null
        if (!has(HealthPermission.getReadPermission(WeightRecord::class))) return null
        return runCatching {
            val now = Instant.now()
            val res = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(60, ChronoUnit.DAYS), now),
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            res.records.firstOrNull()?.let {
                it.time.atZone(ZoneId.systemDefault()).toLocalDate() to it.weight.inKilograms
            }
        }.getOrNull()
    }

    suspend fun writeWorkout(sessionId: Long, title: String, start: Instant, end: Instant, cardioOnly: Boolean): Boolean {
        val c = client ?: return false
        if (!has(HealthPermission.getWritePermission(ExerciseSessionRecord::class))) return false
        return runCatching {
            c.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = offset(start),
                        endTime = end,
                        endZoneOffset = offset(end),
                        exerciseType = if (cardioOnly) ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                        else ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = title,
                        metadata = Metadata(clientRecordId = "agni-workout-$sessionId", clientRecordVersion = 1),
                    )
                )
            )
            true
        }.getOrDefault(false)
    }

    suspend fun deleteWorkout(sessionId: Long) {
        val c = client ?: return
        if (!has(HealthPermission.getWritePermission(ExerciseSessionRecord::class))) return
        runCatching { c.deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf("agni-workout-$sessionId")) }
    }

    suspend fun writeNutrition(log: FoodLog): Boolean {
        val c = client ?: return false
        if (!has(HealthPermission.getWritePermission(NutritionRecord::class))) return false
        return runCatching {
            val start = Instant.ofEpochMilli(log.loggedAt)
            val end = start.plusSeconds(60)
            c.insertRecords(
                listOf(
                    NutritionRecord(
                        startTime = start,
                        startZoneOffset = offset(start),
                        endTime = end,
                        endZoneOffset = offset(end),
                        name = log.name,
                        mealType = mealType(log.meal),
                        energy = Energy.kilocalories(log.kcal),
                        protein = Mass.grams(log.protein),
                        totalCarbohydrate = Mass.grams(log.carbs),
                        totalFat = Mass.grams(log.fat),
                        metadata = Metadata(
                            clientRecordId = "agni-food-${log.id}",
                            clientRecordVersion = System.currentTimeMillis(),
                        ),
                    )
                )
            )
            true
        }.getOrDefault(false)
    }

    suspend fun deleteNutrition(logId: Long) {
        val c = client ?: return
        if (!has(HealthPermission.getWritePermission(NutritionRecord::class))) return
        runCatching {
            c.deleteRecords(NutritionRecord::class, emptyList(), listOf("agni-food-$logId"))
        }
    }

    suspend fun writeWeight(kg: Double): Boolean {
        val c = client ?: return false
        if (!has(HealthPermission.getWritePermission(WeightRecord::class))) return false
        return runCatching {
            val now = Instant.now()
            c.insertRecords(listOf(WeightRecord(time = now, zoneOffset = offset(now), weight = Mass.kilograms(kg))))
            true
        }.getOrDefault(false)
    }

    private fun offset(t: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(t)

    private fun mealType(m: Meal) = when (m) {
        Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
        Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
        Meal.SNACKS -> MealType.MEAL_TYPE_SNACK
        Meal.DINNER -> MealType.MEAL_TYPE_DINNER
    }
}
