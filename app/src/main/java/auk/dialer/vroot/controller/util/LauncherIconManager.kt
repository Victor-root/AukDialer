package auk.dialer.vroot.controller.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swaps the launcher icon by enabling one of the colour activity-alias entries
 * declared in the manifest and disabling the rest.
 */
class LauncherIconManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * The colour the launcher is actually showing, asked of the package manager
     * rather than read from the preference.
     *
     * The preference records what was picked; only the enabled alias says what
     * is on screen. The two drift when a restored backup rewrites the
     * preference on an install whose aliases it cannot touch, and trusting the
     * preference then reports "nothing to do" while the old icon is still up.
     */
    fun currentColor(): Int {
        val pm = context.packageManager
        return ICONS.firstOrNull { (_, suffix) ->
            when (runCatching { pm.getComponentEnabledSetting(aliasComponent(suffix)) }.getOrNull()) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                // Only the manifest default is enabled without ever being toggled.
                else -> suffix == DEFAULT_SUFFIX
            }
        }?.first ?: DEFAULT_COLOR
    }

    fun isChangeNeeded(color: Int): Boolean = nearestColor(color) != currentColor()

    /**
     * Enables the alias closest to [color], then calls [onApplied] once the
     * launcher has actually redrawn.
     *
     * The timing is the whole point. Enabling an alias fires an
     * ACTION_PACKAGE_CHANGED broadcast that the launcher repaints from. Closing
     * the app the moment the call returns reveals the home screen mid-repaint
     * and the icon is seen morphing. So the still-visible app waits for that
     * same broadcast to come back to it, plus a short settle, and only then
     * lets the caller close.
     *
     * The caller must close with finishAffinity(). The task was started from an
     * alias that is now disabled, so its base intent no longer resolves and the
     * next launch would fail to restore the task.
     */
    fun apply(color: Int, onApplied: () -> Unit) {
        val target = nearestColor(color)
        val targetSuffix = ICONS.first { it.first == target }.second
        val pm = context.packageManager

        val handler = Handler(Looper.getMainLooper())
        var done = false
        var receiver: BroadcastReceiver? = null

        fun finish() {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
            onApplied()
        }

        // Registered before the swap so the broadcast cannot be missed.
        val packageWatcher = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.data?.encodedSchemeSpecificPart == context.packageName) {
                    handler.postDelayed({ finish() }, SETTLE_MS)
                }
            }
        }
        receiver = packageWatcher
        val filter = IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") }
        context.registerReceiver(packageWatcher, filter, Context.RECEIVER_NOT_EXPORTED)

        // Off the main thread: rewriting twenty component states is one binder
        // round trip that persists them and fires the broadcast, and the system
        // re-checks the default-dialer role on top of it.
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // One call, one broadcast, so the launcher refreshes almost at once.
                val settings = ICONS.map { (_, suffix) ->
                    PackageManager.ComponentEnabledSetting(
                        aliasComponent(suffix),
                        if (suffix == targetSuffix) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
                runCatching { pm.setComponentEnabledSettings(settings) }
            } else {
                // Enable first, so the app is never absent from the launcher.
                setEnabled(pm, targetSuffix, true)
                ICONS.filter { it.second != targetSuffix }.forEach { (_, suffix) ->
                    setEnabled(pm, suffix, false)
                }
            }
            preferenceManager.setInt(PreferenceManager.KEY_APP_ICON_COLOR, target)
        }

        // Safety net, so the app always closes even if the broadcast never lands.
        handler.postDelayed({ finish() }, TIMEOUT_MS)
    }

    private fun aliasComponent(suffix: String) =
        ComponentName(context.packageName, "$COMPONENT_PACKAGE.LauncherAlias_$suffix")

    private fun setEnabled(pm: PackageManager, suffix: String, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            pm.setComponentEnabledSetting(aliasComponent(suffix), state, PackageManager.DONT_KILL_APP)
        }
    }

    companion object {
        /**
         * Alias class names are relative to the app's namespace, which does not
         * follow the application id. Building the component name from
         * context.packageName would point at a class that does not exist and
         * the enable call would silently do nothing.
         */
        private const val COMPONENT_PACKAGE = "auk.dialer.vroot"

        /** The one alias the manifest ships enabled. */
        private const val DEFAULT_SUFFIX = "Violet"

        /** Time for the launcher to finish drawing behind the still-visible app. */
        private const val SETTLE_MS = 300L

        /** Hard cap on waiting for the broadcast, so the app always closes. */
        private const val TIMEOUT_MS = 1500L

        val ICONS: List<Pair<Int, String>> = listOf(
            0xFF7B2DDC.toInt() to "Violet",
            0xFFD32F2F.toInt() to "Red",
            0xFFE91E63.toInt() to "Pink",
            0xFF9C27B0.toInt() to "Purple",
            0xFF673AB7.toInt() to "DeepPurple",
            0xFF3F51B5.toInt() to "Indigo",
            0xFF2196F3.toInt() to "Blue",
            0xFF03A9F4.toInt() to "LightBlue",
            0xFF00BCD4.toInt() to "Cyan",
            0xFF009688.toInt() to "Teal",
            0xFF4CAF50.toInt() to "Green",
            0xFF8BC34A.toInt() to "LightGreen",
            0xFFCDDC39.toInt() to "Lime",
            0xFFE6C400.toInt() to "Yellow",
            0xFFFFC107.toInt() to "Amber",
            0xFFFF9800.toInt() to "Orange",
            0xFFFF5722.toInt() to "DeepOrange",
            0xFF795548.toInt() to "Brown",
            0xFF9E9E9E.toInt() to "Gray",
            0xFF607D8B.toInt() to "BlueGray",
        )

        val DEFAULT_COLOR: Int = ICONS.first { it.second == DEFAULT_SUFFIX }.first

        /** The available colour perceptually closest to [color]. */
        fun nearestColor(color: Int): Int {
            val target = FloatArray(3)
            Color.colorToHSV(color, target)
            return ICONS.minByOrNull { (candidate, _) ->
                val c = FloatArray(3)
                Color.colorToHSV(candidate, c)
                val hue = abs(c[0] - target[0])
                minOf(hue, 360f - hue) +
                    abs(c[1] - target[1]) * 100f +
                    abs(c[2] - target[2]) * 100f
            }?.first ?: DEFAULT_COLOR
        }
    }
}
