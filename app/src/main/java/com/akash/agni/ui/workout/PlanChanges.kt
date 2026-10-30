package com.akash.agni.ui.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akash.agni.container
import com.akash.agni.data.Plan
import com.akash.agni.data.Workout
import com.akash.agni.ui.components.ChipRow
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.NumberField
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val activityIdeas = listOf("Badminton", "Cricket", "Swimming", "Hike", "Yoga", "Tennis")

fun LocalDate.label(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

private sealed interface Choice {
    data class Plan(val workout: Workout) : Choice
    data object Activity : Choice
    data object Rest : Choice
}

/**
 * Change what a day is: play a sport instead (optionally pushing the planned workout to the next day),
 * do a different plan workout, rest, or swap with another day. Today and future days only.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChangeDaySheet(date: LocalDate, overrides: Map<Long, String>, onDismiss: () -> Unit) {
    val schedule = LocalContext.current.container.schedule
    val scope = rememberCoroutineScope()
    val current = Plan.forDate(date, overrides)
    val next = date.plusDays(1)
    val nextPlan = Plan.forDate(next, overrides)
    val changed = overrides.containsKey(date.toEpochDay())

    var choice by remember { mutableStateOf<Choice?>(null) }
    var activityName by remember { mutableStateOf("Badminton") }
    // Only worth moving a strength day; a walk can simply be replaced.
    var moveCurrent by remember { mutableStateOf(current != null && !current.cardioOnly) }

    fun done(block: suspend () -> Unit) {
        scope.launch { block(); onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Change ${date.label().lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge)
            Muted("Planned: " + (current?.title ?: "Rest"))

            Text("Do something else", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = choice == Choice.Activity, onClick = { choice = Choice.Activity }, label = { Text("Sport / activity") })
                Plan.all.filter { it.id != Plan.activity.id && it.id != current?.id }.forEach { w ->
                    FilterChip(selected = choice == Choice.Plan(w), onClick = { choice = Choice.Plan(w) }, label = { Text(w.title) })
                }
                if (current != null) FilterChip(selected = choice == Choice.Rest, onClick = { choice = Choice.Rest }, label = { Text("Rest") })
            }

            if (choice == Choice.Activity) {
                OutlinedTextField(
                    activityName, { activityName = it.replace("\n", " ") },
                    label = { Text("What are you doing?") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activityIdeas.forEach { idea ->
                        FilterChip(selected = activityName == idea, onClick = { activityName = idea }, label = { Text(idea) })
                    }
                }
                Muted(
                    "Counts as the day's workout when it lasts 20+ minutes. Track it on your watch, or log it " +
                        "from the Workout tab afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (choice != null && current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = moveCurrent, onCheckedChange = { moveCurrent = it })
                    Text(
                        "Move ${current.title} to ${next.label().lowercase()}" +
                            (nextPlan?.let { " (replaces ${it.title})" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (choice != null) {
                val newWorkout: Workout? = when (val ch = choice) {
                    is Choice.Plan -> ch.workout
                    Choice.Activity -> Plan.activity.copy(title = activityName.trim().ifBlank { "Your activity" })
                    else -> null
                }
                Button(
                    onClick = {
                        done {
                            if (moveCurrent && current != null) schedule.replaceAndMove(date, newWorkout, next)
                            else schedule.set(date, newWorkout)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save") }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Or swap with another day", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1L..6L).map { date.plusDays(it) }.forEach { other ->
                    val w = Plan.forDate(other, overrides)
                    FilterChip(
                        selected = false,
                        onClick = { done { schedule.swap(date, other) } },
                        label = { Text("${other.label()} · ${w?.title ?: "Rest"}") },
                    )
                }
            }

            if (changed) {
                OutlinedButton(onClick = { done { schedule.reset(date) } }) { Text("Reset to the original plan") }
            }
        }
    }
}

private data class Intensity(val label: String, val met: Double, val examples: String)

private val intensities = listOf(
    Intensity("Light", 3.5, "easy yoga, casual walk"),
    Intensity("Moderate", 5.5, "social badminton, doubles tennis, hike"),
    Intensity("Hard", 7.5, "competitive badminton, singles, swimming laps"),
)

/** Log a sport or activity you didn't track on the watch. Calories are an estimate from intensity × weight × time. */
@Composable
fun LogActivityDialog(initialName: String, weightKg: Double, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var minutes by remember { mutableStateOf("60") }
    var intensity by remember { mutableStateOf(intensities[1]) }
    val mins = minutes.toDoubleOrNull() ?: 0.0
    val kcal = ((intensity.met - 1) * weightKg * mins / 60).roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log an activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Activity") }, singleLine = true)
                NumberField("Duration", minutes, { minutes = it }, Modifier.fillMaxWidth(), "min")
                ChipRow(intensities, intensity, { it.label }, { intensity = it })
                Muted("e.g. ${intensity.examples}", style = MaterialTheme.typography.bodySmall)
                Text("≈ $kcal kcal burned", style = MaterialTheme.typography.titleMedium)
                Muted("Logged for today. Counts as the day's workout at 20+ minutes on a cardio or activity day.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minutes.toIntOrNull() ?: return@TextButton
                if (m <= 0 || name.isBlank()) return@TextButton
                scope.launch {
                    c.importer.logManual(LocalDate.now(), name.trim(), m, kcal.toDouble())
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
