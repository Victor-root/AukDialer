package auk.dialer.vroot.controller.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.concurrent.thread

/**
 * The dial pad's key tones. They play on the DTMF stream, which phones tie to the ring volume, at
 * the share of it chosen in the settings, and stay silent in silent and vibrate modes, as the
 * system's own dialer does.
 */
object DialpadTones {

    const val DEFAULT_VOLUME = 80

    fun volume(prefs: PreferenceManager): Int = prefs.getInt(PreferenceManager.KEY_DTMF_TONE_VOLUME, DEFAULT_VOLUME)

    /** Null when the audio service has no tone generator to spare: the keys then just stay silent. */
    fun generator(volume: Int): ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, volume) }.getOrNull()

    fun shouldPlay(context: Context, prefs: PreferenceManager): Boolean =
        prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, true) &&
            context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    fun play(generator: ToneGenerator?, key: String) {
        val tone = when (key) {
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "0" -> ToneGenerator.TONE_DTMF_0
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        generator?.startTone(tone, TONE_DURATION_MS)
    }

    /** A key's tone at [volume], to hear a level before keeping it. */
    fun sample(volume: Int) {
        // Its volume is fixed when a generator is made, so each level heard gets its own, released
        // once the tone is over, off the main thread since both talk to the audio service.
        thread {
            val generator = generator(volume) ?: return@thread
            play(generator, "5")
            Thread.sleep(TONE_DURATION_MS + SAMPLE_RELEASE_MARGIN_MS)
            generator.release()
        }
    }

    private const val TONE_DURATION_MS = 120
    private const val SAMPLE_RELEASE_MARGIN_MS = 80L
}
