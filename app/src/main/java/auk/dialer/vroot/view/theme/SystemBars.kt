package auk.dialer.vroot.view.theme

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * The colour every app bar takes, and the colour its title and icons need to stay readable on it.
 *
 * Provided by [AukTheme], which keeps the shade identical in light and dark mode: Material lightens
 * primary in dark mode, and a header that changed colour with the theme would no longer match the
 * system bars sitting against it.
 */
val LocalAccentBarColor: ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf { Color(AUK_BRAND_SEED) }

val LocalOnAccentBarColor: ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf { Color.White }

/** Whether the app draws behind the system bars, from the "Edge to edge" setting. */
val LocalEdgeToEdge: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Opacity of the status bar scrim, which keeps the bar readable once a collapsing header has slid
 * away and app content shows behind it. The screen drives it from its scroll, [AukTheme] draws it.
 */
val LocalStatusBarScrimAlpha: ProvidableCompositionLocal<MutableFloatState> =
    staticCompositionLocalOf { mutableFloatStateOf(0f) }

/** Set while a screen with no accent header is up. Read by [AukTheme], declared by [AukPlainStatusBar]. */
val LocalPlainStatusBar: ProvidableCompositionLocal<MutableState<Boolean>> =
    staticCompositionLocalOf { mutableStateOf(false) }

/**
 * Declares that this screen shows its own background behind the status bar rather than an accent
 * header, so its icons have to contrast with that background instead.
 */
@Composable
fun AukPlainStatusBar() {
    val plain = LocalPlainStatusBar.current
    DisposableEffect(plain) {
        plain.value = true
        onDispose { plain.value = false }
    }
}

/**
 * Keeps the system bar icons readable.
 *
 * The status bar normally sits behind an accent header, so its icons contrast with the accent; on a
 * screen that declared [AukPlainStatusBar] they contrast with the app background instead. The
 * navigation bar is an opaque accent band while edge to edge is off, and transparent over the app
 * background once it is on.
 *
 * Its own composable so a screen changing the status bar recomposes this alone, not the theme and
 * everything under it.
 */
@Composable
internal fun AukSystemBarIcons(
    accentIsDark: Boolean,
    backgroundIsLight: Boolean,
    darkTheme: Boolean,
    edgeToEdge: Boolean,
    plainStatusBar: State<Boolean>
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val lightStatusBarIcons = if (plainStatusBar.value) backgroundIsLight else !accentIsDark
    SideEffect {
        val controller = view.aukInsetsController() ?: return@SideEffect
        controller.isAppearanceLightStatusBars = lightStatusBarIcons
        controller.isAppearanceLightNavigationBars = if (edgeToEdge) !darkTheme else !accentIsDark
    }
}

/** The window controller for this view, found without ever casting a wrapped OEM context blindly. */
private fun View.aukInsetsController(): WindowInsetsControllerCompat? {
    val window = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
        ?.window ?: return null
    return WindowCompat.getInsetsController(window, this)
}

@Composable
fun aukAccentTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = LocalAccentBarColor.current,
    scrolledContainerColor = LocalAccentBarColor.current,
    navigationIconContentColor = LocalOnAccentBarColor.current,
    titleContentColor = LocalOnAccentBarColor.current,
    actionIconContentColor = LocalOnAccentBarColor.current
)

/**
 * Collapses the header off the top of the screen as the body scrolls.
 *
 * It reports a height that shrinks with the scroll offset, so the Scaffold slides the body up into
 * the space freed, and moves itself by the same amount, so the header leaves and the content reaches
 * the status bar together.
 */
fun Modifier.aukCollapsingHeader(scrollBehavior: TopAppBarScrollBehavior): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
        val offsetY = scrollBehavior.state.heightOffset.roundToInt()
        layout(placeable.width, (placeable.height + offsetY).coerceAtLeast(0)) {
            placeable.place(0, offsetY)
        }
    }

/**
 * Follows a collapsing header to keep the status bar readable.
 *
 * While the accent header still covers the bar there is nothing to do. As the header slides away and
 * app content takes the bar over, the scrim fades in and the bar icons switch to contrast with that
 * content instead of with the accent.
 */
@Composable
fun AukStatusBarScrimEffect(scrollBehavior: TopAppBarScrollBehavior, enabled: Boolean) {
    val view = LocalView.current
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val backgroundIsLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentIsDark = LocalAccentBarColor.current.luminance() <= 0.5f
    val scrimAlpha = LocalStatusBarScrimAlpha.current
    if (!enabled || view.isInEditMode) return

    LaunchedEffect(view, statusBarPx, backgroundIsLight, accentIsDark, scrimAlpha) {
        val controller = view.aukInsetsController() ?: return@LaunchedEffect
        try {
            snapshotFlow {
                val headerHeightPx = -scrollBehavior.state.heightOffsetLimit
                val headerBottomPx = scrollBehavior.state.heightOffset + headerHeightPx
                // How much of the status bar shows app content rather than the header, 0 to 1.
                if (headerHeightPx <= 0f || statusBarPx <= 0) {
                    0f
                } else {
                    ((statusBarPx - headerBottomPx) / statusBarPx).coerceIn(0f, 1f)
                }
            }.distinctUntilChanged().collect { contentFraction ->
                scrimAlpha.floatValue = contentFraction
                controller.isAppearanceLightStatusBars = contentFraction > 0.5f && backgroundIsLight
            }
        } finally {
            scrimAlpha.floatValue = 0f
            controller.isAppearanceLightStatusBars = !accentIsDark
        }
    }
}
