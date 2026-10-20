package com.akash.agni.domain

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import com.akash.agni.data.AgniDatabase
import com.akash.agni.data.Plan
import com.akash.agni.data.SettingsStore
import com.akash.agni.data.WorkoutSession
import com.akash.agni.data.XpEvent
import com.akash.agni.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Brings workouts recorded on your Galaxy Watch / Samsung Health (via Health Connect) into Agni's history.
 *
 * - Each Health Connect session is imported once (tracked by its record id), even if you delete it here.
 * - If it overlaps a workout you ran in Agni, it isn't duplicated: its heart rate, calories, steps and
 *   distance are copied onto the Agni workout instead.
 * - It counts toward your streak and XP when it fits the day: 20+ minutes, and on strength days it has to
 *   be a strength-type session (a walk on leg day doesn't count as leg day).
 */
class ActivityImporter(
    private val db: AgniDatabase,
    private val health: HealthConnectManager,
    private val settings: SettingsStore,
) {
    private val lock = Mutex()

    /** Returns how many new sessions were added. Safe to call often. */
    suspend fun sync(): Int = lock.withLock {
        val s = settings.settings.first()
        val zone = ZoneId.systemDefault()
        val fromDay = maxOf(s.startDay, LocalDate.now().minusDays(14).toEpochDay())
        val since = LocalDate.ofEpochDay(fromDay).atStartOfDay(zone).toInstant()
        val dao = db.workouts()
        var added = 0

        for (w in health.externalWorkouts(since)) {
            if (dao.byExternalId(w.id) != null) continue
            val start = w.start.toEpochMilli()
            val end = w.end.toEpochMilli()
            val day = w.start.atZone(zone).toLocalDate()

            val agni = dao.agniOverlapping(start, end)
            if (agni != null) {
                dao.update(
                    agni.copy(
                        kcal = w.kcal ?: agni.kcal,
                        avgHr = w.avgHr ?: agni.avgHr,
                        steps = w.steps ?: agni.steps,
                        distanceM = w.distanceM ?: agni.distanceM,
                    )
                )
                // Marker so this watch session is never imported again.
                dao.insertSession(external(w, day.toEpochDay(), completed = false, hidden = true))
                continue
            }

            val minutes = (end - start) / 60_000
            val planned = Plan.forDay(day.dayOfWeek)
            val counts = minutes >= 20 && (planned == null || planned.cardioOnly || w.type in strengthTypes)
            dao.insertSession(external(w, day.toEpochDay(), completed = counts, hidden = false))
            if (counts) db.xp().award(XpEvent(day = day.toEpochDay(), type = "workout", xp = planned?.xp ?: 30))
            added++
        }
        added
    }

    private fun external(w: HealthConnectManager.ExternalWorkout, day: Long, completed: Boolean, hidden: Boolean) =
        WorkoutSession(
            day = day,
            workoutId = "external",
            title = w.title?.takeIf { it.isNotBlank() } ?: typeName(w.type),
            startedAt = w.start.toEpochMilli(),
            endedAt = w.end.toEpochMilli(),
            kcal = w.kcal,
            avgHr = w.avgHr,
            completed = completed,
            source = w.app,
            externalId = w.id,
            exerciseType = w.type,
            steps = w.steps,
            distanceM = w.distanceM,
            hidden = hidden,
        )

    companion object {
        val strengthTypes = setOf(
            E.EXERCISE_TYPE_STRENGTH_TRAINING,
            E.EXERCISE_TYPE_WEIGHTLIFTING,
            E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            E.EXERCISE_TYPE_CALISTHENICS,
            E.EXERCISE_TYPE_BOOT_CAMP,
            E.EXERCISE_TYPE_OTHER_WORKOUT,
        )

        fun typeName(type: Int): String = when (type) {
            E.EXERCISE_TYPE_WALKING -> "Walk"
            E.EXERCISE_TYPE_RUNNING -> "Run"
            E.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
            E.EXERCISE_TYPE_BIKING -> "Cycling"
            E.EXERCISE_TYPE_BIKING_STATIONARY -> "Stationary bike"
            E.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
            E.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
            E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            E.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
            E.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
            E.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing machine"
            E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair climber"
            E.EXERCISE_TYPE_HIKING -> "Hike"
            E.EXERCISE_TYPE_SWIMMING_POOL -> "Swim"
            E.EXERCISE_TYPE_YOGA -> "Yoga"
            E.EXERCISE_TYPE_BADMINTON -> "Badminton"
            E.EXERCISE_TYPE_CRICKET -> "Cricket"
            E.EXERCISE_TYPE_TENNIS -> "Tennis"
            else -> "Workout"
        }
    }
}
