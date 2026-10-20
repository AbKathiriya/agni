package com.akash.agni.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akash.agni.container
import com.akash.agni.data.LengthUnit
import com.akash.agni.data.Settings
import com.akash.agni.data.WeightUnit
import com.akash.agni.data.height
import com.akash.agni.data.oneDp
import com.akash.agni.data.weight
import com.akash.agni.data.ThemeMode
import com.akash.agni.domain.Targets
import com.akash.agni.health.HealthConnectManager
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.ChipRow
import com.akash.agni.ui.components.KeyValue
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.NumberField
import com.akash.agni.ui.components.commas
import com.akash.agni.ui.components.short
import com.akash.agni.update.UpdateCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    fun update(t: (Settings) -> Settings) = scope.launch { c.settings.update(t) }

    var hcStatus by remember { mutableStateOf(c.health.status()) }
    var hcGranted by remember { mutableStateOf(false) }
    val refreshHc: () -> Unit = {
        hcStatus = c.health.status()
        scope.launch { hcGranted = c.health.hasAll() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(c.health.permissionContract()) { refreshHc() }
    LaunchedEffect(Unit) { refreshHc() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshHc() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        AgniCard {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.themeMode == m,
                        onClick = { update { it.copy(themeMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        AgniCard {
            Text("Units", style = MaterialTheme.typography.titleMedium)
            Muted("Body weight and lifting weights")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WeightUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = s.weightUnit == u,
                        onClick = { update { it.copy(weightUnit = u) } },
                        shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                        label = { Text(if (u == WeightUnit.KG) "Kilograms (kg)" else "Pounds (lb)") },
                    )
                }
            }
            Muted("Height and body measurements")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                LengthUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = s.lengthUnit == u,
                        onClick = { update { it.copy(lengthUnit = u) } },
                        shape = SegmentedButtonDefaults.itemShape(i, LengthUnit.entries.size),
                        label = { Text(if (u == LengthUnit.IN) "Inches (in)" else "Centimeters (cm)") },
                    )
                }
            }
            Muted("Past entries are converted automatically. You can also switch units inside the check-in.", style = MaterialTheme.typography.bodySmall)
        }

        AgniCard {
            Text("Samsung Health & Google Fit", style = MaterialTheme.typography.titleMedium)
            Muted("Both sync through Health Connect. In Samsung Health, open Settings › Health Connect and allow it to share steps, heart rate, exercise and weight.")
            when (hcStatus) {
                HealthConnectManager.Status.AVAILABLE -> if (hcGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Connected", style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    Button(onClick = { permissionLauncher.launch(c.health.permissions) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect Health Connect")
                    }
                }
                HealthConnectManager.Status.NEEDS_UPDATE, HealthConnectManager.Status.UNAVAILABLE ->
                    Button(onClick = { runCatching { context.startActivity(c.health.installIntent()) } }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (hcStatus == HealthConnectManager.Status.NEEDS_UPDATE) "Update Health Connect" else "Install Health Connect")
                    }
            }
        }

        val weight = s.startWeightKg
        AgniCard {
            Text("Calories", style = MaterialTheme.typography.titleMedium)
            KeyValue("Maintenance (estimated)", "${Targets.maintenance(s, weight).commas()} kcal")
            KeyValue("Daily target", "${Targets.dailyKcal(s, weight).commas()} kcal")
            Muted("Activity level")
            ChipRow(listOf(1.2, 1.375, 1.55), s.activity, { mapOf(1.2 to "Desk", 1.375 to "Light", 1.55 to "Active")[it] ?: "" }, { a -> update { it.copy(activity = a) } })
            Muted("Pace")
            ChipRow(listOf(300, 500, 750), s.deficit, { "−$it/day" }, { d -> update { it.copy(deficit = d) } })
            Muted(
                "−500 a day is roughly ${s.weight(0.45)} a week, a pace you can keep without feeling starved.",
                style = MaterialTheme.typography.bodySmall,
            )
            SettingNumber("Fixed target (0 = automatic)", s.targetOverride.toString(), "kcal") { v -> v.toIntOrNull()?.let { n -> update { it.copy(targetOverride = n) } } }
        }

        AgniCard {
            Text("Profile & goals", style = MaterialTheme.typography.titleMedium)
            if (s.lengthUnit == LengthUnit.CM) {
                SettingNumber("Height", s.heightCm.oneDp(), "cm") { v -> v.toDoubleOrNull()?.let { n -> update { it.copy(heightCm = n) } } }
            } else {
                SettingNumber("Height", (s.heightCm / 2.54).oneDp(), "in") { v ->
                    v.toDoubleOrNull()?.let { n -> update { it.copy(heightCm = n * 2.54) } }
                }
                Muted("= ${s.height()}", style = MaterialTheme.typography.bodySmall)
            }
            SettingNumber("Age", s.age.toString(), "yrs") { v -> v.toIntOrNull()?.let { n -> update { it.copy(age = n) } } }
            SettingNumber("Starting weight", s.weightUnit.fromKg(s.startWeightKg).oneDp(), s.weightUnit.label) { v ->
                v.toDoubleOrNull()?.let { n -> update { it.copy(startWeightKg = it.weightUnit.toKg(n)) } }
            }
            SettingNumber("Protein target", s.proteinTarget.toString(), "g") { v -> v.toIntOrNull()?.let { n -> update { it.copy(proteinTarget = n) } } }
            SettingNumber("Step goal", s.stepGoal.toString(), "steps") { v -> v.toIntOrNull()?.let { n -> update { it.copy(stepGoal = n) } } }
        }

        AgniCard {
            Text("Weekly treat", style = MaterialTheme.typography.titleMedium)
            var name by remember(s.treatName) { mutableStateOf(s.treatName) }
            OutlinedTextField(
                name, { name = it; update { st -> st.copy(treatName = it) } },
                label = { Text("Treat") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            SettingNumber("Extra calories it needs", s.treatKcal.toString(), "kcal") { v -> v.toIntOrNull()?.let { n -> update { it.copy(treatKcal = n) } } }
        }
        UpdateCard(manual = true)

        Muted("Agni · data stays on this phone", Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingNumber(label: String, value: String, suffix: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    NumberField(label, text, {
        text = it
        // Wait until the number is complete ("168." is still being typed).
        if (it.isNotEmpty() && !it.endsWith(".")) onCommit(it)
    }, Modifier.fillMaxWidth(), suffix)
}
