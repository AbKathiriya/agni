package com.akash.agni.domain

import com.akash.agni.data.Plan
import com.akash.agni.data.SettingsStore
import com.akash.agni.data.Workout
import java.time.LocalDate

/** Changes to the weekly plan for specific dates: swap two days, replace a day, or reset it. */
class Schedule(private val store: SettingsStore) {

    private fun base(date: LocalDate) = Plan.valueOf(Plan.forDay(date.dayOfWeek))

    private fun put(map: MutableMap<Long, String>, date: LocalDate, value: String) {
        if (value == base(date)) map.remove(date.toEpochDay()) else map[date.toEpochDay()] = value
    }

    /** Set what a day is. Returns nothing; the plan updates everywhere through settings. */
    suspend fun set(date: LocalDate, workout: Workout?) = store.update { s ->
        val m = s.planOverrides.toMutableMap()
        put(m, date, Plan.valueOf(workout))
        s.copy(planOverrides = m)
    }

    /** Replace [date] with [workout] and push what was planned there onto [moveTo] (replacing that day's plan). */
    suspend fun replaceAndMove(date: LocalDate, workout: Workout?, moveTo: LocalDate) = store.update { s ->
        val m = s.planOverrides.toMutableMap()
        val displaced = Plan.valueOf(Plan.forDate(date, s.planOverrides))
        put(m, date, Plan.valueOf(workout))
        put(m, moveTo, displaced)
        s.copy(planOverrides = m)
    }

    suspend fun swap(a: LocalDate, b: LocalDate) = store.update { s ->
        val m = s.planOverrides.toMutableMap()
        val va = Plan.valueOf(Plan.forDate(a, s.planOverrides))
        val vb = Plan.valueOf(Plan.forDate(b, s.planOverrides))
        put(m, a, vb)
        put(m, b, va)
        s.copy(planOverrides = m)
    }

    suspend fun reset(date: LocalDate) = store.update { s -> s.copy(planOverrides = s.planOverrides - date.toEpochDay()) }
}
