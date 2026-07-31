package com.grinch.rivo4.controller.vvm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.grinch.rivo4.MainActivity
import com.grinch.rivo4.R

/**
 * Posts the "new voicemail" notification when a sync imports fresh messages.
 * No-ops silently when notifications are disabled, so the worker never crashes
 * on a missing POST_NOTIFICATIONS grant.
 */
class VvmNotifier(private val context: Context) {

    fun notifyNewVoicemails(newCount: Int) {
        if (newCount <= 0) return
        ensureChannel()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_VOICEMAIL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_VOICEMAIL,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_voicemail)
            .setContentTitle(context.getString(R.string.voicemail_notif_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.voicemail_notif_text,
                    newCount,
                    newCount,
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_voicemail),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "voicemail_channel"
        const val NOTIFICATION_ID = 1_001
        const val ACTION_VIEW_VOICEMAIL = "com.grinch.rivo4.ACTION_VIEW_VOICEMAIL"
        private const val REQUEST_CODE_OPEN_VOICEMAIL = 1_001
    }
}
