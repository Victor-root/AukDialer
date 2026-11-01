package auk.dialer.vroot.controller.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.PowerManager

/**
 * Single-instance MediaPlayer wrapper for spoken audio, voicemails and call recordings, that can
 * play on the loudspeaker or the earpiece. Each item is told apart by its [K] key.
 *
 * All interaction stays on the main thread, audio focus is requested on play and
 * abandoned on release, and every native call is guarded: vendor MediaPlayer
 * implementations throw where AOSP does not.
 */
class SpeechPlayer<K : Any>(
    private val context: Context,
    private val onUpdate: (playingId: K?, isPlaying: Boolean, positionMs: Int, durationMs: Int) -> Unit,
    private val onError: (message: String) -> Unit,
    private val onAudioUnavailable: () -> Unit,
) {
    private var player: MediaPlayer? = null
    private var currentItemId: K? = null
    private var preparing = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var proximityLockArmedAt = 0L
    private var speakerOn = true
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            notifyUpdate()
            updateProximityLock()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    /** Plays [uri] as [id], or pauses and resumes it when it is the current one. */
    fun toggle(id: K, uri: Uri) {
        try {
            if (currentItemId == id && preparing) return
            if (currentItemId == id && player != null) {
                togglePlayPause()
                return
            }
            release()
            startNew(id, uri)
        } catch (e: Exception) {
            release()
            onError(e.message ?: e.javaClass.simpleName)
        }
    }

    fun seekTo(positionMs: Int) {
        if (preparing) return
        try {
            player?.seekTo(positionMs)
            notifyUpdate()
        } catch (_: Exception) {
        }
    }

    /**
     * Switches between the loudspeaker and the earpiece. Takes effect
     * immediately, mid-playback included, and drives the proximity lock so
     * holding the phone to your ear blanks the screen as it does on a call.
     */
    fun setSpeakerOn(enabled: Boolean) {
        speakerOn = enabled
        // Outside playback the route is applied when playback resumes.
        if (isPlaying()) applyAudioRoute()
        updateProximityLock()
    }

    fun release() {
        stopProgressTimer()
        try {
            player?.let {
                try {
                    if (!preparing && it.isPlaying) it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
        currentItemId = null
        preparing = false
        releaseProximityLock()
        releaseAudioSession()
        notifyUpdate()
    }

    /**
     * Hands the audio back whenever playback stops: other apps' music resumes, and the phone
     * leaves the call mode the earpiece needs, which otherwise keeps the volume keys on call volume.
     */
    private fun releaseAudioSession() {
        restoreAudioMode()
        abandonFocus()
    }

    private fun togglePlayPause() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                stopProgressTimer()
                updateProximityLock()
                releaseAudioSession()
                notifyUpdate()
            } else if (requestFocus()) {
                enterCommunicationMode()
                applyAudioRoute()
                mp.start()
                startProgressTimer()
                updateProximityLock()
                notifyUpdate()
            }
        } catch (e: Exception) {
            release()
            onError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun startNew(id: K, uri: Uri) {

        // Checked up front rather than left to MediaPlayer: it swallows the
        // missing-file error internally and surfaces an unrelated one, which
        // would reach the user as a raw failure instead of a plain "this
        // recording is gone". A row can outlive its audio when whichever app
        // imported it deleted the file without clearing the row.
        if (!isAudioReadable(uri)) {
            onAudioUnavailable()
            return
        }

        val mp = MediaPlayer()
        // Voice-communication usage rather than media: it is what lets the
        // stream be steered to the earpiece, and it keeps a single audio setup
        // for both routes so toggling never has to rebuild the player.
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnPreparedListener {
            preparing = false
            try {
                if (requestFocus()) {
                    enterCommunicationMode()
                    applyAudioRoute()
                    it.start()
                    startProgressTimer()
                    updateProximityLock()
                }
                notifyUpdate()
            } catch (e: Exception) {
                release()
                onError(e.message ?: e.javaClass.simpleName)
            }
        }
        mp.setOnCompletionListener {
            stopProgressTimer()
            try {
                it.seekTo(0)
            } catch (_: Exception) {
            }
            releaseProximityLock()
            releaseAudioSession()
            notifyUpdate()
        }
        mp.setOnErrorListener { _, what, extra ->
            release()
            onError("MediaPlayer error: $what/$extra")
            true
        }
        preparing = true
        try {
            mp.setDataSource(context, uri)
            mp.prepareAsync()
        } catch (e: java.io.IOException) {
            // Backstop for a file that disappears between the check and here.
            preparing = false
            runCatching { mp.release() }
            onAudioUnavailable()
            return
        } catch (e: Exception) {
            preparing = false
            runCatching { mp.release() }
            onError(e.message ?: e.javaClass.simpleName)
            return
        }
        player = mp
        currentItemId = id
        // Emit the preparing state without touching native getters: querying
        // duration or position while PREPARING raises a native error on some
        // vendor builds, which then aborts playback through onError.
        onUpdate(currentItemId, false, 0, 0)
    }

    /** True when the provider can actually hand back the recording's bytes. */
    private fun isAudioReadable(uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun startProgressTimer() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun stopProgressTimer() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun notifyUpdate() {
        val mp = player
        if (mp == null || preparing) {
            onUpdate(currentItemId, false, 0, 0)
            return
        }
        val isPlaying = try {
            mp.isPlaying
        } catch (_: Exception) {
            false
        }
        val position = try {
            mp.currentPosition
        } catch (_: Exception) {
            0
        }
        val duration = try {
            mp.duration.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
        onUpdate(currentItemId, isPlaying, position, duration)
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                runCatching { player?.pause() }
                stopProgressTimer()
                releaseProximityLock()
                releaseAudioSession()
                notifyUpdate()
            }
        }
    }

    private fun requestFocus(): Boolean {
        return try {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return true
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonFocus() {
        try {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } catch (_: Exception) {
        }
    }

    /** Communication mode is what makes the earpiece an available output. */
    private fun enterCommunicationMode() {
        try {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
        }
    }

    private fun restoreAudioMode() {
        try {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    private fun applyAudioRoute() {
        try {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wanted = if (speakerOn) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == wanted }
                    ?.let { audioManager.setCommunicationDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = speakerOn
            }
        } catch (_: Exception) {
        }
    }

    private fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    private fun updateProximityLock() {
        if (!speakerOn && isPlaying()) acquireProximityLock() else releaseProximityLock()
    }

    private fun acquireProximityLock() {
        try {
            val powerManager = context.getSystemService(PowerManager::class.java) ?: return
            if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            val lock = proximityWakeLock ?: powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "AukDialer::SpeechProximity",
            ).also {
                // Re-arming its timeout then takes a single acquire, not a matching release each.
                it.setReferenceCounted(false)
                proximityWakeLock = it
            }
            val now = System.currentTimeMillis()
            if (!lock.isHeld || now - proximityLockArmedAt > PROXIMITY_LOCK_REARM_MS) {
                lock.acquire(PROXIMITY_LOCK_TIMEOUT_MS)
                proximityLockArmedAt = now
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseProximityLock() {
        try {
            proximityWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 250L

        /** Safety net: a lock left held would leave the screen dead until the next power press.
         *  Playback re-arms it well before it runs out, so a long recording keeps it. */
        private const val PROXIMITY_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val PROXIMITY_LOCK_REARM_MS = 60 * 1000L
    }
}
