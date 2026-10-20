package com.akash.agni.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.akash.agni.container
import com.akash.agni.data.weight
import com.akash.agni.data.weightDelta
import com.akash.agni.domain.Snapshot
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.KeyValue
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.ProgressBar
import com.akash.agni.ui.components.Ring
import com.akash.agni.ui.components.StatTile
import com.akash.agni.ui.components.commas
import com.akash.agni.ui.components.kcal
import com.akash.agni.ui.components.short
import com.akash.agni.ui.goTab
import com.akash.agni.ui.startWorkout
import com.akash.agni.ui.theme.NumberStyle
import com.akash.agni.update.UpdateCard
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(nav: NavHostController) {
    val c = LocalContext.current.container
    val vm: TodayViewModel = viewModel { TodayViewModel(c) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) { vm.refresh() }

    val s = snap ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(s, onLevel = { nav.goTab("rewards") }, onSettings = { nav.navigate("settings") })

        UpdateCard(manual = false)

        if (!connected) {
            AgniCard(color = MaterialTheme.colorScheme.primaryContainer, onClick = { nav.navigate("settings") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Connect Samsung Health", style = MaterialTheme.typography.titleSmall)
                        Muted("Steps, heart rate and calories from your Galaxy Watch")
                    }
                }
            }
        }

        CalorieCard(s, burned = health?.activeKcal)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Streak", Modifier.weight(1f)) {
                Text("${s.streak} " + if (s.streak == 1) "day" else "days", style = NumberStyle.copy(fontSize = 24.sp))
                Text(
                    "${s.freezes} " + if (s.freezes == 1) "freeze saved" else "freezes saved",
                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
                )
            }
            StatTile("Protein", Modifier.weight(1f)) {
                Text("${s.protein.roundToInt()} / ${s.settings.proteinTarget} g", style = NumberStyle.copy(fontSize = 22.sp))
                ProgressBar((s.protein / s.settings.proteinTarget).toFloat(), color = MaterialTheme.colorScheme.secondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Steps", Modifier.weight(1f)) {
                Text(health?.steps?.commas() ?: "—", style = NumberStyle.copy(fontSize = 22.sp))
                ProgressBar(((health?.steps ?: 0L).toFloat() / s.settings.stepGoal), color = MaterialTheme.colorScheme.secondary)
            }
            StatTile("Weight", Modifier.weight(1f)) {
                Text(s.settings.weight(s.currentWeight), style = NumberStyle.copy(fontSize = 22.sp))
                val delta = s.currentWeight - s.settings.startWeightKg
                Muted(if (abs(delta) < 0.05) "Starting point" else "${s.settings.weightDelta(delta)} since start", style = MaterialTheme.typography.bodySmall)
            }
        }

        WorkoutCard(s, onStart = { id -> nav.startWorkout(id) }, onPlan = { nav.goTab("workout") })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(s: Snapshot, onLevel: () -> Unit, onSettings: () -> Unit) {
    val greeting = when (LocalTime.now().hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Muted(s.today.format(DateTimeFormatter.ofPattern("EEE, d MMM")))
            Text("$greeting, Akash", style = MaterialTheme.typography.headlineSmall)
        }
        AssistChip(
            onClick = onLevel,
            label = { Text("Lv ${s.level.level}", fontWeight = FontWeight.SemiBold) },
            leadingIcon = { Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
        )
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun CalorieCard(s: Snapshot, burned: Double?) {
    AgniCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val over = s.left < 0
            Ring(
                fraction = (s.eaten / s.target).toFloat(),
                size = 132.dp, stroke = 12.dp,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(abs(s.left).commas(), style = NumberStyle.copy(fontSize = 30.sp))
                    Muted(if (over) "kcal over" else "kcal left", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyValue("Eaten", s.eaten.kcal())
                KeyValue("Target", s.target.commas())
                KeyValue("Burned", burned?.kcal() ?: "—")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row {
            Text("Weekly bank", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                (if (s.bank >= 0) "+" else "−") + abs(s.bank).commas() + " kcal",
                color = if (s.bank >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        ProgressBar(s.treatProgress)
        val need = s.settings.treatKcal - s.bank
        Muted(
            if (need <= 0) "${s.settings.treatName} is covered. Enjoy it, guilt-free."
            else "${need.commas()} kcal more this week unlocks ${s.settings.treatName}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun WorkoutCard(s: Snapshot, onStart: (String) -> Unit, onPlan: () -> Unit) {
    val w = s.todaysWorkout
    AgniCard(padding = 20.dp) {
        Muted("Today's workout")
        if (w == null) {
            Text("Rest day", style = MaterialTheme.typography.titleLarge)
            Muted("Recovery matters. A relaxed walk still counts toward your steps.")
            Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("See the week") }
            return@AgniCard
        }
        Text(w.title, style = MaterialTheme.typography.titleLarge)
        Muted(w.summary)
        if (s.workoutDoneToday) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Done for today", style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Button(
                onClick = { onStart(w.id) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Start workout", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
