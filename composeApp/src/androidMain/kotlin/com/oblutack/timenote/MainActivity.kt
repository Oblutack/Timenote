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
import kotlinx.coroutines.launch
import com.oblutack.timenote.data.database.AppDatabase
import com.oblutack.timenote.data.database.instantiateDatabase
import com.oblutack.timenote.data.database.ALL_MIGRATIONS
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_timer.domain.AudioLocator
import com.oblutack.timenote.feature_timer.domain.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// 1. THIS IS THE MAGIC FIX: A true Singleton DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings.preferences_pb")

class MainActivity : ComponentActivity() {

    // 1. UPDATE: Ask for BOTH Notifications AND Microphone
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle denied states here if needed in the future
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. UPDATE: Launch the permission request array
        val permissionsToRequest = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())

        val dbBuilder = Room.databaseBuilder(
            context = applicationContext,
            klass = AppDatabase::class.java,
            name = "timenotes.db"
        )
            .addMigrations(*ALL_MIGRATIONS)

        val database = instantiateDatabase(dbBuilder)

        SettingsRepository.initialize(applicationContext.dataStore)
        ServiceLocator.timerServiceManager = AndroidTimerServiceManager(applicationContext)

        // 3. NEW: Inject the Audio Recorder
        AudioLocator.audioRecorder = AndroidAudioRecorder(applicationContext)
        AudioLocator.audioPlayer = AndroidAudioPlayer(applicationContext)


        // Move memos recorded by older versions out of the OS-clearable cache directory
        CoroutineScope(Dispatchers.IO).launch {
            VoiceMemoMigration(applicationContext, database.timenoteDao()).run()
        }

        setContent {
            App(database = database)
        }
    }
}