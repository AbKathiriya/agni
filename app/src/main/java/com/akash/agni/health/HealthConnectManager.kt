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
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.akash.agni.data.FoodLog
import com.akash.agni.data.Meal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
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
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
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

    /** A workout another app (Samsung Health, Google Fit…) recorded, with stats from its time window. */
    data class ExternalWorkout(
        val id: String,
        val app: String,
        val title: String?,
        val type: Int,
        val start: Instant,
        val end: Instant,
        val steps: Long?,
        val distanceM: Double?,
        val kcal: Double?,
        val avgHr: Int?,
    )

    /** Workouts recorded by other apps since [since]; Agni's own records are skipped. */
    suspend fun externalWorkouts(since: Instant, bmrPerDay: Double): List<ExternalWorkout> {
        val c = client ?: return emptyList()
        if (!has(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) return emptyList()
        val sessions = runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(since, Instant.now()),
                )
            ).records
        }.getOrElse { return emptyList() }
        val granted = granted()
        val canSteps = HealthPermission.getReadPermission(StepsRecord::class) in granted
        val canDistance = HealthPermission.getReadPermission(DistanceRecord::class) in granted
        val canHr = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
        return sessions
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .map { s ->
                val metrics = buildSet<AggregateMetric<*>> {
                    if (canSteps) add(StepsRecord.COUNT_TOTAL)
                    if (canDistance) add(DistanceRecord.DISTANCE_TOTAL)
                    if (canHr) add(HeartRateRecord.BPM_AVG)
                }
                val r = if (metrics.isEmpty()) null else runCatching {
                    c.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(s.startTime, s.endTime)))
                }.getOrNull()
                ExternalWorkout(
                    id = s.metadata.id,
                    app = appName(s.metadata.dataOrigin.packageName),
                    title = s.title,
                    type = s.exerciseType,
                    start = s.startTime,
                    end = s.endTime,
                    steps = r?.get(StepsRecord.COUNT_TOTAL)?.takeIf { it > 0 },
                    distanceM = r?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters?.takeIf { it > 0 },
                    kcal = activeKcal(c, s.startTime, s.endTime, bmrPerDay)?.takeIf { it > 0 },
                    avgHr = r?.get(HeartRateRecord.BPM_AVG)?.toInt(),
                )
            }
    }

    /** Total steps per day for the last [days] days, oldest first. */
    suspend fun stepsByDay(days: Int): List<Pair<LocalDate, Long>> {
        val c = client ?: return emptyList()
        if (!has(HealthPermission.getReadPermission(StepsRecord::class))) return emptyList()
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong()).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        return runCatching {
            val rows = c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
            (0 until days).map { i -> today.minusDays((days - 1 - i).toLong()).let { d -> d to (rows[d] ?: 0L) } }
        }.getOrDefault(emptyList())
    }

    private fun appName(pkg: String): String = when (pkg) {
        "com.sec.android.app.shealth" -> "Samsung Health"
        "com.google.android.apps.fitness" -> "Google Fit"
        "com.google.android.apps.healthdata" -> "Health Connect"
        "com.fitbit.FitbitMobile" -> "Fitbit"
        "com.strava" -> "Strava"
        else -> runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("Health Connect")
    }

    /**
     * Active calories for a time window. Samsung Health often writes only *total* calories burned
     * (which includes your resting burn), not active calories. In that case the resting burn for the
     * window (from your BMR) is subtracted so the number means "calories from moving".
     */
    private suspend fun activeKcal(c: HealthConnectClient, start: Instant, end: Instant, bmrPerDay: Double): Double? {
        val granted = granted()
        val canActive = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted
        val canTotal = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted
        if (!canActive && !canTotal) return null
        val metrics = buildSet<AggregateMetric<*>> {
            if (canActive) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canTotal) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        val r = runCatching { c.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, end))) }.getOrNull() ?: return null
        val active = if (canActive) r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories else null
        if (active != null && active > 0) return active
        val total = (if (canTotal) r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories else null) ?: return active
        val resting = bmrPerDay * Duration.between(start, end).seconds / 86_400.0
        // If the total is well below the resting burn, the source only logged exercise calories: use it as-is.
        return if (total < resting * 0.5) total else (total - resting).coerceAtLeast(0.0)
    }

    suspend fun today(bmrPerDay: Double): Today? {
        val c = client ?: return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        val canSteps = has(HealthPermission.getReadPermission(StepsRecord::class))
        val steps = if (canSteps) runCatching {
            c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, now)))[StepsRecord.COUNT_TOTAL]
        }.getOrNull() ?: 0L else 0L
        val kcal = activeKcal(c, start, now, bmrPerDay)
        if (!canSteps && kcal == null) return null
        return Today(steps = steps, activeKcal = kcal ?: 0.0)
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

    suspend fun sessionStats(start: Instant, end: Instant, bmrPerDay: Double): SessionStats {
        val c = client ?: return SessionStats(null, null)
        val avgHr = if (has(HealthPermission.getReadPermission(HeartRateRecord::class))) runCatching {
            c.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(start, end)))[HeartRateRecord.BPM_AVG]?.toInt()
        }.getOrNull() else null
        return SessionStats(avgHr, activeKcal(c, start, end, bmrPerDay)?.takeIf { it > 0 })
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
