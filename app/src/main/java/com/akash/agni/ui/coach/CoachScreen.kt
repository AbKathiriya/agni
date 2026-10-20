package com.akash.agni.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.agni.AppContainer
import com.akash.agni.container
import com.akash.agni.domain.Tip
import com.akash.agni.domain.TipKind
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.Muted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CoachViewModel(private val c: AppContainer) : ViewModel() {
    private val _tips = MutableStateFlow<List<Tip>?>(null)
    val tips = _tips.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val snap = c.engine.snapshot().filterNotNull().first()
        _tips.value = c.coach.tips(snap.settings, snap)
    }
}

fun TipKind.icon(): ImageVector = when (this) {
    TipKind.FOOD -> Icons.Rounded.Restaurant
    TipKind.ACTIVITY -> Icons.Rounded.DirectionsWalk
    TipKind.HABIT -> Icons.Rounded.Bedtime
    TipKind.WIN -> Icons.Rounded.EmojiEvents
}

@Composable
fun TipRow(tip: Tip) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            tip.kind.icon(), contentDescription = tip.kind.label,
            tint = if (tip.kind == TipKind.WIN) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp, end = 12.dp).size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tip.title, style = MaterialTheme.typography.titleSmall)
            Muted(tip.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Compact version for the Today screen: the top few suggestions and a link to all of them. */
@Composable
fun CoachCard(tips: List<Tip>, onSeeAll: () -> Unit) {
    val top = tips.filter { it.kind != TipKind.WIN }.take(2).ifEmpty { tips.take(1) }
    if (top.isEmpty()) return
    AgniCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("  Coach", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (tips.size > top.size) TextButton(onClick = onSeeAll) { Text("See all ${tips.size}") }
        }
        top.forEach { TipRow(it) }
    }
}

@Composable
fun CoachScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: CoachViewModel = viewModel { CoachViewModel(c) }
    val tips by vm.tips.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("Coach", style = MaterialTheme.typography.headlineSmall)
            }
            Muted(
                "Based on your last 7 days of food, workouts, steps, weight and sleep. Updates as you log. " +
                    "General fitness guidance, not medical advice.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val list = tips
        if (list == null) {
            item { CircularProgressIndicator(Modifier.padding(24.dp)) }
        } else if (list.isEmpty()) {
            item { AgniCard { Muted("Nothing to change right now. Keep logging and check back in a few days.") } }
        } else {
            TipKind.entries.forEach { kind ->
                val group = list.filter { it.kind == kind }
                if (group.isNotEmpty()) {
                    item(key = kind.name) {
                        AgniCard {
                            Text(kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            group.forEach { TipRow(it) }
                        }
                    }
                }
            }
        }
    }
}
