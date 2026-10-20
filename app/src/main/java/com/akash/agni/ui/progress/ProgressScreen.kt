package com.akash.agni.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.agni.AppContainer
import com.akash.agni.container
import com.akash.agni.data.JournalEntry
import com.akash.agni.data.Measurement
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.ChipRow
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.NumberField
import com.akash.agni.ui.components.StatTile
import com.akash.agni.ui.components.short
import com.akash.agni.ui.components.signed
import com.akash.agni.ui.theme.NumberStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    private val today = LocalDate.now().toEpochDay()
    val measurements = c.db.measurements().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val journal = c.db.journal().forDay(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recentJournal = c.db.journal().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun log(weight: Double?, waist: Double?, hips: Double?, thigh: Double?) = viewModelScope.launch {
        if (weight == null && waist == null && hips == null && thigh == null) return@launch
        c.db.measurements().insert(Measurement(day = today, weightKg = weight, waistIn = waist, hipsIn = hips, thighIn = thigh))
        if (weight != null) c.health.writeWeight(weight)
        c.engine.awardMeasurement(LocalDate.now())
    }

    fun saveJournal(text: String, energy: Int, sleep: Double?) = viewModelScope.launch {
        c.db.journal().upsert(JournalEntry(day = today, text = text, energy = energy, sleepHours = sleep))
    }
}

private enum class Range(val label: String, val days: Long) { W4("4W", 28), M3("3M", 92), ALL("All", Long.MAX_VALUE) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen() {
    val c = LocalContext.current.container
    val vm: ProgressViewModel = viewModel { ProgressViewModel(c) }
    val ms by vm.measurements.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()
    val recent by vm.recentJournal.collectAsStateWithLifecycle()
    var range by remember { mutableStateOf(Range.M3) }
    var showLog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Progress", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            SingleChoiceSegmentedButtonRow {
                Range.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = range == r, onClick = { range = r },
                        shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size),
                        label = { Text(r.label) },
                    )
                }
            }
        }

        val cutoff = if (range == Range.ALL) Long.MIN_VALUE else LocalDate.now().toEpochDay() - range.days
        val weights = ms.filter { it.weightKg != null && it.day >= cutoff }
        val allWeights = ms.mapNotNull { it.weightKg }

        AgniCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Muted("Weight")
                    Text(allWeights.lastOrNull()?.let { "${it.short()} kg" } ?: "—", style = NumberStyle.copy(fontSize = 28.sp))
                }
                if (allWeights.size >= 2) {
                    Text(
                        (allWeights.last() - allWeights.first()).signed(" kg"),
                        color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (weights.size >= 2) WeightChart(weights)
            else Muted("Weigh in once a week, same time, after waking. The trend matters more than any single day.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureTile("Waist", ms.mapNotNull { it.waistIn }, Modifier.weight(1f))
            MeasureTile("Hips", ms.mapNotNull { it.hipsIn }, Modifier.weight(1f))
            MeasureTile("Thigh", ms.mapNotNull { it.thighIn }, Modifier.weight(1f))
        }
        Button(onClick = { showLog = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Log weight & measurements") }

        JournalCard(journal, onSave = vm::saveJournal)

        recent.filter { it.day != LocalDate.now().toEpochDay() && it.text.isNotBlank() }.take(5).forEach { e ->
            AgniCard(padding = 14.dp) {
                Muted(LocalDate.ofEpochDay(e.day).format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.labelMedium)
                Text(e.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showLog) {
        val last = ms.lastOrNull()
        LogDialog(
            last = last,
            lastWeight = ms.lastOrNull { it.weightKg != null }?.weightKg,
            onConfirm = { w, wa, h, t -> vm.log(w, wa, h, t); showLog = false },
            onDismiss = { showLog = false },
        )
    }
}

@Composable
private fun MeasureTile(label: String, values: List<Double>, modifier: Modifier) {
    StatTile(label, modifier) {
        Text(values.lastOrNull()?.let { "${it.short()}″" } ?: "—", style = NumberStyle.copy(fontSize = 20.sp))
        if (values.size >= 2) {
            Text((values.last() - values.first()).signed("″"), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WeightChart(points: List<Measurement>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ws = points.map { it.weightKg!! }
    val minW = ws.min() - 0.5
    val maxW = ws.max() + 0.5
    val minD = points.first().day
    val spanD = (points.last().day - minD).coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = "Weight trend from ${ws.first().short()} to ${ws.last().short()} kg"
        }
    ) {
        fun pos(m: Measurement) = Offset(
            x = (m.day - minD).toFloat() / spanD * size.width,
            y = ((maxW - m.weightKg!!) / (maxW - minW)).toFloat() * (size.height - 8f) + 4f,
        )
        drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
        val path = Path()
        points.forEachIndexed { i, m -> val p = pos(m); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, line, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(line, radius = 10f, center = pos(points.last()))
    }
}

@Composable
private fun JournalCard(entry: JournalEntry?, onSave: (String, Int, Double?) -> Unit) {
    var text by remember(entry) { mutableStateOf(entry?.text.orEmpty()) }
    var energy by remember(entry) { mutableIntStateOf(entry?.energy ?: 0) }
    var sleep by remember(entry) { mutableStateOf(entry?.sleepHours?.short().orEmpty()) }
    AgniCard {
        Text("Journal · today", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("How did today go? Cravings, wins, what you'll do differently…") },
            minLines = 3,
        )
        Muted("Energy")
        ChipRow(listOf(1, 2, 3, 4, 5), energy, { listOf("Low", "Meh", "OK", "Good", "Great")[it - 1] }, { energy = it })
        NumberField("Sleep last night", sleep, { sleep = it }, suffix = "h")
        FilledTonalButton(onClick = { onSave(text.trim(), energy, sleep.toDoubleOrNull()) }) { Text("Save entry") }
    }
}

@Composable
private fun LogDialog(
    last: Measurement?,
    lastWeight: Double?,
    onConfirm: (Double?, Double?, Double?, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by remember { mutableStateOf(lastWeight?.short().orEmpty()) }
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's check-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Weight", weight, { weight = it }, Modifier.fillMaxWidth(), "kg")
                NumberField("Waist (at navel)", waist, { waist = it }, Modifier.fillMaxWidth(), "in")
                NumberField("Hips (widest point)", hips, { hips = it }, Modifier.fillMaxWidth(), "in")
                NumberField("Thigh (widest point)", thigh, { thigh = it }, Modifier.fillMaxWidth(), "in")
                last?.let { Muted("Last: ${LocalDate.ofEpochDay(it.day).format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(weight.toDoubleOrNull(), waist.toDoubleOrNull(), hips.toDoubleOrNull(), thigh.toDoubleOrNull())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
