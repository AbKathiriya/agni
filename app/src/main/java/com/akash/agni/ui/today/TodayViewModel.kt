package com.akash.agni.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.agni.AppContainer
import com.akash.agni.data.Measurement
import com.akash.agni.domain.Snapshot
import com.akash.agni.domain.Tip
import com.akash.agni.health.HealthConnectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TodayViewModel(private val c: AppContainer) : ViewModel() {

    val snapshot: StateFlow<Snapshot?> = c.engine.snapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _health = MutableStateFlow<HealthConnectManager.Today?>(null)
    val health = _health.asStateFlow()

    private val _connected = MutableStateFlow(true)
    val connected = _connected.asStateFlow()

    private val _tips = MutableStateFlow<List<Tip>>(emptyList())
    val tips = _tips.asStateFlow()

    init {
        viewModelScope.launch {
            val journal = c.db.journal().forDay(LocalDate.now().toEpochDay())
            combine(snapshot.filterNotNull(), _health, journal) { s, h, j -> Triple(s, h, j) }
                .collectLatest { (s, h, j) ->
                    delay(600) // let bursts of edits settle
                    c.engine.syncAwards(s, h?.steps, j?.text?.isNotBlank() == true)
                    _tips.value = runCatching { c.coach.tips(s.settings, s) }.getOrDefault(emptyList())
                }
        }
    }

    fun refresh() = viewModelScope.launch {
        _connected.value = c.health.status() == HealthConnectManager.Status.AVAILABLE && c.health.hasAll()
        _health.value = c.health.today()
        importWeightFromHealth()
        c.importer.sync()
    }

    /** Pulls a newer weigh-in (e.g. from a Samsung Health scale entry) into the progress log. */
    private suspend fun importWeightFromHealth() {
        val (day, kg) = c.health.latestWeight() ?: return
        val existing = c.db.measurements().all().first()
        if (existing.any { it.day == day.toEpochDay() && it.weightKg != null }) return
        if (existing.lastOrNull { it.weightKg != null }?.let { it.day > day.toEpochDay() } == true) return
        c.db.measurements().insert(Measurement(day = day.toEpochDay(), weightKg = kg))
    }
}
