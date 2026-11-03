package auk.dialer.vroot.controller.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The vibrations the app's own settings turn on. They go to the vibrator directly: the feedback
 * constants of performHapticFeedback (text handle move, confirm, reject) are left unplayed on many
 * phones, so a setting the user had switched on did nothing at all. They also follow the phone's
 * touch vibration strength.
 */
object AukHaptics {
    const val FULL_INTENSITY = 100

    /** Each vibration the settings offer, with the key of its strength and the effect it plays. */
    enum class Vibration(val intensityKey: String, val effect: Effect) {
        Dialpad(PreferenceManager.KEY_VIBRATION_INTENSITY_DIALPAD, Effect.Click),
        Answer(PreferenceManager.KEY_VIBRATION_INTENSITY_ANSWER, Effect.HeavyClick),
        Hangup(PreferenceManager.KEY_VIBRATION_INTENSITY_HANGUP, Effect.DoubleClick),
        Scroll(PreferenceManager.KEY_VIBRATION_INTENSITY_SCROLL, Effect.Tick)
    }

    enum class Effect(val predefined: Int, val primitive: Int, val oneShotMs: Long, val repeats: Int) {
        Tick(VibrationEffect.EFFECT_TICK, VibrationEffect.Composition.PRIMITIVE_TICK, 10, 1),
        Click(VibrationEffect.EFFECT_CLICK, VibrationEffect.Composition.PRIMITIVE_CLICK, 20, 1),
        HeavyClick(VibrationEffect.EFFECT_HEAVY_CLICK, VibrationEffect.Composition.PRIMITIVE_CLICK, 35, 1),
        DoubleClick(VibrationEffect.EFFECT_DOUBLE_CLICK, VibrationEffect.Composition.PRIMITIVE_CLICK, 20, 2)
    }

    /** The strength set for [vibration], in percent of its full strength. */
    fun intensityOf(prefs: PreferenceManager, vibration: Vibration): Int =
        prefs.getInt(vibration.intensityKey, FULL_INTENSITY)

    /** Plays [vibration] at the strength set for it. */
    fun play(context: Context, vibration: Vibration, prefs: PreferenceManager) =
        play(context, vibration, intensityOf(prefs, vibration))

    /** Plays [vibration] at [intensity], in percent of its full strength. */
    fun play(context: Context, vibration: Vibration, intensity: Int) {
        val effect = vibration.effect
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val vibration = vibrationFor(vibrator, effect, intensity.coerceIn(1, FULL_INTENSITY) / 100f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(vibration, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(vibration)
        }
    }

    /**
     * At full strength the phone's own predefined effect. Below, the closest thing the vibrator can
     * scale: a composed primitive, else a short pulse of lower amplitude, else the predefined effect
     * as is, for a vibrator that offers no way to vary its strength.
     */
    private fun vibrationFor(vibrator: Vibrator, effect: Effect, scale: Float): VibrationEffect = when {
        scale >= 1f -> VibrationEffect.createPredefined(effect.predefined)
        vibrator.areAllPrimitivesSupported(effect.primitive) ->
            VibrationEffect.startComposition().apply {
                repeat(effect.repeats) { index ->
                    addPrimitive(effect.primitive, scale, if (index == 0) 0 else DOUBLE_GAP_MS)
                }
            }.compose()
        vibrator.hasAmplitudeControl() -> {
            val amplitude = (scale * 255).toInt().coerceIn(1, 255)
            if (effect.repeats == 1) {
                VibrationEffect.createOneShot(effect.oneShotMs, amplitude)
            } else {
                VibrationEffect.createWaveform(
                    longArrayOf(0, effect.oneShotMs, DOUBLE_GAP_MS.toLong(), effect.oneShotMs),
                    intArrayOf(0, amplitude, 0, amplitude),
                    -1
                )
            }
        }
        else -> VibrationEffect.createPredefined(effect.predefined)
    }

    private const val DOUBLE_GAP_MS = 100
}
