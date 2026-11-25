package com.oblutack.timenote.backup

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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oblutack.timenote.MainActivity
import com.oblutack.timenote.TimenoteApplication
import com.oblutack.timenote.core.logError
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "backup_reminder"
private const val NOTIFICATION_ID = 4101
private const val WORK_NAME = "timenote-backup-reminder"

/** Where tapping the notification leads. */
const val EXTRA_OPEN_ROUTE = "open_route"

/** Once a day the system wakes this up; it sends at most one quiet notification per 30 days (see BackupReminderCheck). */
object BackupReminderScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** A backup was just made: the notification, if it is still showing, is out of date. */
    fun clearNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

class BackupReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val check = (applicationContext as? TimenoteApplication)?.container?.backupReminderCheck ?: return Result.success()
        return try {
            val now = System.currentTimeMillis()
            val text = check.textIfDue(now) ?: return Result.success()
            // Without the notification permission nothing is sent or recorded: it is tried again tomorrow
            if (!canNotify()) return Result.success()
            show(text)
            check.markNotified(now)
            Result.success()
        } catch (e: Exception) {
            logError("BackupReminder", "Backup reminder check failed", e)
            Result.success() // a reminder is not worth a retry storm; the next daily run looks again
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun show(text: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Its own channel, so it can be muted in the system settings without touching the timer notification
            val channel = NotificationChannel(CHANNEL_ID, "Backup reminders", NotificationManager.IMPORTANCE_LOW)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ROUTE, "settings")
        }
        val pending = PendingIntent.getActivity(context, 10, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Back up your timenotes")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text Open Settings to save a backup file."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
