package auk.dialer.vroot.view.theme

import android.app.WallpaperManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.LocalAukAvatarStyle
import auk.dialer.vroot.view.components.LocalAukSurfaceStyle
import auk.dialer.vroot.view.components.rememberAukAvatarStyle
import auk.dialer.vroot.view.components.rememberAukSurfaceStyle
import org.koin.compose.koinInject
import kotlin.math.abs

const val KEY_CUSTOM_PRIMARY_COLOR: String = "custom_primary_color"
const val CUSTOM_PRIMARY_COLOR_UNSET: Int = -1

// A hue gap this wide between the platform's dynamic scheme and the wallpaper's own
// dominant colour is past what harmonisation/tone mapping would ever produce from the
// same source, so it means the two disagree on what the wallpaper even is.
private const val OemHueMismatchDegrees = 60f
private const val OemColorSaturationFloor = 0.15f

/**
 * Some OEM skins (seen on ColorOS/OPPO) populate the system dynamic-colour resources
 * dynamicLightColorScheme/dynamicDarkColorScheme read from with a colour that has
 * stopped tracking the wallpaper entirely. WallpaperColors is the lower-level API
 * stock Android's own SystemUI derives those resources from in the first place, so
 * comparing against it catches the mismatch: close enough, and every correctly
 * behaving device already lands here, so the platform's own scheme is kept as is;
 * way off, and the scheme is rebuilt from the wallpaper's actual colour instead, with
 * the same seed-based generator as the manual picker.
 */
fun aukDynamicColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
    val platformScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val wallpaperSeed = wallpaperSeedColor(context) ?: return platformScheme

    val platformHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(platformScheme.primary.toArgb(), platformHsv)
    val wallpaperHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(wallpaperSeed, wallpaperHsv)

    val hueGap = abs(platformHsv[0] - wallpaperHsv[0]).let { minOf(it, 360f - it) }
    val bothSaturated = platformHsv[1] > OemColorSaturationFloor && wallpaperHsv[1] > OemColorSaturationFloor

    return if (bothSaturated && hueGap > OemHueMismatchDegrees) {
        aukColorSchemeFromSeed(wallpaperSeed, darkTheme)
    } else {
        platformScheme
    }
}

/**
 * Where ColorOS keeps the wallpaper's colour, as "<ARGB int>&…&…". Its glass wallpaper effect is a
 * live wallpaper reporting no WallpaperColors at all, while the platform palette it leaves behind
 * stays on a stale blue: this record is then the only trace of the real colour.
 */
private const val ColorOsWallpaperSeedKey = "wall_paper_seed_color"

/** The wallpaper's dominant colour, from WallpaperColors or else from ColorOS's own record of it. */
private fun wallpaperSeedColor(context: Context): Int? =
    runCatching {
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    }.getOrNull() ?: runCatching {
        Settings.System.getString(context.contentResolver, ColorOsWallpaperSeedKey)
            ?.substringBefore('&')
            ?.toIntOrNull()
    }.getOrNull()

/**
 * Counts the wallpaper colour changes while [enabled], so a scheme keyed on it is worked out again.
 * ColorOS's record changes without the platform palette doing so, which would otherwise recreate
 * the activity, hence the observer on it next to the usual listener.
 */
@Composable
private fun rememberWallpaperColorsVersion(context: Context, enabled: Boolean): Int {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val handler = Handler(Looper.getMainLooper())
        val wallpaperManager = WallpaperManager.getInstance(context)
        val colorsListener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) version++
        }
        val seedObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        runCatching { wallpaperManager.addOnColorsChangedListener(colorsListener, handler) }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(ColorOsWallpaperSeedKey),
            false,
            seedObserver
        )
        onDispose {
            runCatching { wallpaperManager.removeOnColorsChangedListener(colorsListener) }
            context.contentResolver.unregisterContentObserver(seedObserver)
        }
    }
    return version
}

@Composable
fun AukTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    prefs: PreferenceManager = koinInject(),
    systemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val settingsVersion by prefs.settingsChanged.collectAsState()
    val context = LocalContext.current

    val dynamicColor = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, true)
    }
    val amoledMode = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_AMOLED_MODE, false)
    }
    val vividColors = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_VIVID_COLORS, true)
    }
    val customPrimaryInt = remember(settingsVersion) {
        prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, CUSTOM_PRIMARY_COLOR_UNSET)
    }
    val cardRoundness = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, AukShapeDefaults.DefaultRoundness)
    }
    val edgeToEdge by prefs.edgeToEdge.collectAsState()

    val wallpaperColorsVersion = rememberWallpaperColorsVersion(context, enabled = dynamicColor)

    val baseScheme = remember(darkTheme, dynamicColor, amoledMode, customPrimaryInt, wallpaperColorsVersion) {
        val base = when {
            dynamicColor -> aukDynamicColorScheme(context, darkTheme)
            customPrimaryInt != CUSTOM_PRIMARY_COLOR_UNSET ->
                aukColorSchemeFromSeed(customPrimaryInt, darkTheme)
            darkTheme -> AukDarkColorScheme
            else -> AukLightColorScheme
        }
        base.withNeutralSurfaces(darkTheme, amoledMode)
    }
    val colorScheme = remember(baseScheme, vividColors) {
        if (vividColors) baseScheme.withVividContainers(darkTheme) else baseScheme
    }
    val softAccentColors = remember(baseScheme) { baseScheme.softAccentColors() }

    val shapes = remember(cardRoundness) { aukShapes(cardRoundness) }

    val callColors = remember(colorScheme, darkTheme) { aukCallColors(colorScheme, darkTheme) }

    val barColor = colorScheme.accentBarColor(darkTheme)
    val onBarColor = contentColorOnAccent(barColor)

    val statusBarBackground = remember { mutableStateOf<Color?>(null) }
    val plainStatusBar = remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalCallColors provides callColors,
        LocalSoftAccentColors provides softAccentColors,
        LocalCardRoundness provides cardRoundness,
        LocalAccentBarColor provides barColor,
        LocalOnAccentBarColor provides onBarColor,
        LocalEdgeToEdge provides edgeToEdge,
        LocalStatusBarBackground provides statusBarBackground,
        LocalPlainStatusBar provides plainStatusBar
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = shapes,
            typography = AukTypography
        ) {
            CompositionLocalProvider(
                LocalAukAvatarStyle provides rememberAukAvatarStyle(prefs),
                LocalAukSurfaceStyle provides rememberAukSurfaceStyle(prefs)
            ) {
                if (systemBars) {
                    AukSystemBars(
                        accentIsDark = barColor.luminance() <= 0.5f,
                        backgroundIsDark = colorScheme.background.luminance() <= 0.5f,
                        darkTheme = darkTheme,
                        edgeToEdge = edgeToEdge,
                        plainStatusBar = plainStatusBar,
                        statusBarBackground = statusBarBackground
                    )
                }
                if (!systemBars) {
                    content()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                    ) {
                        content()
                        // Edge to edge off: an opaque accent band over the navigation bar area, so it
                        // reads as a solid bar matching the header. On: nothing, and the app shows
                        // through the transparent navigation bar.
                        if (!edgeToEdge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
