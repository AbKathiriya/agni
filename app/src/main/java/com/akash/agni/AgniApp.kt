package com.akash.agni

import android.app.Application
import android.content.Context
import com.akash.agni.data.AgniDatabase
import com.akash.agni.data.Seeder
import com.akash.agni.data.SettingsStore
import com.akash.agni.domain.Engine
import com.akash.agni.health.HealthConnectManager
import com.akash.agni.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Simple manual dependency container; one instance for the whole app. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AgniDatabase.build(context)
    val settings = SettingsStore(context)
    val health = HealthConnectManager(context)
    val engine = Engine(db, settings)
    val updater = Updater(context)

    init {
        scope.launch {
            settings.ensureStartDay()
            Seeder(context, db).seedIfEmpty()
        }
    }
}

class AgniApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as AgniApp).container
