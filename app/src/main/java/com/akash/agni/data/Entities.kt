package com.akash.agni.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A food with nutrition for ONE serving of [unit] (e.g. "piece", "katori"). */
@Entity(tableName = "foods", indices = [Index("name")])
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val aliases: String = "",
    val category: String,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val custom: Boolean = false,
    val useCount: Int = 0,
)

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), SNACKS("Snacks"), DINNER("Dinner")
}

/** One logged item. Nutrition is copied per serving so later edits to the food don't rewrite history. */
@Entity(tableName = "food_logs", indices = [Index("day")])
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val meal: Meal,
    val foodId: Long,
    val name: String,
    val unit: String,
    val servings: Double,
    val kcalPer: Double,
    val proteinPer: Double,
    val carbsPer: Double,
    val fatPer: Double,
    val loggedAt: Long = System.currentTimeMillis(),
) {
    val kcal get() = kcalPer * servings
    val protein get() = proteinPer * servings
    val carbs get() = carbsPer * servings
    val fat get() = fatPer * servings
}

@Entity(tableName = "saved_meals")
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val meal: Meal,
)

@Entity(tableName = "saved_meal_items", indices = [Index("savedMealId")])
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedMealId: Long,
    val foodId: Long,
    val servings: Double,
)

@Entity(tableName = "workout_sessions", indices = [Index("day"), Index("externalId")])
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val workoutId: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val kcal: Double? = null,
    val avgHr: Int? = null,
    val completed: Boolean = true,
    /** "agni" for workouts done in this app, otherwise the app it was imported from (e.g. "Samsung Health"). */
    val source: String = "agni",
    /** Health Connect record id for imported sessions; used to never import the same one twice. */
    val externalId: String? = null,
    val exerciseType: Int? = null,
    val steps: Long? = null,
    val distanceM: Double? = null,
    /** Imported sessions you deleted (or merged into an Agni workout) stay as hidden markers. */
    val hidden: Boolean = false,
) {
    val imported get() = externalId != null
}

@Entity(tableName = "set_logs", indices = [Index("sessionId"), Index("exerciseId")])
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val day: Long,
)

@Entity(tableName = "measurements", indices = [Index("day")])
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val weightKg: Double? = null,
    val waistIn: Double? = null,
    val hipsIn: Double? = null,
    val thighIn: Double? = null,
)

@Entity(tableName = "journal")
data class JournalEntry(
    @PrimaryKey val day: Long,
    val text: String,
    val energy: Int = 0, // 1..5, 0 = not set
    val sleepHours: Double? = null,
)

/** XP awards. (day, type) is unique so each daily award can only be earned once. */
@Entity(tableName = "xp_events", indices = [Index(value = ["day", "type"], unique = true)])
data class XpEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val type: String,
    val xp: Int,
)

enum class GoalType(val label: String, val unit: String) {
    WAIST_DOWN_IN("Waist down", "″"),
    WEIGHT_DOWN_KG("Weight down", " kg"),
    STREAK_DAYS("Streak", " days"),
    WORKOUTS("Workouts done", ""),
}

@Entity(tableName = "rewards")
data class Reward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val goalType: GoalType,
    val target: Double,
    val createdDay: Long,
    val unlockedDay: Long? = null,
)

@Entity(tableName = "exercise_media")
data class ExerciseMedia(
    @PrimaryKey val exerciseId: String,
    val gifUrl: String?,
    val fetchedAt: Long,
)
