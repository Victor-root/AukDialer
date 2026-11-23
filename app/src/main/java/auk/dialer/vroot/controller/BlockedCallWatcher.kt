package auk.dialer.vroot.controller

import auk.dialer.vroot.controller.util.PrivacyMask
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.CallLog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The notification telling that a call from a blocked number was stopped. */
object BlockedCallNotifier {
    private const val CHANNEL_ID = "blocked_call_channel"

    fun notify(context: Context, number: String, privacy: PrivacyMask) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_blocked_calls),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(context.getString(R.string.notif_blocked_call_title))
            .setContentText(context.getString(R.string.notif_blocked_call_text, privacy.number(number)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(number.hashCode(), builder.build())
    }
}

/**
 * Tells about the calls Android's block list stopped. Those never reach the app: Telecom rejects
 * them before the in-call service hears of them, and only writes them to the call log. So this
 * watches the call log, woken by WorkManager whenever it changes, and notifies each blocked call
 * logged since it last looked. Calls a caller ID and spam app stopped are left to that app, which
 * has its own setting for telling about them.
 */
class BlockedCallWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params), KoinComponent {

    private val preferenceManager: PreferenceManager by inject()

    override fun doWork(): Result {
        val context = applicationContext
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
            return Result.success()
        }
        val seen = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
        val seenUntil = seen.getLong(KEY_SEEN_UNTIL, System.currentTimeMillis())
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            var latest = seenUntil
            val privacy = PrivacyMask.of(context, preferenceManager)
            runCatching {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.BLOCK_REASON} != ?",
                    arrayOf(
                        CallLog.Calls.BLOCKED_TYPE.toString(),
                        seenUntil.toString(),
                        CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE.toString(),
                    ),
                    "${CallLog.Calls.DATE} ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        BlockedCallNotifier.notify(context, cursor.getString(0).orEmpty(), privacy)
                        latest = maxOf(latest, cursor.getLong(1))
                    }
                }
            }
            seen.edit().putLong(KEY_SEEN_UNTIL, latest).apply()
        }
        // A content trigger fires once: the next change of the call log needs a new request,
        // queued behind this one.
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "auk.blocked_calls.watch"
        private const val STATE_FILE = "auk_blocked_calls"
        private const val KEY_SEEN_UNTIL = "seen_until"

        /** Starts watching, or stops it, as the blocked call notifications setting says. */
        fun schedule(context: Context, preferenceManager: PreferenceManager) {
            // WorkManager keeps its queue in storage that stays locked until the first unlock.
            if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
            val seen = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
            if (preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
                // What was blocked before the watch started is old news.
                if (!seen.contains(KEY_SEEN_UNTIL)) {
                    seen.edit().putLong(KEY_SEEN_UNTIL, System.currentTimeMillis()).apply()
                }
                enqueue(context, ExistingWorkPolicy.KEEP)
            } else {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
                seen.edit().remove(KEY_SEEN_UNTIL).apply()
            }
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<BlockedCallWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(CallLog.Calls.CONTENT_URI, true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
