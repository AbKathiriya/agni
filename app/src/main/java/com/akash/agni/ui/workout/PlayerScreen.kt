package com.akash.agni.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.akash.agni.container
import com.akash.agni.data.Block
import com.akash.agni.data.Kind
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.Ring
import com.akash.agni.ui.components.short
import com.akash.agni.ui.theme.NumberStyle

@Composable
fun PlayerScreen(workoutId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val vm: PlayerViewModel = viewModel(key = "player_$workoutId") {
        PlayerViewModel(c, context.applicationContext, workoutId)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf<String?>(null) }

    // Keep the screen on while training.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = s.phase != Phase.DONE) { confirmExit = true }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("End workout?") },
            text = { Text("Sets you've done are saved. It counts as complete once you've done at least half.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.finish() }) { Text("Save and finish") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }

    videoUrl?.let { VideoDialog(it, onClose = { videoUrl = null }) }

    val summary = s.summary
    if (s.phase == Phase.DONE) {
        if (summary == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            SummaryView(vm.workout.title, summary, onExit)
        }
        return
    }

    val block = vm.block
    val shownBlock = if (s.phase == Phase.REST) vm.blockAt(s.nextBlockIndex) ?: block else block

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { confirmExit = true }) { Icon(Icons.Rounded.Close, contentDescription = "End workout") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.workout.title, style = MaterialTheme.typography.titleMedium)
                Muted("Exercise ${s.blockIndex + 1} of ${vm.blockCount} · ${clock(s.elapsedSec)} elapsed", style = MaterialTheme.typography.bodySmall)
            }
            HeartChip(s.heartRate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(vm.blockCount) { i ->
                val color = when {
                    i < s.blockIndex -> MaterialTheme.colorScheme.primary
                    i == s.blockIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }

        DemoMedia(shownBlock, onVideo = { videoUrl = shownBlock.exercise.youtubeUrl })

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.phase == Phase.REST) Muted("Up next")
            Text(shownBlock.exercise.name, style = NumberStyle.copy(fontSize = 28.sp))
            val setNo = if (s.phase == Phase.REST) s.nextSet else s.set
            Muted("Set $setNo of ${shownBlock.sets} · ${shownBlock.prescription} · ${shownBlock.exercise.muscles}")
            shownBlock.exercise.cues.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            if (s.phase == Phase.WORK && s.hint != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(s.hint!!, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        when {
            s.phase == Phase.REST -> RestPanel(s, vm)
            block.kind == Kind.REPS -> RepsPanel(s, vm)
            else -> TimedPanel(s, vm, block)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = vm::previousExercise, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous exercise")
            }
            FilledIconButton(onClick = vm::toggleRunning, modifier = Modifier.size(64.dp)) {
                Icon(if (s.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (s.running) "Pause" else "Resume")
            }
            FilledTonalIconButton(onClick = vm::skipExercise, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Skip exercise")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeartChip(bpm: Int?) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(bpm?.toString() ?: "—", style = MaterialTheme.typography.titleSmall)
            Text(" bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DemoMedia(block: Block, onVideo: () -> Unit) {
    val frames = block.exercise.frames
    var frame by remember(block.exerciseId) { mutableIntStateOf(0) }
    // Alternate start and end position photos so the movement reads like a slow animation.
    LaunchedEffect(block.exerciseId) {
        if (frames.size > 1) while (true) {
            delay(1100)
            frame = (frame + 1) % frames.size
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (frames.isEmpty()) {
            Icon(Icons.Rounded.PlayCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Crossfade(targetState = frame, animationSpec = tween(350), label = "exercise frames") { i ->
                AsyncImage(
                    model = frames[i],
                    contentDescription = "${block.exercise.name}, " + if (i == 0) "start position" else "end position",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        FilledTonalButton(onClick = onVideo, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
            Icon(Icons.Rounded.SmartDisplay, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Video")
        }
    }
}

@Composable
private fun RepsPanel(s: PlayerState, vm: PlayerViewModel) {
    AgniCard {
        Stepper("Reps", s.reps.toString(), onMinus = { vm.setReps(-1) }, onPlus = { vm.setReps(1) })
        Stepper("Weight", "${s.weightKg.short()} kg", onMinus = { vm.setWeight(-2.5) }, onPlus = { vm.setWeight(2.5) })
    }
    Button(onClick = vm::completeSet, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Done set", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TimedPanel(s: PlayerState, vm: PlayerViewModel, block: Block) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Ring(fraction = if (s.phaseTotal > 0) s.secondsLeft.toFloat() / s.phaseTotal else 0f, size = 180.dp, stroke = 12.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Muted(
                    when {
                        block.kind == Kind.CARDIO -> "Keep moving"
                        s.switchSides -> "Switch sides"
                        else -> "Hold"
                    }
                )
                Text(clock(s.secondsLeft), style = NumberStyle.copy(fontSize = 44.sp))
            }
        }
    }
    OutlinedButton(onClick = vm::completeSet, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(if (block.kind == Kind.CARDIO) "Finish this block" else "Done")
    }
}

@Composable
private fun RestPanel(s: PlayerState, vm: PlayerViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Ring(fraction = if (s.phaseTotal > 0) s.secondsLeft.toFloat() / s.phaseTotal else 0f, size = 180.dp, stroke = 12.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Muted("Rest")
                Text(clock(s.secondsLeft), style = NumberStyle.copy(fontSize = 44.sp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { vm.addRest(15) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("+15 s") }
        Button(onClick = vm::skipRest, modifier = Modifier.weight(1f).height(52.dp)) { Text("Skip rest") }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Muted(label, Modifier.weight(1f))
        IconButton(onClick = onMinus, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
            Icon(Icons.Rounded.Remove, contentDescription = "Less $label")
        }
        Text(value, Modifier.width(96.dp), style = NumberStyle.copy(fontSize = 22.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
            Icon(Icons.Rounded.Add, contentDescription = "More $label")
        }
    }
}

@Composable
private fun SummaryView(title: String, summary: Summary, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (summary.completed) "Workout done" else "Workout saved", style = MaterialTheme.typography.headlineMedium)
        Muted(title)
        if (summary.xp > 0) Text("+${summary.xp} XP", style = NumberStyle.copy(fontSize = 40.sp), color = MaterialTheme.colorScheme.primary)
        AgniCard(Modifier.fillMaxWidth()) {
            com.akash.agni.ui.components.KeyValue("Time", "${summary.minutes} min")
            com.akash.agni.ui.components.KeyValue("Sets", summary.sets.toString())
            com.akash.agni.ui.components.KeyValue("Avg heart rate", summary.avgHr?.let { "$it bpm" } ?: "Syncing from watch")
            com.akash.agni.ui.components.KeyValue("Active calories", summary.kcal?.let { "$it kcal" } ?: "Syncing from watch")
        }
        if (!summary.completed) Muted("Do at least half the sets for it to count toward your streak.")
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back") }
    }
}

private fun clock(sec: Int): String = "%d:%02d".format(sec.coerceAtLeast(0) / 60, sec.coerceAtLeast(0) % 60)
