package com.akash.agni.data

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import java.time.LocalDate

/** Fills the database on first launch: food list, two saved meals, starter rewards. */
class Seeder(private val context: Context, private val db: AgniDatabase) {

    suspend fun seedIfEmpty() {
        if (db.foods().count() == 0) seedFoods()
        if (db.savedMeals().count() == 0) seedSavedMeals()
        if (db.rewards().count() == 0) seedRewards()
    }

    private suspend fun seedFoods() {
        val json = context.assets.open("foods.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val foods = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Food(
                name = o.getString("n"),
                aliases = o.optString("a"),
                category = o.getString("c"),
                unit = o.getString("u"),
                kcal = o.getDouble("k"),
                protein = o.getDouble("p"),
                carbs = o.getDouble("cb"),
                fat = o.getDouble("f"),
            )
        }
        db.foods().insertAll(foods)
    }

    private suspend fun seedSavedMeals() {
        suspend fun id(name: String) = db.foods().byName(name)?.id
        db.withTransaction {
            val lunch = listOfNotNull(
                id("Rotli with ghee")?.let { it to 3.0 },
                id("Gujarati dal")?.let { it to 1.0 },
                id("Mixed veg shaak")?.let { it to 1.0 },
                id("Bhaat (steamed rice)")?.let { it to 0.5 },
            )
            val lunchId = db.savedMeals().insert(SavedMeal(name = "My usual lunch", meal = Meal.LUNCH))
            db.savedMeals().insertItems(lunch.map { SavedMealItem(savedMealId = lunchId, foodId = it.first, servings = it.second) })

            val breakfast = listOfNotNull(
                id("Methi thepla")?.let { it to 2.0 },
                id("Dahi")?.let { it to 1.0 },
            )
            val bId = db.savedMeals().insert(SavedMeal(name = "Thepla + dahi", meal = Meal.BREAKFAST))
            db.savedMeals().insertItems(breakfast.map { SavedMealItem(savedMealId = bId, foodId = it.first, servings = it.second) })
        }
    }

    private suspend fun seedRewards() {
        val today = LocalDate.now().toEpochDay()
        db.rewards().insert(Reward(title = "Tap to name your reward", goalType = GoalType.WAIST_DOWN_IN, target = 2.0, createdDay = today))
        db.rewards().insert(Reward(title = "Tap to name your reward", goalType = GoalType.WEIGHT_DOWN_KG, target = 5.0, createdDay = today))
        db.rewards().insert(Reward(title = "Tap to name your reward", goalType = GoalType.STREAK_DAYS, target = 30.0, createdDay = today))
        db.rewards().insert(Reward(title = "Tap to name your reward", goalType = GoalType.WORKOUTS, target = 25.0, createdDay = today))
    }
}
