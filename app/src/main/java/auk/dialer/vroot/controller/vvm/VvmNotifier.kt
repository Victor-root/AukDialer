package auk.dialer.vroot.controller.vvm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import auk.dialer.vroot.MainActivity
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager

/**
 * Posts the "new voicemail" notification when a sync imports fresh messages.
 * No-ops silently when notifications are disabled, so the worker never crashes
 * on a missing POST_NOTIFICATIONS grant.
 */
class VvmNotifier(private val context: Context) {

    fun notifyNewVoicemails(newCount: Int) {
        if (newCount <= 0) return
        val channelId = ensureChannel() ?: return

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

        val notification = NotificationCompat.Builder(context, channelId)
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

    /**
     * The channel carrying the sound and vibration chosen in the voicemail settings. Android fixes a
     * channel's sound and vibration once it exists, so each choice gets a channel of its own and the
     * previous one is removed. Called again when the settings change.
     */
    fun ensureChannel(): String? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        val prefs = PreferenceManager(context)
        val ringtone = prefs.getString(PreferenceManager.KEY_VOICEMAIL_RINGTONE, null)
        val vibrate = prefs.getBoolean(PreferenceManager.KEY_VOICEMAIL_VIBRATION, true)
        val channelId = "${CHANNEL_PREFIX}_${Integer.toHexString((ringtone ?: "default").hashCode())}_${if (vibrate) "v" else "s"}"

        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(channelId) != null) return channelId

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notif_channel_voicemail),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            // No value is the phone's default notification sound, an empty one is silence.
            when {
                ringtone == null -> Unit
                ringtone.isEmpty() -> setSound(null, null)
                else -> setSound(
                    Uri.parse(ringtone),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            enableVibration(vibrate)
        }
        manager.createNotificationChannel(channel)
        return channelId
    }

    companion object {
        // Also the id of the single channel earlier versions created, so that one is removed too.
        private const val CHANNEL_PREFIX = "voicemail_channel"
        const val NOTIFICATION_ID = 1_001
        const val ACTION_VIEW_VOICEMAIL = "auk.dialer.vroot.ACTION_VIEW_VOICEMAIL"
        private const val REQUEST_CODE_OPEN_VOICEMAIL = 1_001
    }
}
