package auk.dialer.vroot.controller

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.telecom.TelecomManager
import android.util.Log
import auk.dialer.vroot.BuildConfig

/**
 * Rings for an incoming call in the two cases Telecom cannot handle from the contacts database:
 * a contact set to no ringtone (Telecom plays a fallback ring for an empty ringtone), and a private
 * contact, which Telecom never sees. Telecom's own ringer is silenced and this one takes over,
 * playing the contact's ringtone, or nothing, and vibrating as the phone settings allow.
 */
class ContactRinger(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var vibrating = false
    private var ownSilenceAt = 0L

    val isRinging: Boolean get() = ringtone != null || vibrating

    /** [ringtoneUri] empty means silent. */
    fun start(ringtoneUri: String) {
        stop()
        try {
            ownSilenceAt = SystemClock.elapsedRealtime()
            (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).silenceRinger()
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, "silenceRinger refused", e)
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode

        if (ringtoneUri.isNotEmpty() && ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            audioManager.getStreamVolume(AudioManager.STREAM_RING) > 0
        ) {
            ringtone = try {
                RingtoneManager.getRingtone(context, Uri.parse(ringtoneUri))?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    isLooping = true
                    play()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, "ringtone failed", e)
                null
            }
        }

        // A ringtone-usage vibration follows the phone's ring vibration setting on its own: the
        // system mutes it when that setting is off, as it does for Telecom's.
        if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
            val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE)
                )
                vibrating = true
            }
        }
    }

    /**
     * Telecom tells every in-call app to go quiet when the ringer is silenced, including after the
     * silencing [start] asks for itself. That echo is not the user and must not stop this ringer.
     */
    fun isOwnSilenceEcho(): Boolean {
        val echo = ownSilenceAt != 0L && SystemClock.elapsedRealtime() - ownSilenceAt < OWN_SILENCE_ECHO_MS
        ownSilenceAt = 0L
        return echo
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        if (vibrating) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.cancel()
            vibrating = false
        }
    }

    private companion object {
        const val TAG = "AukCallDebug"
        const val OWN_SILENCE_ECHO_MS = 2000L
    }
}
