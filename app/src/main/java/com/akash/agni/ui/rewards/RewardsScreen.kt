package com.akash.agni.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.agni.AppContainer
import com.akash.agni.container
import com.akash.agni.data.GoalType
import com.akash.agni.data.Reward
import com.akash.agni.domain.DayStatus
import com.akash.agni.domain.RewardProgress
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.ChipRow
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.NumberField
import com.akash.agni.ui.components.ProgressBar
import com.akash.agni.ui.components.Ring
import com.akash.agni.ui.components.commas
import com.akash.agni.ui.components.short
import com.akash.agni.ui.theme.NumberStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class RewardsViewModel(private val c: AppContainer) : ViewModel() {
    val snapshot = c.engine.snapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(reward: Reward) = viewModelScope.launch {
        if (reward.id == 0L) c.db.rewards().insert(reward) else c.db.rewards().update(reward)
    }

    fun delete(reward: Reward) = viewModelScope.launch { c.db.rewards().delete(reward) }
}

@Composable
fun RewardsScreen() {
    val c = LocalContext.current.container
    val vm: RewardsViewModel = viewModel { RewardsViewModel(c) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Reward?>(null) }
    val s = snap ?: return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Rewards", style = MaterialTheme.typography.headlineSmall)

        AgniCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Level ${s.level.level}", style = NumberStyle.copy(fontSize = 24.sp))
                Muted("  ·  ${s.level.name}")
            }
            ProgressBar(s.level.progress, height = 8.dp)
            Row {
                Muted("${s.level.totalXp.commas()} XP", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Muted("${s.level.toNext.commas()} XP to level ${s.level.level + 1}", style = MaterialTheme.typography.bodySmall)
            }
        }

        AgniCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Restaurant, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("  Calorie bank", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Text(s.settings.treatName, style = MaterialTheme.typography.titleLarge)
            ProgressBar(s.treatProgress, height = 8.dp)
            val banked = s.bank.coerceAtLeast(0)
            Text(
                when {
                    s.bank < 0 -> "You're ${abs(s.bank).commas()} kcal over for the week. A lighter day or two gets you back."
                    banked >= s.settings.treatKcal -> "${banked.commas()} kcal banked. ${s.settings.treatName} fits this week. Enjoy it."
                    else -> "${banked.commas()} of ${s.settings.treatKcal.commas()} kcal banked. Eat a little under target on weekdays and it fits."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Muted("Resets every Monday. Change the treat in Settings.", style = MaterialTheme.typography.bodySmall)
        }

        AgniCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.streak}-day streak", style = MaterialTheme.typography.titleMedium)
                    Muted("On track = food logged, within calories, workout done on training days", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AcUnit, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(" ${s.freezes}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
                }
            }
            Calendar(s.history)
            Muted("Freezes cover a missed day automatically. You earn one every 7 days on track (max 3).", style = MaterialTheme.typography.bodySmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Milestones", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                editing = Reward(title = "", goalType = GoalType.WAIST_DOWN_IN, target = 1.0, createdDay = LocalDate.now().toEpochDay())
            }) { Text("+ Add") }
        }
        AgniCard(padding = 6.dp) {
            s.rewards.forEachIndexed { i, rp ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MilestoneRow(rp, onClick = { editing = rp.reward })
            }
        }
    }

    editing?.let { r ->
        RewardDialog(
            reward = r,
            onSave = { vm.save(it); editing = null },
            onDelete = if (r.id != 0L) ({ vm.delete(r); editing = null }) else null,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Calendar(history: List<DayStatus>) {
    // Pad to start on Monday so columns line up with weekdays.
    val first = history.firstOrNull()?.day ?: return
    val pad = first.dayOfWeek.value - 1
    val cells: List<DayStatus?> = List(pad) { null } + history
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Muted(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    val shape = RoundedCornerShape(6.dp)
                    val base = Modifier.weight(1f).aspectRatio(1f).clip(shape)
                    when {
                        d == null -> Box(base)
                        d.onTrack -> Box(base.background(MaterialTheme.colorScheme.primary))
                        d.usedFreeze -> Box(base.border(2.dp, MaterialTheme.colorScheme.secondary, shape))
                        else -> Box(base.background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(rp: RewardProgress, onClick: () -> Unit) {
    val r = rp.reward
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r.unlockedDay != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Ring(rp.fraction, 40.dp, 4.dp) {
                Text("${(rp.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(goalLabel(r), style = MaterialTheme.typography.titleSmall)
            Muted(
                (if (r.unlockedDay != null) "Unlocked: " else "") + r.title +
                    if (r.unlockedDay == null) " · ${rp.current.short()} of ${r.target.short()}${r.goalType.unit}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun goalLabel(r: Reward) = when (r.goalType) {
    GoalType.WAIST_DOWN_IN -> "−${r.target.short()}″ waist"
    GoalType.WEIGHT_DOWN_KG -> "−${r.target.short()} kg"
    GoalType.STREAK_DAYS -> "${r.target.roundToInt()}-day streak"
    GoalType.WORKOUTS -> "${r.target.roundToInt()} workouts"
}

@Composable
private fun RewardDialog(reward: Reward, onSave: (Reward) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(if (reward.title.startsWith("Tap to")) "" else reward.title) }
    var type by remember { mutableStateOf(reward.goalType) }
    var target by remember { mutableStateOf(reward.target.short()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reward.id == 0L) "New milestone" else "Edit milestone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Your reward (e.g. new running shoes)") }, singleLine = true)
                Muted("Goal")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChipRow(listOf(GoalType.WAIST_DOWN_IN, GoalType.WEIGHT_DOWN_KG), type, { it.label }, { type = it })
                    ChipRow(listOf(GoalType.STREAK_DAYS, GoalType.WORKOUTS), type, { it.label }, { type = it })
                }
                NumberField("Target", target, { target = it }, suffix = type.unit.trim())
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) { Text("Delete milestone", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = target.toDoubleOrNull() ?: return@TextButton
                val changedGoal = type != reward.goalType || t != reward.target
                onSave(
                    reward.copy(
                        title = title.ifBlank { "Treat yourself" }, goalType = type, target = t,
                        unlockedDay = if (changedGoal) null else reward.unlockedDay,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
