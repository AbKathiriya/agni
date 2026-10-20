package com.akash.agni.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Food::class, FoodLog::class, SavedMeal::class, SavedMealItem::class,
        WorkoutSession::class, SetLog::class, Measurement::class, JournalEntry::class,
        XpEvent::class, Reward::class, ExerciseMedia::class,
    ],
    version = 2,
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
        /** v2: imported workouts from Samsung Health / Health Connect. Keeps all existing data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'agni'")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN externalId TEXT")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN exerciseType INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN steps INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN distanceM REAL")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_externalId ON workout_sessions (externalId)")
            }
        }

        fun build(context: Context): AgniDatabase =
            Room.databaseBuilder(context, AgniDatabase::class.java, "agni.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
