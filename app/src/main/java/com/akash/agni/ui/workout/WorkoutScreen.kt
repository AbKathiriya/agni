package com.akash.agni.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
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

    /** Removes a session everywhere: its sets, the day's workout XP (if no other workout that day), Health Connect. */
    fun delete(session: WorkoutSession) = viewModelScope.launch {
        val w = c.db.workouts()
        w.deleteSets(session.id)
        w.deleteSession(session.id)
        if (w.completedOn(session.day) == 0) c.db.xp().revoke(session.day, "workout")
        c.health.deleteWorkout(session.id)
    }
}

@Composable
fun WorkoutScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val vm: WorkoutViewModel = viewModel { WorkoutViewModel(c) }
    val history by vm.history.collectAsStateWithLifecycle()
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
        if (history.isNotEmpty()) {
            item {
                Text("History", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                Muted("Started one by mistake? Delete it here.", style = MaterialTheme.typography.bodySmall)
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
            text = { Text("${session.title} on ${dateTime(session)} will be removed from your history, streak and XP.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(session); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun dateTime(s: WorkoutSession): String =
    Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))

@Composable
private fun HistoryRow(session: WorkoutSession, onDelete: () -> Unit) {
    val minutes = ((session.endedAt - session.startedAt) / 60_000).coerceAtLeast(0)
    AgniCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall)
                Muted(
                    dateTime(session) + " · $minutes min · " + if (session.completed) "Completed" else "Partial",
                    style = MaterialTheme.typography.bodySmall,
                )
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
