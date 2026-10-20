package com.akash.agni.ui.food

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.agni.container
import com.akash.agni.data.Food
import com.akash.agni.data.FoodLog
import com.akash.agni.data.Meal
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.ChipRow
import com.akash.agni.ui.components.Muted
import com.akash.agni.ui.components.NumberField
import com.akash.agni.ui.components.ProgressBar
import com.akash.agni.ui.components.commas
import com.akash.agni.ui.components.kcal
import com.akash.agni.ui.components.short
import com.akash.agni.ui.theme.NumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** What the add/edit sheet is showing. */
private sealed interface Sheet {
    data class Add(val food: Food) : Sheet
    data class Edit(val log: FoodLog) : Sheet
}

@Composable
fun FoodScreen() {
    val c = LocalContext.current.container
    val vm: FoodViewModel = viewModel { FoodViewModel(c) }
    val day by vm.day.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val frequent by vm.frequent.collectAsStateWithLifecycle()
    val saved by vm.savedMeals.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var preferredMeal by remember { mutableStateOf<Meal?>(null) }
    var saveMealFor by remember { mutableStateOf<Meal?>(null) }
    var createFood by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<Food?>(null) }
    var showMyFoods by remember { mutableStateOf(false) }
    val customFoods by vm.customFoods.collectAsStateWithLifecycle()

    val target = snap?.target ?: 1550
    val focus = remember { FocusRequester() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Food", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
                Text(
                    if (day == LocalDate.now()) "Today" else day.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { vm.shiftDay(1) }, enabled = day.isBefore(LocalDate.now())) {
                    Icon(Icons.Rounded.ChevronRight, "Next day")
                }
            }
        }
        item { Totals(logs, target) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Search thepla, dal, khandvi, paneer…") },
                supportingText = { preferredMeal?.let { m -> Text("Adding to ${m.label}") } },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (query.isNotBlank()) {
            items(results, key = { it.id }) { f ->
                AgniCard(onClick = { sheet = Sheet.Add(f) }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Muted("1 ${f.unit} · ${f.category}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${f.kcal.kcal()} kcal", style = MaterialTheme.typography.bodyMedium)
                        if (f.custom) {
                            IconButton(onClick = { editingFood = f }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${f.name}")
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { createFood = true }) { Text("+ Create “${query.trim()}” as a custom food") }
            }
        } else {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { m ->
                        AssistChip(onClick = { vm.logSavedMeal(m) }, label = { Text("+ ${m.name}") })
                    }
                    frequent.filter { it.useCount > 0 }.take(5).forEach { f ->
                        AssistChip(onClick = { sheet = Sheet.Add(f) }, label = { Text(f.name) })
                    }
                    if (customFoods.isNotEmpty()) {
                        AssistChip(
                            onClick = { showMyFoods = true },
                            label = { Text("My foods (${customFoods.size})") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            Meal.entries.forEach { meal ->
                item(key = meal.name) {
                    MealSection(
                        meal = meal,
                        items = logs.filter { it.meal == meal },
                        onAdd = { preferredMeal = meal; focus.requestFocus() },
                        onItem = { sheet = Sheet.Edit(it) },
                        onSave = { saveMealFor = meal },
                    )
                }
            }
        }
    }

    when (val s = sheet) {
        is Sheet.Add -> AddSheet(
            title = s.food.name, unit = s.food.unit, kcalPer = s.food.kcal, proteinPer = s.food.protein,
            initialServings = 1.0, initialMeal = preferredMeal ?: vm.defaultMeal(),
            confirmLabel = "Add",
            onConfirm = { servings, meal -> vm.add(s.food, meal, servings); sheet = null; vm.query.value = ""; preferredMeal = null },
            onDelete = null,
            onDismiss = { sheet = null },
        )
        is Sheet.Edit -> AddSheet(
            title = s.log.name, unit = s.log.unit, kcalPer = s.log.kcalPer, proteinPer = s.log.proteinPer,
            initialServings = s.log.servings, initialMeal = s.log.meal,
            confirmLabel = "Save",
            onConfirm = { servings, meal -> vm.update(s.log, servings, meal); sheet = null },
            onDelete = { vm.delete(s.log); sheet = null },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    saveMealFor?.let { meal ->
        NameDialog(
            title = "Save ${meal.label.lowercase()} as a quick meal",
            initial = "My usual ${meal.label.lowercase()}",
            onConfirm = { name -> vm.saveMeal(name, meal, logs.filter { it.meal == meal }); saveMealFor = null },
            onDismiss = { saveMealFor = null },
        )
    }

    if (createFood) {
        CustomFoodDialog(
            existing = null,
            initialName = query.trim(),
            onConfirm = { name, unit, k, p, cb, f ->
                createFood = false
                vm.createFood(name, unit, k, p, cb, f) { food -> sheet = Sheet.Add(food) }
            },
            onDelete = null,
            onDismiss = { createFood = false },
        )
    }

    if (showMyFoods) {
        MyFoodsSheet(
            foods = customFoods,
            onEdit = { editingFood = it },
            onDismiss = { showMyFoods = false },
        )
    }

    editingFood?.let { food ->
        CustomFoodDialog(
            existing = food,
            initialName = food.name,
            onConfirm = { name, unit, k, p, cb, f ->
                vm.updateFood(food.copy(name = name, unit = unit, kcal = k, protein = p, carbs = cb, fat = f))
                editingFood = null
            },
            onDelete = {
                vm.deleteFood(food)
                editingFood = null
                if (customFoods.size <= 1) showMyFoods = false
            },
            onDismiss = { editingFood = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyFoodsSheet(foods: List<Food>, onEdit: (Food) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("My foods", style = MaterialTheme.typography.titleLarge)
            Muted("Tap a food to edit or delete it.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 8.dp)) {
                items(foods, key = { it.id }) { f ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onEdit(f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            Muted(
                                "1 ${f.unit} · ${f.kcal.kcal()} kcal · ${f.protein.roundToInt()} g protein",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${f.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun Totals(logs: List<FoodLog>, target: Int) {
    val eaten = logs.sumOf { it.kcal }
    AgniCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(eaten.kcal(), style = NumberStyle.copy(fontSize = 26.sp))
            Muted(" / ${target.commas()} kcal", Modifier.weight(1f))
            val left = target - eaten.roundToInt()
            Text(
                if (left >= 0) "${left.commas()} left" else "${(-left).commas()} over",
                color = if (left >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        ProgressBar((eaten / target).toFloat())
        Row {
            Macro("Protein", logs.sumOf { it.protein }, Modifier.weight(1f))
            Macro("Carbs", logs.sumOf { it.carbs }, Modifier.weight(1f))
            Macro("Fat", logs.sumOf { it.fat }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double, modifier: Modifier) {
    Row(modifier) {
        Muted("$label ", style = MaterialTheme.typography.bodySmall)
        Text("${grams.roundToInt()} g", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MealSection(meal: Meal, items: List<FoodLog>, onAdd: () -> Unit, onItem: (FoodLog) -> Unit, onSave: () -> Unit) {
    AgniCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meal.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (items.isNotEmpty()) Text(items.sumOf { it.kcal }.kcal(), style = MaterialTheme.typography.titleSmall)
            FilledTonalIconButton(onClick = onAdd, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Add to ${meal.label}")
            }
        }
        items.forEach { log ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onItem(log) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(log.name, style = MaterialTheme.typography.bodyMedium)
                    Muted("${log.servings.short()} ${log.unit}", style = MaterialTheme.typography.bodySmall)
                }
                Muted(log.kcal.kcal())
            }
        }
        if (items.size >= 2) {
            TextButton(onClick = onSave, contentPadding = PaddingValues(0.dp)) { Text("Save as quick meal") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
    title: String,
    unit: String,
    kcalPer: Double,
    proteinPer: Double,
    initialServings: Double,
    initialMeal: Meal,
    confirmLabel: String,
    onConfirm: (Double, Meal) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var servings by remember { mutableDoubleStateOf(initialServings) }
    var meal by remember { mutableStateOf(initialMeal) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { servings = (servings - 0.5).coerceAtLeast(0.5) }) {
                    Icon(Icons.Rounded.Remove, "Less")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(servings.short(), style = NumberStyle.copy(fontSize = 32.sp))
                    Muted(unit)
                }
                FilledTonalIconButton(onClick = { servings += 0.5 }) { Icon(Icons.Rounded.Add, "More") }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${(kcalPer * servings).kcal()} kcal", style = MaterialTheme.typography.titleMedium)
                Muted("${(proteinPer * servings).roundToInt()} g protein")
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ChipRow(Meal.entries, meal, { it.label }, { meal = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete, modifier = Modifier.weight(1f).height(52.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                Button(onClick = { onConfirm(servings, meal) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(confirmLabel) }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomFoodDialog(
    existing: Food?,
    initialName: String,
    onConfirm: (String, String, Double, Double, Double, Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var unit by remember { mutableStateOf(existing?.unit ?: "katori") }
    var kcal by remember { mutableStateOf(existing?.kcal?.short().orEmpty()) }
    var protein by remember { mutableStateOf(existing?.protein?.short().orEmpty()) }
    var carbs by remember { mutableStateOf(existing?.carbs?.short().orEmpty()) }
    var fat by remember { mutableStateOf(existing?.fat?.short().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “$name”?") },
            text = { Text("It disappears from search and quick meals. Days you already logged it keep their calories.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Custom food" else "Edit food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Serving (piece, katori, plate…)") }, singleLine = true)
                NumberField("Calories per serving", kcal, { kcal = it }, suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Protein", protein, { protein = it }, Modifier.weight(1f), "g")
                    NumberField("Carbs", carbs, { carbs = it }, Modifier.weight(1f), "g")
                    NumberField("Fat", fat, { fat = it }, Modifier.weight(1f), "g")
                }
                if (existing != null) {
                    Muted("Changes apply from now on. Meals you already logged keep their old values.", style = MaterialTheme.typography.bodySmall)
                }
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("Delete food", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = kcal.toDoubleOrNull() ?: return@TextButton
                if (name.isBlank()) return@TextButton
                onConfirm(name.trim(), unit.trim().ifBlank { "serving" }, k,
                    protein.toDoubleOrNull() ?: 0.0, carbs.toDoubleOrNull() ?: 0.0, fat.toDoubleOrNull() ?: 0.0)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
