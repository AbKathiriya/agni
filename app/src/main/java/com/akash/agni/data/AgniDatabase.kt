package com.akash.agni.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Food::class, FoodLog::class, SavedMeal::class, SavedMealItem::class,
        WorkoutSession::class, SetLog::class, Measurement::class, JournalEntry::class,
        XpEvent::class, Reward::class, ExerciseMedia::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AgniDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun foodLogs(): FoodLogDao
    abstract fun savedMeals(): SavedMealDao
    abstract fun workouts(): WorkoutDao
    abstract fun measurements(): MeasurementDao
    abstract fun journal(): JournalDao
    abstract fun xp(): XpDao
    abstract fun rewards(): RewardDao
    abstract fun exerciseMedia(): ExerciseMediaDao

    companion object {
        fun build(context: Context): AgniDatabase =
            Room.databaseBuilder(context, AgniDatabase::class.java, "agni.db").build()
    }
}
