package com.oblutack.timenote

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.oblutack.timenote.data.database.ALL_MIGRATIONS
import com.oblutack.timenote.data.database.AppDatabase
import com.oblutack.timenote.data.database.instantiateDatabase
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// DataStore must be a process-wide singleton, hence the top-level delegate
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings.preferences_pb")

/**
 * Builds the dependency graph once per process. Activities and the foreground service read it
 * from here instead of rebuilding the database every time they are recreated.
 */
class TimenoteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val database = instantiateDatabase(
            Room.databaseBuilder(applicationContext, AppDatabase::class.java, "timenotes.db")
                .addMigrations(*ALL_MIGRATIONS)
        )
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        container = AppContainer(
            sessionRepository = SessionRepository(database.timenoteDao(), appScope),
            settingsRepository = SettingsRepository(dataStore),
            timerServiceManager = AndroidTimerServiceManager(this),
            audioRecorder = AndroidAudioRecorder(this),
            audioPlayer = AndroidAudioPlayer(this)
        )

        // Move memos recorded by older versions out of the OS-clearable cache directory
        appScope.launch(Dispatchers.IO) {
            VoiceMemoMigration(applicationContext, database.timenoteDao()).run()
        }
    }
}
