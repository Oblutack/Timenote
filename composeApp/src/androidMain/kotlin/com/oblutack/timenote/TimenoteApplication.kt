package com.oblutack.timenote

import android.app.Application
import android.content.Context
import android.os.Build
import com.oblutack.timenote.core.AppInfo
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
import com.oblutack.timenote.backup.BackupReminderScheduler
import com.oblutack.timenote.backup.BackupService
import com.oblutack.timenote.core.BackupReminderCheck
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
                audioDir = audioDir,
                onExported = {
                    settingsRepository.setLastBackupAt(System.currentTimeMillis())
                    BackupReminderScheduler.clearNotification(this@TimenoteApplication)
                }
            ),
            driveSession = drive,
            syncEngine = syncEngine,
            syncManager = syncManager,
            audioFetcher = audioSync,
            backupReminderCheck = BackupReminderCheck(dao, settingsRepository),
            appInfo = AppInfo(
                versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty(),
                platformSummary = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
            )
        )

        // The first run of this version picks the theme: people who already use the app keep their dark look, a new
        // installation follows the phone. (An installation that was never updated is a new one.)
        val installInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val existingUser = installInfo != null && installInfo.firstInstallTime != installInfo.lastUpdateTime
        appScope.launch { settingsRepository.ensureThemeDefault(existingUser) }

        // What has been in the trash for 30 days is removed for good (and from the cloud copy, when sync is on)
        sessionRepository.purgeOldTrash()

        // A quiet daily check for the backup reminder notification (only people without cloud sync ever get one)
        BackupReminderScheduler.schedule(this)

        // Move memos recorded by older versions out of the OS-clearable cache directory
        appScope.launch(Dispatchers.IO) {
            VoiceMemoMigration(applicationContext, database.timenoteDao()).run()
        }
    }
}
