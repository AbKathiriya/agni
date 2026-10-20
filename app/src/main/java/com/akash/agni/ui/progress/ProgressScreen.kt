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
import com.akash.agni.data.LengthUnit
import com.akash.agni.data.Measurement
import com.akash.agni.data.Settings
import com.akash.agni.data.WeightUnit
import com.akash.agni.data.length
import com.akash.agni.data.lengthDelta
import com.akash.agni.data.oneDp
import com.akash.agni.data.weight
import com.akash.agni.data.weightDelta
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
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** The log dialog remembers the units you last used. */
    fun setUnits(w: WeightUnit, l: LengthUnit) = viewModelScope.launch {
        c.settings.update { it.copy(weightUnit = w, lengthUnit = l) }
    }

    /** Values arrive already converted to kg and inches. */
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
    val st by vm.settings.collectAsStateWithLifecycle()
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
                    Text(allWeights.lastOrNull()?.let { st.weight(it) } ?: "—", style = NumberStyle.copy(fontSize = 28.sp))
                }
                if (allWeights.size >= 2) {
                    Text(
                        st.weightDelta(allWeights.last() - allWeights.first()),
                        color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (weights.size >= 2) WeightChart(weights, st)
            else Muted("Weigh in once a week, same time, after waking. The trend matters more than any single day.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasureTile("Waist", ms.mapNotNull { it.waistIn }, st, Modifier.weight(1f))
            MeasureTile("Hips", ms.mapNotNull { it.hipsIn }, st, Modifier.weight(1f))
            MeasureTile("Thigh", ms.mapNotNull { it.thighIn }, st, Modifier.weight(1f))
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
            initialWeightUnit = st.weightUnit,
            initialLengthUnit = st.lengthUnit,
            onConfirm = { w, wa, h, t, wu, lu ->
                vm.log(w, wa, h, t)
                if (wu != st.weightUnit || lu != st.lengthUnit) vm.setUnits(wu, lu)
                showLog = false
            },
            onDismiss = { showLog = false },
        )
    }
}

@Composable
private fun MeasureTile(label: String, values: List<Double>, st: Settings, modifier: Modifier) {
    StatTile(label, modifier) {
        Text(values.lastOrNull()?.let { st.length(it) } ?: "—", style = NumberStyle.copy(fontSize = 20.sp))
        if (values.size >= 2) {
            Text(st.lengthDelta(values.last() - values.first()), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WeightChart(points: List<Measurement>, st: Settings) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ws = points.map { it.weightKg!! }
    val minW = ws.min() - 0.5
    val maxW = ws.max() + 0.5
    val minD = points.first().day
    val spanD = (points.last().day - minD).coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = "Weight trend from ${st.weight(ws.first())} to ${st.weight(ws.last())}"
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
    initialWeightUnit: WeightUnit,
    initialLengthUnit: LengthUnit,
    /** weight (kg), waist, hips, thigh (inches), and the units used, to remember them. */
    onConfirm: (Double?, Double?, Double?, Double?, WeightUnit, LengthUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    var wu by remember { mutableStateOf(initialWeightUnit) }
    var lu by remember { mutableStateOf(initialLengthUnit) }
    var weight by remember { mutableStateOf(lastWeight?.let { initialWeightUnit.fromKg(it).oneDp() }.orEmpty()) }
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }

    // Switching units converts whatever is already typed, so nothing has to be re-entered.
    fun switchWeight(to: WeightUnit) {
        weight.toDoubleOrNull()?.let { weight = to.fromKg(wu.toKg(it)).oneDp() }
        wu = to
    }
    fun switchLength(to: LengthUnit) {
        fun conv(v: String) = v.toDoubleOrNull()?.let { to.fromIn(lu.toIn(it)).oneDp() } ?: v
        waist = conv(waist); hips = conv(hips); thigh = conv(thigh)
        lu = to
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's check-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(WeightUnit.entries.toList(), wu, { it.label }, ::switchWeight)
                NumberField("Weight", weight, { weight = it }, Modifier.fillMaxWidth(), wu.label)
                ChipRow(LengthUnit.entries.toList(), lu, { it.label }, ::switchLength, Modifier.padding(top = 4.dp))
                NumberField("Waist (at navel)", waist, { waist = it }, Modifier.fillMaxWidth(), lu.label)
                NumberField("Hips (widest point)", hips, { hips = it }, Modifier.fillMaxWidth(), lu.label)
                NumberField("Thigh (widest point)", thigh, { thigh = it }, Modifier.fillMaxWidth(), lu.label)
                last?.let { Muted("Last: ${LocalDate.ofEpochDay(it.day).format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    weight.toDoubleOrNull()?.let(wu::toKg),
                    waist.toDoubleOrNull()?.let(lu::toIn),
                    hips.toDoubleOrNull()?.let(lu::toIn),
                    thigh.toDoubleOrNull()?.let(lu::toIn),
                    wu, lu,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
