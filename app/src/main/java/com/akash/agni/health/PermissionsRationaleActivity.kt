package com.akash.agni.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akash.agni.ui.theme.AgniTheme

/** Shown by Health Connect when you tap "privacy policy" for this app. Required by Health Connect. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgniTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("How Agni uses your health data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Agni is a personal app. It reads steps, distance, active calories, heart rate, weight " +
                                "and workouts recorded by your watch from Health Connect to show your progress, and writes your workouts, meals " +
                                "and weigh-ins back so Samsung Health and other apps see them. Data stays on this " +
                                "phone and is never sent anywhere else.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
    }
}
