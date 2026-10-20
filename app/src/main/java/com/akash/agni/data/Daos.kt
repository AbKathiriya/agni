package com.akash.agni.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(foods: List<Food>): List<Long>

    @Insert
    suspend fun insert(food: Food): Long

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): Food?

    @Query("SELECT * FROM foods WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Food?

    @Query(
        """SELECT * FROM foods
           WHERE name LIKE '%' || :q || '%' OR aliases LIKE '%' || :q || '%'
           ORDER BY (name LIKE :q || '%') DESC, useCount DESC, name
           LIMIT 40"""
    )
    fun search(q: String): Flow<List<Food>>

    @Query("SELECT * FROM foods ORDER BY useCount DESC, name LIMIT 8")
    fun frequent(): Flow<List<Food>>

    @Query("UPDATE foods SET useCount = useCount + 1 WHERE id = :id")
    suspend fun bumpUse(id: Long)
}

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs WHERE day = :day ORDER BY loggedAt")
    fun forDay(day: Long): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE day BETWEEN :from AND :to")
    fun forRange(from: Long, to: Long): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE day BETWEEN :from AND :to")
    suspend fun forRangeOnce(from: Long, to: Long): List<FoodLog>

    @Insert
    suspend fun insert(log: FoodLog): Long

    @Update
    suspend fun update(log: FoodLog)

    @Delete
    suspend fun delete(log: FoodLog)
}

@Dao
interface SavedMealDao {
    @Query("SELECT * FROM saved_meals ORDER BY name")
    fun all(): Flow<List<SavedMeal>>

    @Query("SELECT * FROM saved_meal_items WHERE savedMealId = :id")
    suspend fun items(id: Long): List<SavedMealItem>

    @Insert
    suspend fun insert(meal: SavedMeal): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItem>)

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_meal_items WHERE savedMealId = :id")
    suspend fun deleteItems(id: Long)

    @Query("SELECT COUNT(*) FROM saved_meals")
    suspend fun count(): Int
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert
    suspend fun insertSets(sets: List<SetLog>)

    @Query("SELECT * FROM workout_sessions WHERE day BETWEEN :from AND :to ORDER BY startedAt")
    fun sessions(from: Long, to: Long): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE day BETWEEN :from AND :to")
    suspend fun sessionsOnce(from: Long, to: Long): List<WorkoutSession>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE completed = 1")
    fun completedCount(): Flow<Int>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC LIMIT 20")
    fun recent(): Flow<List<WorkoutSession>>

    @Query("SELECT COUNT(*) FROM set_logs WHERE sessionId = :sessionId")
    suspend fun setCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE day = :day AND completed = 1")
    suspend fun completedOn(day: Long): Int

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId")
    suspend fun deleteSets(sessionId: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    /** Most recent sets for an exercise, used to pre-fill weight and reps. */
    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY id DESC LIMIT 10")
    suspend fun recentSets(exerciseId: String): List<SetLog>
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY day, id")
    fun all(): Flow<List<Measurement>>

    @Insert
    suspend fun insert(m: Measurement): Long

    @Delete
    suspend fun delete(m: Measurement)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal WHERE day = :day")
    fun forDay(day: Long): Flow<JournalEntry?>

    @Query("SELECT * FROM journal ORDER BY day DESC LIMIT 30")
    fun recent(): Flow<List<JournalEntry>>

    @Upsert
    suspend fun upsert(entry: JournalEntry)
}

@Dao
interface XpDao {
    @Query("SELECT COALESCE(SUM(xp), 0) FROM xp_events")
    fun total(): Flow<Int>

    @Query("SELECT * FROM xp_events WHERE day = :day")
    fun forDay(day: Long): Flow<List<XpEvent>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun award(event: XpEvent): Long

    @Query("DELETE FROM xp_events WHERE day = :day AND type = :type")
    suspend fun revoke(day: Long, type: String)
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards ORDER BY (unlockedDay IS NULL), target")
    fun all(): Flow<List<Reward>>

    @Query("SELECT * FROM rewards")
    suspend fun allOnce(): List<Reward>

    @Insert
    suspend fun insert(reward: Reward): Long

    @Update
    suspend fun update(reward: Reward)

    @Delete
    suspend fun delete(reward: Reward)

    @Query("SELECT COUNT(*) FROM rewards")
    suspend fun count(): Int
}

@Dao
interface ExerciseMediaDao {
    @Query("SELECT * FROM exercise_media WHERE exerciseId = :id")
    suspend fun get(id: String): ExerciseMedia?

    @Upsert
    suspend fun put(media: ExerciseMedia)
}
