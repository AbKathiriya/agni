package com.akash.agni.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.agni.AppContainer
import com.akash.agni.data.Food
import com.akash.agni.data.FoodLog
import com.akash.agni.data.Meal
import com.akash.agni.data.SavedMeal
import com.akash.agni.data.SavedMealItem
import com.akash.agni.domain.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModel(private val c: AppContainer) : ViewModel() {

    val day = MutableStateFlow(LocalDate.now())
    val query = MutableStateFlow("")

    val logs: StateFlow<List<FoodLog>> = day.flatMapLatest { c.db.foodLogs().forDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<List<Food>> = query.flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else c.db.foods().search(q.trim())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val frequent = c.db.foods().frequent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedMeals = c.db.savedMeals().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val snapshot: StateFlow<Snapshot?> = c.engine.snapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftDay(days: Long) {
        val next = day.value.plusDays(days)
        if (!next.isAfter(LocalDate.now())) day.value = next
    }

    fun defaultMeal(): Meal {
        val h = LocalTime.now().hour
        return when {
            h < 11 -> Meal.BREAKFAST
            h < 16 -> Meal.LUNCH
            h < 19 -> Meal.SNACKS
            else -> Meal.DINNER
        }
    }

    fun add(food: Food, meal: Meal, servings: Double) = viewModelScope.launch {
        val log = FoodLog(
            day = day.value.toEpochDay(), meal = meal, foodId = food.id, name = food.name, unit = food.unit,
            servings = servings, kcalPer = food.kcal, proteinPer = food.protein, carbsPer = food.carbs, fatPer = food.fat,
        )
        val id = c.db.foodLogs().insert(log)
        c.db.foods().bumpUse(food.id)
        c.health.writeNutrition(log.copy(id = id))
    }

    fun update(log: FoodLog, servings: Double, meal: Meal) = viewModelScope.launch {
        val updated = log.copy(servings = servings, meal = meal)
        c.db.foodLogs().update(updated)
        c.health.writeNutrition(updated) // same client id, newer version: replaces the old record
    }

    fun delete(log: FoodLog) = viewModelScope.launch {
        c.db.foodLogs().delete(log)
        c.health.deleteNutrition(log.id)
    }

    fun logSavedMeal(meal: SavedMeal) = viewModelScope.launch {
        c.db.savedMeals().items(meal.id).forEach { item ->
            val food = c.db.foods().byId(item.foodId) ?: return@forEach
            val log = FoodLog(
                day = day.value.toEpochDay(), meal = meal.meal, foodId = food.id, name = food.name, unit = food.unit,
                servings = item.servings, kcalPer = food.kcal, proteinPer = food.protein, carbsPer = food.carbs, fatPer = food.fat,
            )
            val id = c.db.foodLogs().insert(log)
            c.health.writeNutrition(log.copy(id = id))
        }
    }

    fun saveMeal(name: String, meal: Meal, items: List<FoodLog>) = viewModelScope.launch {
        val id = c.db.savedMeals().insert(SavedMeal(name = name, meal = meal))
        c.db.savedMeals().insertItems(items.map { SavedMealItem(savedMealId = id, foodId = it.foodId, servings = it.servings) })
    }

    fun deleteSavedMeal(meal: SavedMeal) = viewModelScope.launch {
        c.db.savedMeals().deleteItems(meal.id)
        c.db.savedMeals().delete(meal.id)
    }

    fun createFood(name: String, unit: String, kcal: Double, protein: Double, carbs: Double, fat: Double, then: (Food) -> Unit) =
        viewModelScope.launch {
            val food = Food(name = name, unit = unit, kcal = kcal, protein = protein, carbs = carbs, fat = fat, category = "My foods", custom = true)
            val id = c.db.foods().insert(food)
            then(food.copy(id = id))
        }
}
