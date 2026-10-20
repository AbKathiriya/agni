package com.akash.agni.domain

import com.akash.agni.data.AgniDatabase
import com.akash.agni.data.FoodLog
import com.akash.agni.data.GoalType
import com.akash.agni.data.Measurement
import com.akash.agni.data.Plan
import com.akash.agni.data.Reward
import com.akash.agni.data.Settings
import com.akash.agni.data.SettingsStore
import com.akash.agni.data.Workout
import com.akash.agni.data.WorkoutSession
import com.akash.agni.data.XpEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import kotlin.math.roundToInt

object Targets {
    fun bmr(s: Settings, weightKg: Double) =
        10 * weightKg + 6.25 * s.heightCm - 5 * s.age + if (s.male) 5 else -161

    /** Daily calorie target: maintenance minus the chosen deficit, never below a safe floor. */
    fun dailyKcal(s: Settings, weightKg: Double): Int {
        if (s.targetOverride > 0) return s.targetOverride
        val maintenance = bmr(s, weightKg) * s.activity
        val floor = if (s.male) 1500 else 1200
        return ((maintenance - s.deficit).coerceAtLeast(floor.toDouble()) / 10).roundToInt() * 10
    }

    fun maintenance(s: Settings, weightKg: Double) = (bmr(s, weightKg) * s.activity).roundToInt()
}

data class LevelInfo(val level: Int, val name: String, val totalXp: Int, val levelStartXp: Int, val nextLevelXp: Int) {
    val progress get() = ((totalXp - levelStartXp).toFloat() / (nextLevelXp - levelStartXp)).coerceIn(0f, 1f)
    val toNext get() = nextLevelXp - totalXp
}

object Levels {
    private val names = listOf("Spark", "Kindling", "Steady Burner", "Furnace", "Forge", "Iron Will", "Summit", "Ascended")
    /** Level n starts at 50·n·(n−1) XP: 0, 100, 300, 600, 1000, 1500… */
    fun start(n: Int) = 50 * n * (n - 1)
    fun of(xp: Int): LevelInfo {
        var n = 1
        while (start(n + 1) <= xp) n++
        return LevelInfo(n, names[(n - 1).coerceAtMost(names.lastIndex)], xp, start(n), start(n + 1))
    }
}

data class DayStatus(val day: LocalDate, val onTrack: Boolean, val usedFreeze: Boolean)

data class RewardProgress(val reward: Reward, val current: Double) {
    val fraction get() = (current / reward.target).coerceIn(0.0, 1.0).toFloat()
    val reached get() = current >= reward.target
}

data class Snapshot(
    val settings: Settings,
    val today: LocalDate,
    val currentWeight: Double,
    val target: Int,
    val maintenance: Int,
    val todayLogs: List<FoodLog>,
    val eaten: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** Calories saved (or overspent, if negative) on earlier days of this week. */
    val bank: Int,
    val todaysWorkout: Workout?,
    val workoutDoneToday: Boolean,
    val sessionsThisWeek: Int,
    val streak: Int,
    val freezes: Int,
    val history: List<DayStatus>,
    val level: LevelInfo,
    val measurements: List<Measurement>,
    val rewards: List<RewardProgress>,
    val workoutsDone: Int,
    /** Calories from today's workouts (Agni and watch), as a floor for "Burned". */
    val todayWorkoutKcal: Double = 0.0,
) {
    val left get() = target - eaten.roundToInt()
    val treatProgress get() = (bank.toFloat() / settings.treatKcal).coerceIn(0f, 1f)
}

/**
 * Turns raw logs into the numbers the app shows: calorie target, weekly bank, streak with freezes,
 * XP level and reward progress. Everything is derived, so editing a past log fixes history.
 */
class Engine(private val db: AgniDatabase, private val settingsStore: SettingsStore) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshot(): Flow<Snapshot> = settingsStore.settings.flatMapLatest { s ->
        val today = LocalDate.now()
        val from = minOf(s.startDay, today.toEpochDay() - 6)
        val to = today.toEpochDay()
        combine(
            db.foodLogs().forRange(from, to),
            db.workouts().sessions(from, to),
            db.measurements().all(),
            db.xp().total(),
            db.rewards().all(),
        ) { logs, sessions, measurements, xp, rewards ->
            build(s, today, logs, sessions, measurements, xp, rewards)
        }
    }

    private fun build(
        s: Settings,
        today: LocalDate,
        logs: List<FoodLog>,
        sessions: List<WorkoutSession>,
        measurements: List<Measurement>,
        xp: Int,
        rewards: List<Reward>,
    ): Snapshot {
        val weight = measurements.lastOrNull { it.weightKg != null }?.weightKg ?: s.startWeightKg
        val target = Targets.dailyKcal(s, weight)
        val byDay = logs.groupBy { it.day }
        val sessionDays = sessions.filter { it.completed }.map { it.day }.toSet()
        val todayEpoch = today.toEpochDay()
        val todayLogs = byDay[todayEpoch].orEmpty()

        // Weekly bank: Monday..yesterday, only days with food logged count.
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        var bank = 0
        for (d in maxOf(weekStart, s.startDay) until todayEpoch) {
            val dayLogs = byDay[d] ?: continue
            bank += target - dayLogs.sumOf { it.kcal }.roundToInt()
        }

        // Streak with freezes, walking forward from the start day.
        var streak = 0
        var freezes = 1
        val history = mutableListOf<DayStatus>()
        var runningBank = 0
        for (d in s.startDay..todayEpoch) {
            val date = LocalDate.ofEpochDay(d)
            if (date.dayOfWeek.value == 1) runningBank = 0
            val dayLogs = byDay[d].orEmpty()
            val eatenDay = dayLogs.sumOf { it.kcal }
            val logged = dayLogs.size >= 2
            val withinCalories = eatenDay <= target * 1.1 || eatenDay <= target + runningBank
            val trained = Plan.forDay(date.dayOfWeek) == null || d in sessionDays
            val onTrack = logged && withinCalories && trained
            if (logged) runningBank += target - eatenDay.roundToInt()

            if (d == todayEpoch && !onTrack) {
                // Today isn't over yet: it neither extends nor breaks the streak.
                history += DayStatus(date, onTrack = false, usedFreeze = false)
                continue
            }
            if (onTrack) {
                streak++
                if (streak % 7 == 0) freezes = (freezes + 1).coerceAtMost(3)
                history += DayStatus(date, onTrack = true, usedFreeze = false)
            } else if (freezes > 0) {
                freezes--
                history += DayStatus(date, onTrack = false, usedFreeze = true)
            } else {
                streak = 0
                history += DayStatus(date, onTrack = false, usedFreeze = false)
            }
        }

        val waists = measurements.mapNotNull { it.waistIn }
        val weights = measurements.mapNotNull { it.weightKg }
        val workoutsDone = sessions.count { it.completed }
        val rewardProgress = rewards.map { r ->
            val current = when (r.goalType) {
                GoalType.WAIST_DOWN_IN -> if (waists.size >= 2) waists.first() - waists.last() else 0.0
                GoalType.WEIGHT_DOWN_KG -> s.startWeightKg - (weights.lastOrNull() ?: s.startWeightKg)
                GoalType.STREAK_DAYS -> streak.toDouble()
                GoalType.WORKOUTS -> workoutsDone.toDouble()
            }
            RewardProgress(r, current.coerceAtLeast(0.0))
        }

        return Snapshot(
            settings = s,
            today = today,
            currentWeight = weight,
            target = target,
            maintenance = Targets.maintenance(s, weight),
            todayLogs = todayLogs,
            eaten = todayLogs.sumOf { it.kcal },
            protein = todayLogs.sumOf { it.protein },
            carbs = todayLogs.sumOf { it.carbs },
            fat = todayLogs.sumOf { it.fat },
            bank = bank,
            todaysWorkout = Plan.forDay(today.dayOfWeek),
            workoutDoneToday = todayEpoch in sessionDays,
            sessionsThisWeek = sessions.count { it.completed && it.day >= weekStart },
            streak = streak,
            freezes = freezes,
            history = history.takeLast(28),
            level = Levels.of(xp),
            measurements = measurements,
            rewards = rewardProgress,
            workoutsDone = workoutsDone,
            todayWorkoutKcal = sessions.filter { it.day == todayEpoch }.sumOf { it.kcal ?: 0.0 },
        )
    }

    /**
     * Grants XP for what the snapshot shows. Safe to call repeatedly: each award is unique per day
     * (or per week / per streak milestone), so the database ignores duplicates.
     */
    suspend fun syncAwards(snap: Snapshot, steps: Long?, hasJournal: Boolean) {
        val day = snap.today.toEpochDay()
        val xp = db.xp()
        suspend fun give(type: String, amount: Int, onDay: Long = day) = xp.award(XpEvent(day = onDay, type = type, xp = amount))

        if (snap.todayLogs.size >= 3) give("log_food", 10)
        if (snap.protein >= snap.settings.proteinTarget) give("protein", 10)
        if (steps != null && steps >= snap.settings.stepGoal) give("steps", 10)
        if (hasJournal) give("journal", 5)

        // Yesterday's calorie result is final now.
        snap.history.dropLast(1).lastOrNull()?.let { if (it.onTrack) give("on_track", 20, it.day.toEpochDay()) }

        if (snap.streak > 0 && snap.streak % 7 == 0) give("streak_${snap.streak}", 50)

        snap.rewards.filter { it.reached && it.reward.unlockedDay == null }.forEach {
            db.rewards().update(it.reward.copy(unlockedDay = day))
            give("reward_${it.reward.id}", 50, 0)
        }
    }

    suspend fun awardWorkout(day: Long, workout: Workout) =
        db.xp().award(XpEvent(day = day, type = "workout", xp = workout.xp))

    /** Resting burn per day for the latest weight; used to turn "total" calories into "active" ones. */
    suspend fun currentBmr(): Double {
        val s = settingsStore.settings.first()
        val w = db.measurements().all().first().lastOrNull { it.weightKg != null }?.weightKg ?: s.startWeightKg
        return Targets.bmr(s, w)
    }

    suspend fun awardMeasurement(day: LocalDate) {
        val weekStart = day.minusDays((day.dayOfWeek.value - 1).toLong()).toEpochDay()
        db.xp().award(XpEvent(day = weekStart, type = "measure", xp = 20))
    }
}
