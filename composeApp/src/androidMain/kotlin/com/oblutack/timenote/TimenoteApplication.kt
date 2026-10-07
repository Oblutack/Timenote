package com.oblutack.timenote

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.oblutack.timenote.data.database.ALL_MIGRATIONS
import com.oblutack.timenote.data.database.AppDatabase
import com.oblutack.timenote.data.database.DATABASE_VERSION
import com.oblutack.timenote.data.database.instantiateDatabase
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.oblutack.timenote.core.DirectoryAudioFiles
import java.io.File
import com.oblutack.timenote.backup.AndroidBackupRunner
import com.oblutack.timenote.backup.BackupService
import com.oblutack.timenote.drive.AndroidDriveSession
import com.oblutack.timenote.sync.AndroidAudioNetworkPolicy
import com.oblutack.timenote.sync.AndroidAudioStorage
import com.oblutack.timenote.sync.AudioSync
import com.oblutack.timenote.sync.SyncEngine
import com.oblutack.timenote.sync.AndroidSyncScheduler
import com.oblutack.timenote.sync.SyncManager

// DataStore must be a process-wide singleton, hence the top-level delegate
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings.preferences_pb")

/**
 * Builds the dependency graph once per process. Activities and the foreground service read it
 * from here instead of rebuilding the database every time they are recreated.
 */
class TimenoteApplication : Application() {

    private companion object {
        const val DATABASE_NAME = "timenotes.db"
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        backupDatabaseBeforeUpgrade(this, DATABASE_NAME, DATABASE_VERSION)
        val database = instantiateDatabase(
            Room.databaseBuilder(applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
        )
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val settingsRepository = SettingsRepository(dataStore)

        val audioDir = File(filesDir, "voice_memos")
        val dao = database.timenoteDao()
        val sessionRepository = SessionRepository(dao, appScope, settingsRepository, settingsRepository)

        // Google Drive sync: built here, started by nothing yet (the debug panel can run it; the settings screen comes later)
        val drive = AndroidDriveSession(this)
        val audioSync = AudioSync(dao, drive.store, AndroidAudioStorage(audioDir), AndroidAudioNetworkPolicy(this, settingsRepository))
        val syncEngine = SyncEngine(
            dao = dao,
            remote = drive.store,
            checkpoint = settingsRepository,
            deviceIdSource = settingsRepository,
            writeLock = sessionRepository.writeLock,
            accountId = { drive.accountId() },
            audio = audioSync
        )
        val syncManager = SyncManager(
            engine = syncEngine,
            session = drive,
            prefs = settingsRepository,
            scheduler = AndroidSyncScheduler(this),
            scope = appScope
        )
        // A burst of edits by the user leads to one sync shortly after (nothing happens while sync is off)
        appScope.launch { sessionRepository.localEdits.collect { syncManager.onLocalEdit() } }

        container = AppContainer(
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository,
            timerServiceManager = AndroidTimerServiceManager(this),
            audioRecorder = AndroidAudioRecorder(this),
            audioPlayer = AndroidAudioPlayer(this),
            audioFiles = DirectoryAudioFiles(
                directory = audioDir.absolutePath,
                exists = { File(it).exists() }
            ),
            backupRunner = AndroidBackupRunner(
                context = this,
                service = BackupService(database.timenoteDao(), settingsRepository),
                scope = appScope,
                audioDir = audioDir
            ),
            driveSession = drive,
            syncEngine = syncEngine,
            syncManager = syncManager,
            audioFetcher = audioSync
        )

        // Move memos recorded by older versions out of the OS-clearable cache directory
        appScope.launch(Dispatchers.IO) {
            VoiceMemoMigration(applicationContext, database.timenoteDao()).run()
        }
    }
}
