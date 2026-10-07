package com.oblutack.timenote.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oblutack.timenote.TimenoteApplication
import com.oblutack.timenote.core.logError
import java.util.concurrent.TimeUnit

/** Background syncs through WorkManager: only with a connection, spread out by the system to save battery. */
class AndroidSyncScheduler(private val context: Context) : SyncScheduler {
    private val work get() = WorkManager.getInstance(context)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleAfterEdit(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // REPLACE moves the pending sync back with every edit, so a burst of edits causes one sync
        work.enqueueUniqueWork(AFTER_EDIT, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelAll() {
        work.cancelUniqueWork(PERIODIC)
        work.cancelUniqueWork(AFTER_EDIT)
    }

    private companion object {
        const val PERIODIC = "timenote-sync-periodic"
        const val AFTER_EDIT = "timenote-sync-after-edit"
        const val PERIODIC_HOURS = 6L
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as? TimenoteApplication)?.container?.syncManager ?: return Result.success()
        return try {
            if (manager.runBackgroundSync().shouldRetryLater()) Result.retry() else Result.success()
        } catch (e: Exception) {
            logError("SyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
