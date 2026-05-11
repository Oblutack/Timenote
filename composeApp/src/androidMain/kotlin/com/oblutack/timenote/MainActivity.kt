package com.oblutack.timenote

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.room.Room
import com.oblutack.timenote.data.database.AppDatabase
import com.oblutack.timenote.data.database.instantiateDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile

class MainActivity : ComponentActivity() {

    // 1. NEW: The Permission Requester for Android 13+ Notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // We can handle denied states later if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. NEW: Ask for Notification Permission as soon as the app opens
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val dbBuilder = Room.databaseBuilder(
            context = applicationContext,
            klass = AppDatabase::class.java,
            name = "timenotes.db"
        ).addMigrations(com.oblutack.timenote.data.database.MIGRATION_1_2)

        val database = instantiateDatabase(dbBuilder)

        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { applicationContext.preferencesDataStoreFile("settings.preferences_pb") }
        )

        com.oblutack.timenote.data.repository.SettingsRepository.initialize(dataStore)

        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager = AndroidTimerServiceManager(applicationContext)

        setContent {
            App(database = database)
        }
    }
}