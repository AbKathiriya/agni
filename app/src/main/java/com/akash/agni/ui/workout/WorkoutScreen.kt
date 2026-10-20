package com.akash.agni.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.akash.agni.data.Settings
import com.akash.agni.ui.components.commas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.agni.AppContainer
import com.akash.agni.container
import com.akash.agni.data.WorkoutSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.navigation.NavHostController
import com.akash.agni.data.Plan
import com.akash.agni.data.Workout
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.startWorkout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    val history = c.db.workouts().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _steps = MutableStateFlow<List<Pair<LocalDate, Long>>>(emptyList())
    val steps = _steps.asStateFlow()

    /** Pulls new watch workouts and the week's steps from Health Connect. */
    fun refresh() = viewModelScope.launch {
        c.importer.sync()
        _steps.value = c.health.stepsByDay(7)
    }

    /**
     * Agni workouts are deleted everywhere (sets, Health Connect record). Imported ones are hidden instead,
     * so they stay in Samsung Health but won't come back here or count toward your streak.
     * Either way the day's workout XP goes if nothing else was done that day.
     */
    fun delete(session: WorkoutSession) = viewModelScope.launch {
        val w = c.db.workouts()
        if (session.imported) {
            w.hide(session.id)
        } else {
            w.deleteSets(session.id)
            w.deleteSession(session.id)
            c.health.deleteWorkout(session.id)
        }
        if (w.completedOn(session.day) == 0) c.db.xp().revoke(session.day, "workout")
    }
}

@Composable
fun WorkoutScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val vm: WorkoutViewModel = viewModel { WorkoutViewModel(c) }
    val history by vm.history.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val today = LocalDate.now().dayOfWeek
    var expanded by remember { mutableStateOf<DayOfWeek?>(today) }
    var deleting by remember { mutableStateOf<WorkoutSession?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("This week", style = MaterialTheme.typography.headlineSmall)
            Muted("3 strength days for shape, cardio days for fat burning. Add weight once you hit the top of the rep range on every set.")
        }
        items(DayOfWeek.values().toList()) { day ->
            val w = Plan.forDay(day)
            DayCard(
                day = day,
                isToday = day == today,
                workout = w,
                expanded = expanded == day,
                onToggle = { expanded = if (expanded == day) null else day },
                onStart = { w?.let { nav.startWorkout(it.id) } },
            )
        }
        if (steps.isNotEmpty()) {
            item { StepsCard(steps, settings.stepGoal) }
        }
        if (history.isNotEmpty()) {
            item {
                Text("History", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                Muted(
                    "Includes workouts from your watch. Started one by mistake? Delete it here.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(history, key = { it.id }) { session ->
                HistoryRow(session, onDelete = { deleting = session })
            }
        }
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this workout?") },
            text = {
                Text(
                    "${session.title} on ${dateTime(session)} will be removed from your history, streak and XP." +
                        if (session.imported) " It stays in ${session.source}." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(session); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepsCard(days: List<Pair<LocalDate, Long>>, goal: Int) {
    val max = maxOf(goal.toLong(), days.maxOf { it.second }).coerceAtLeast(1)
    val avg = days.sumOf { it.second } / days.size
    AgniCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Steps", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Muted("${avg.commas()} a day · goal ${goal.commas()}", style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth().height(96.dp).semantics {
                contentDescription = "Steps, last 7 days: " + days.joinToString { "${it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.second}" }
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { (_, n) ->
                val hit = n >= goal
                Box(
                    Modifier.weight(1f).fillMaxHeight((n.toFloat() / max).coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { (d, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Muted(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun dateTime(s: WorkoutSession): String =
    Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))

@Composable
private fun HistoryRow(session: WorkoutSession, onDelete: () -> Unit) {
    val minutes = ((session.endedAt - session.startedAt) / 60_000).coerceAtLeast(0)
    val status = when {
        session.imported && session.completed -> "Counts for today's plan"
        session.imported -> "Logged"
        session.completed -> "Completed"
        else -> "Partial"
    }
    val stats = listOfNotNull(
        session.kcal?.let { "${it.roundToInt()} kcal" },
        session.avgHr?.let { "$it bpm avg" },
        session.steps?.let { "${it.commas()} steps" },
        session.distanceM?.let { "%.1f km".format(it / 1000) },
    )
    AgniCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall)
                    if (session.imported) {
                        Icon(
                            Icons.Rounded.Watch, contentDescription = "From ${session.source}",
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Muted(dateTime(session) + " · $minutes min · " + status, style = MaterialTheme.typography.bodySmall)
                if (stats.isNotEmpty()) Muted(stats.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${session.title} on ${dateTime(session)}")
            }
        }
    }
}

@Composable
private fun DayCard(
    day: DayOfWeek,
    isToday: Boolean,
    workout: Workout?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onStart: () -> Unit,
) {
    AgniCard(
        onClick = onToggle,
        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Muted(day.getDisplayName(TextStyle.FULL, Locale.getDefault()) + if (isToday) " · Today" else "")
                Text(workout?.title ?: "Rest", style = MaterialTheme.typography.titleMedium)
                if (workout != null) Muted(workout.summary, style = MaterialTheme.typography.bodySmall)
            }
            if (workout != null) {
                FilledTonalIconButton(onClick = onStart) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Start ${workout.title}")
                }
            }
        }
        if (expanded && workout != null) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workout.blocks.forEach { b ->
                    Row {
                        Text(b.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Muted(b.prescription, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
