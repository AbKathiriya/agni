package com.akash.agni.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.unit.dp
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

@Composable
fun WorkoutScreen(nav: NavHostController) {
    val today = LocalDate.now().dayOfWeek
    var expanded by remember { mutableStateOf<DayOfWeek?>(today) }

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
