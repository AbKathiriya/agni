package com.akash.agni.ui.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.agni.AppContainer
import com.akash.agni.data.Block
import com.akash.agni.data.Kind
import com.akash.agni.data.Plan
import com.akash.agni.data.SetLog
import com.akash.agni.data.WorkoutSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

enum class Phase { WORK, REST, DONE }

data class PlayerState(
    val blockIndex: Int = 0,
    val set: Int = 1,
    val phase: Phase = Phase.WORK,
    /** Countdown for timed work, cardio and rest. */
    val secondsLeft: Int = 0,
    val phaseTotal: Int = 0,
    val running: Boolean = true,
    val reps: Int = 10,
    val weightKg: Double = 0.0,
    val hint: String? = null,
    val switchSides: Boolean = false,
    val heartRate: Int? = null,
    val elapsedSec: Int = 0,
    val setsDone: Int = 0,
    val nextBlockIndex: Int = 0,
    val nextSet: Int = 1,
    val summary: Summary? = null,
)

data class Summary(val minutes: Int, val sets: Int, val xp: Int, val avgHr: Int?, val kcal: Int?, val completed: Boolean)

class PlayerViewModel(private val c: AppContainer, private val appContext: Context, workoutId: String) : ViewModel() {

    val workout = Plan.byId[workoutId] ?: Plan.lowerCore
    private val blocks = workout.blocks
    private val startedAt = Instant.now()
    private val sets = mutableListOf<SetLog>()
    private val totalSets = blocks.sumOf { it.sets }

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    val block: Block get() = blocks[_state.value.blockIndex]
    fun blockAt(index: Int): Block? = blocks.getOrNull(index)
    val blockCount get() = blocks.size

    private var mediaJob: Job? = null

    init {
        enterWork(0, 1)
        viewModelScope.launch { tick() }
        viewModelScope.launch {
            while (isActive) {
                val hr = c.health.latestHeartRate(withinMinutes = 5)
                _state.update { it.copy(heartRate = hr) }
                delay(30_000)
            }
        }
    }

    private suspend fun tick() {
        while (true) {
            delay(1000)
            val s = _state.value
            if (s.phase == Phase.DONE) return
            if (!s.running) continue
            _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
            val countdown = s.phase == Phase.REST || block.kind != Kind.REPS
            if (!countdown) continue
            val left = s.secondsLeft - 1
            if (s.phase == Phase.WORK && block.kind == Kind.TIMED && block.perSide && left == block.seconds) {
                buzz(150)
                _state.update { it.copy(secondsLeft = left, switchSides = true) }
                continue
            }
            if (left <= 3 && left > 0) buzz(40)
            if (left <= 0) {
                buzz(400)
                if (s.phase == Phase.REST) startPending() else completeSet()
            } else {
                _state.update { it.copy(secondsLeft = left) }
            }
        }
    }

    private fun enterWork(blockIndex: Int, set: Int) {
        val b = blocks[blockIndex]
        val seconds = when (b.kind) {
            Kind.REPS -> 0
            Kind.TIMED -> if (b.perSide) b.seconds * 2 else b.seconds
            Kind.CARDIO -> b.seconds
        }
        val sameBlock = blockIndex == _state.value.blockIndex && _state.value.phase != Phase.DONE
        _state.update {
            it.copy(
                blockIndex = blockIndex, set = set, phase = Phase.WORK,
                secondsLeft = seconds, phaseTotal = seconds, switchSides = false,
                reps = if (sameBlock && set > 1) it.reps else (if (b.repsMax > 0) b.repsMax else b.repsMin),
            )
        }
        if (set == 1) loadBlockContext(b)
    }

    /** Pre-fills last weight used and suggests progression. */
    private fun loadBlockContext(b: Block) {
        mediaJob?.cancel()
        _state.update { it.copy(hint = null) }
        mediaJob = viewModelScope.launch {
            if (b.kind == Kind.REPS) {
                val recent = c.db.workouts().recentSets(b.exerciseId)
                val lastDay = recent.firstOrNull()?.day
                val lastSession = recent.filter { it.day == lastDay }
                val lastWeight = lastSession.firstOrNull()?.weightKg ?: 0.0
                val target = if (b.repsMax > 0) b.repsMax else b.repsMin
                val hitTop = lastSession.size >= b.sets && lastSession.all { it.reps >= target }
                val hint = when {
                    lastSession.isEmpty() -> "First time: pick a weight you could lift 2–3 more reps with."
                    hitTop && lastWeight > 0 -> "You hit $target on every set last time. Try ${fmt(lastWeight + 2.5)} kg."
                    lastWeight > 0 -> "Last time: ${fmt(lastWeight)} kg × ${lastSession.joinToString("/") { it.reps.toString() }}"
                    else -> null
                }
                _state.update { it.copy(weightKg = if (hitTop) lastWeight + 2.5 else lastWeight, hint = hint) }
            }
        }
    }

    fun setReps(delta: Int) = _state.update { it.copy(reps = (it.reps + delta).coerceIn(0, 100)) }
    fun setWeight(delta: Double) = _state.update { it.copy(weightKg = (it.weightKg + delta).coerceIn(0.0, 400.0)) }
    fun toggleRunning() = _state.update { it.copy(running = !it.running) }

    /** "Done set" for rep sets; also used to end a timed/cardio block early. */
    fun completeSet() {
        val s = _state.value
        if (s.phase != Phase.WORK) return
        val b = block
        sets += SetLog(
            sessionId = 0, exerciseId = b.exerciseId, setIndex = s.set,
            reps = if (b.kind == Kind.REPS) s.reps else 0,
            weightKg = if (b.kind == Kind.REPS) s.weightKg else 0.0,
            seconds = if (b.kind == Kind.REPS) 0 else s.phaseTotal - s.secondsLeft,
            day = LocalDate.now().toEpochDay(),
        )
        _state.update { it.copy(setsDone = it.setsDone + 1) }
        val lastSetOfBlock = s.set >= b.sets
        val lastBlock = s.blockIndex >= blocks.lastIndex
        when {
            lastSetOfBlock && lastBlock -> finish()
            b.restSec <= 0 -> if (lastSetOfBlock) enterWork(s.blockIndex + 1, 1) else enterWork(s.blockIndex, s.set + 1)
            else -> {
                val (nb, ns) = if (lastSetOfBlock) s.blockIndex + 1 to 1 else s.blockIndex to s.set + 1
                _state.update {
                    it.copy(
                        phase = Phase.REST, secondsLeft = b.restSec, phaseTotal = b.restSec, running = true,
                        nextBlockIndex = nb, nextSet = ns,
                    )
                }
            }
        }
    }

    private fun startPending() = enterWork(_state.value.nextBlockIndex, _state.value.nextSet)

    fun skipRest() {
        if (_state.value.phase == Phase.REST) startPending()
    }

    fun addRest(seconds: Int) = _state.update {
        if (it.phase == Phase.REST) it.copy(secondsLeft = it.secondsLeft + seconds, phaseTotal = it.phaseTotal + seconds) else it
    }

    fun skipExercise() {
        val s = _state.value
        if (s.blockIndex >= blocks.lastIndex) finish() else enterWork(s.blockIndex + 1, 1)
    }

    fun previousExercise() {
        val s = _state.value
        enterWork((s.blockIndex - 1).coerceAtLeast(0), 1)
    }

    fun finish() {
        if (_state.value.phase == Phase.DONE) return
        _state.update { it.copy(phase = Phase.DONE, running = false) }
        viewModelScope.launch {
            val end = Instant.now()
            val completed = sets.size * 2 >= totalSets
            val stats = c.health.sessionStats(startedAt, end)
            val day = LocalDate.now().toEpochDay()
            val id = c.db.workouts().insertSession(
                WorkoutSession(
                    day = day, workoutId = workout.id, title = workout.title,
                    startedAt = startedAt.toEpochMilli(), endedAt = end.toEpochMilli(),
                    kcal = stats.activeKcal, avgHr = stats.avgHr, completed = completed,
                )
            )
            c.db.workouts().insertSets(sets.map { it.copy(sessionId = id) })
            if (completed) {
                c.engine.awardWorkout(day, workout)
                c.health.writeWorkout(workout.title, startedAt, end, workout.cardioOnly)
            }
            val minutes = ((end.toEpochMilli() - startedAt.toEpochMilli()) / 60_000).toInt()
            _state.update {
                it.copy(summary = Summary(minutes, sets.size, if (completed) workout.xp else 0, stats.avgHr, stats.activeKcal?.toInt(), completed))
            }
        }
    }

    private fun buzz(ms: Long) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun fmt(kg: Double) = if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg)
}
