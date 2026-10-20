package com.akash.agni.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.akash.agni.container
import com.akash.agni.ui.components.AgniCard
import com.akash.agni.ui.components.Muted
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val release: Updater.Release, val progress: Float) : UpdateState
    data class Ready(val release: Updater.Release, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * [manual] = Settings: always visible, with a "Check for updates" button.
 * Otherwise (Today screen): checks quietly and only appears when there is something to install.
 */
@Composable
fun UpdateCard(manual: Boolean) {
    val context = LocalContext.current
    val updater = context.container.updater
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun check(force: Boolean) = scope.launch {
        state = UpdateState.Checking
        state = updater.check(force)?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
    }

    fun install(release: Updater.Release, apk: File) {
        if (updater.canInstall()) {
            context.startActivity(updater.installIntent(apk))
        } else {
            // One-time: let Agni install updates. Come back and tap Install again.
            context.startActivity(updater.allowInstallsIntent())
        }
        state = UpdateState.Ready(release, apk)
    }

    fun download(release: Updater.Release) = scope.launch {
        state = UpdateState.Downloading(release, 0f)
        runCatching { updater.download(release) { p -> state = UpdateState.Downloading(release, p) } }
            .onSuccess { install(release, it) }
            .onFailure { state = UpdateState.Failed("Download failed. Check your connection and try again.") }
    }

    LaunchedEffect(Unit) { if (!manual) check(force = false) }

    val visible = manual || state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready
    if (!visible) return

    AgniCard(color = if (manual) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
            Text(if (manual) "App updates" else "Update available", style = MaterialTheme.typography.titleMedium)
        }
        when (val s = state) {
            UpdateState.Idle, UpdateState.UpToDate, UpdateState.Checking, is UpdateState.Failed -> {
                Muted(
                    when (s) {
                        UpdateState.Checking -> "Checking…"
                        UpdateState.UpToDate -> "You're on the latest build (${updater.currentVersionName})."
                        is UpdateState.Failed -> s.message
                        else -> "Installed: ${updater.currentVersionName}"
                    }
                )
                OutlinedButton(onClick = { check(force = true) }, enabled = s != UpdateState.Checking) { Text("Check for updates") }
            }
            is UpdateState.Available -> {
                Muted("Build ${s.release.build} is ready (you have build ${updater.currentBuild}). Your data stays.")
                Button(onClick = { download(s.release) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Update now") }
            }
            is UpdateState.Downloading -> {
                Muted("Downloading build ${s.release.build}…")
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                Muted(
                    if (updater.canInstall()) "Downloaded. Tap Install and confirm."
                    else "Allow Agni to install updates (one time), then come back and tap Install."
                )
                Button(onClick = { install(s.release, s.apk) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Install") }
            }
        }
    }
}
