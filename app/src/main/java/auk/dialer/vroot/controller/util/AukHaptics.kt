package auk.dialer.vroot.controller.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * The vibrations the app's own settings turn on. They go to the vibrator directly: the feedback
 * constants of performHapticFeedback (text handle move, confirm, reject) are left unplayed on many
 * phones, so a setting the user had switched on did nothing at all. Predefined effects are played
 * by every vibrator, and still follow the phone's touch vibration strength.
 */
object AukHaptics {
    enum class Effect(val id: Int) {
        Tick(VibrationEffect.EFFECT_TICK),
        Click(VibrationEffect.EFFECT_CLICK),
        HeavyClick(VibrationEffect.EFFECT_HEAVY_CLICK),
        DoubleClick(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }

    fun play(context: Context, effect: Effect) {
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val vibration = VibrationEffect.createPredefined(effect.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(vibration, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(vibration)
        }
    }
}
