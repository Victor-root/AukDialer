package auk.dialer.vroot.view.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukAuroraBackground
import org.koin.compose.koinInject

const val KEY_CUSTOM_PRIMARY_COLOR: String = "custom_primary_color"
const val CUSTOM_PRIMARY_COLOR_UNSET: Int = -1

private const val StatusBarScrimLightAlpha = 0.14f
private const val StatusBarScrimDarkAlpha = 0.10f

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
    val customPrimaryInt = remember(settingsVersion) {
        prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, CUSTOM_PRIMARY_COLOR_UNSET)
    }
    val cardRoundness = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, AukShapeDefaults.DefaultRoundness)
    }
    val edgeToEdge = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_EDGE_TO_EDGE, false)
    }

    val colorScheme = remember(darkTheme, dynamicColor, amoledMode, customPrimaryInt) {
        val base = when {
            dynamicColor ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            customPrimaryInt != CUSTOM_PRIMARY_COLOR_UNSET ->
                aukColorSchemeFromSeed(customPrimaryInt, darkTheme)
            darkTheme -> AukDarkColorScheme
            else -> AukLightColorScheme
        }
        base.withNeutralSurfaces(darkTheme, amoledMode)
    }

    val shapes = remember(cardRoundness) { aukShapes(cardRoundness) }

    val callColors = remember(colorScheme, darkTheme) { aukCallColors(colorScheme, darkTheme) }

    // One shade for the app bars and the system bars in both themes. Material lightens primary in
    // dark mode, but inversePrimary there is the light mode primary, so this stays the same colour.
    val barColor = if (darkTheme) colorScheme.inversePrimary else colorScheme.primary
    val onBarColor = if (barColor.luminance() > 0.5f) Color.Black else Color.White

    val statusBarScrimAlpha = remember { mutableFloatStateOf(0f) }
    val plainStatusBar = remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalCallColors provides callColors,
        LocalCardRoundness provides cardRoundness,
        LocalAccentBarColor provides barColor,
        LocalOnAccentBarColor provides onBarColor,
        LocalEdgeToEdge provides edgeToEdge,
        LocalStatusBarScrimAlpha provides statusBarScrimAlpha,
        LocalPlainStatusBar provides plainStatusBar
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = shapes,
            typography = AukTypography
        ) {
            if (systemBars) {
                AukSystemBarIcons(
                    accentIsDark = barColor.luminance() <= 0.5f,
                    backgroundIsLight = colorScheme.background.luminance() > 0.5f,
                    darkTheme = darkTheme,
                    edgeToEdge = edgeToEdge,
                    plainStatusBar = plainStatusBar
                )
            }
            if (!systemBars) {
                content()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // The page itself, under the screens, which are transparent so it shows
                    // through: they carry no accent of their own any more, the colour lives here.
                    AukAuroraBackground()
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
                    // Edge to edge on: a faint scrim keeps the status bar readable once a
                    // collapsing header has slid away and content sits behind it. Invisible while
                    // the accent header still covers the bar, so the header is never tinted.
                    if (edgeToEdge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                                .graphicsLayer { alpha = statusBarScrimAlpha.floatValue }
                                .background(
                                    if (darkTheme) Color.White.copy(alpha = StatusBarScrimDarkAlpha)
                                    else Color.Black.copy(alpha = StatusBarScrimLightAlpha)
                                )
                        )
                    }
                }
            }
        }
    }
}
