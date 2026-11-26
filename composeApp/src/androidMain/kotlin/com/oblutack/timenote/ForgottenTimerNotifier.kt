package com.oblutack.timenote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oblutack.timenote.backup.EXTRA_OPEN_ROUTE
import com.oblutack.timenote.core.STILL_WORKING_ROUTE
import com.oblutack.timenote.core.humanDuration
import com.oblutack.timenote.core.logError
import com.oblutack.timenote.feature_timer.domain.ActiveSessionBackup
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "forgotten_timer"
private const val NOTIFICATION_ID = 4102
private const val WORK_NAME = "timenote-forgotten-timer"

/** One question per running session: a new schedule replaces the old one, ending the session cancels it. */
object ForgottenTimerScheduler {
    fun schedule(context: Context, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ForgottenTimerWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** The session ended: no question is due, and one that is already on the screen is no longer true. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/** Runs when a session has gone on longer than it usually does. Does nothing when no session is running any more. */
class ForgottenTimerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? TimenoteApplication)?.container ?: return Result.success()
        return try {
            // The running session is saved as a backup for as long as it runs; no backup means it ended
            val json = container.settingsRepository.activeSessionBackupFlow.first() ?: return Result.success()
            val session = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<ActiveSessionBackup>(json)
            if (!canNotify()) return Result.success()
            val elapsedSeconds = ((System.currentTimeMillis() - session.startTimeMillis) / 1000).toInt()
            show(session.sessionTitle, elapsedSeconds)
            Result.success()
        } catch (e: Exception) {
            logError("ForgottenTimer", "Could not ask whether the session is still going", e)
            Result.success() // a question is not worth a retry storm
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun show(title: String, elapsedSeconds: Int) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Its own channel (default importance, so it is noticed) that can be muted without touching the timer notification
            val channel = NotificationChannel(CHANNEL_ID, "Still working?", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ROUTE, STILL_WORKING_ROUTE)
        }
        val pending = PendingIntent.getActivity(context, 11, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val what = title.ifBlank { "Your session" }
        val text = "$what has been running for ${humanDuration(elapsedSeconds)}. Tap to keep going or end it."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Still working?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
