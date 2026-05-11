package com.oblutack.timenote

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.oblutack.timenote.data.database.AppDatabase
import com.oblutack.timenote.data.database.instantiateDatabase

// 1. THIS IS THE MAGIC FIX: A true Singleton DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings.preferences_pb")

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val dbBuilder = Room.databaseBuilder(
            context = applicationContext,
            klass = AppDatabase::class.java,
            name = "timenotes.db"
        ).addMigrations(com.oblutack.timenote.data.database.MIGRATION_1_2)

        val database = instantiateDatabase(dbBuilder)

        // 2. Initialize with the Singleton!
        com.oblutack.timenote.data.repository.SettingsRepository.initialize(applicationContext.dataStore)
        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager = AndroidTimerServiceManager(applicationContext)

        setContent {
            App(database = database)
        }
    }
}