package com.akash.agni.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val heightCm: Double = 168.9,  // 5'6.5"
    val age: Int = 33,
    val male: Boolean = true,
    val activity: Double = 1.2,    // desk job
    val deficit: Int = 500,
    val targetOverride: Int = 0,   // 0 = calculated
    val proteinTarget: Int = 100,
    val stepGoal: Int = 8000,
    val treatName: String = "Saturday thali",
    val treatKcal: Int = 400,
    val startDay: Long = LocalDate.now().toEpochDay(),
    val startWeightKg: Double = 80.0,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val height = doublePreferencesKey("height_cm")
        val age = intPreferencesKey("age")
        val male = intPreferencesKey("male")
        val activity = doublePreferencesKey("activity")
        val deficit = intPreferencesKey("deficit")
        val targetOverride = intPreferencesKey("target_override")
        val protein = intPreferencesKey("protein")
        val steps = intPreferencesKey("steps")
        val treatName = stringPreferencesKey("treat_name")
        val treatKcal = intPreferencesKey("treat_kcal")
        val startDay = longPreferencesKey("start_day")
        val startWeight = doublePreferencesKey("start_weight")
    }

    val settings: Flow<Settings> = context.dataStore.data.map(::read)

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            heightCm = p[K.height] ?: d.heightCm,
            age = p[K.age] ?: d.age,
            male = (p[K.male] ?: 1) == 1,
            activity = p[K.activity] ?: d.activity,
            deficit = p[K.deficit] ?: d.deficit,
            targetOverride = p[K.targetOverride] ?: d.targetOverride,
            proteinTarget = p[K.protein] ?: d.proteinTarget,
            stepGoal = p[K.steps] ?: d.stepGoal,
            treatName = p[K.treatName] ?: d.treatName,
            treatKcal = p[K.treatKcal] ?: d.treatKcal,
            startDay = p[K.startDay] ?: d.startDay,
            startWeightKg = p[K.startWeight] ?: d.startWeightKg,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
        p[K.theme] = s.themeMode.name
        p[K.height] = s.heightCm
        p[K.age] = s.age
        p[K.male] = if (s.male) 1 else 0
        p[K.activity] = s.activity
        p[K.deficit] = s.deficit
        p[K.targetOverride] = s.targetOverride
        p[K.protein] = s.proteinTarget
        p[K.steps] = s.stepGoal
        p[K.treatName] = s.treatName
        p[K.treatKcal] = s.treatKcal
        p[K.startDay] = s.startDay
        p[K.startWeight] = s.startWeightKg
    }

    /** Pins the start day on first launch so streaks and the calorie bank have a fixed origin. */
    suspend fun ensureStartDay() {
        context.dataStore.edit { p -> if (p[K.startDay] == null) p[K.startDay] = LocalDate.now().toEpochDay() }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p -> write(p, transform(read(p))) }
    }
}
