package auk.dialer.vroot.view.theme

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

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
 * What shows behind the status bar once a collapsing header has left it, set by
 * [rememberStatusBarSampler] and read by [AukTheme]. Null while a header covers the bar.
 */
val LocalStatusBarBackground: ProvidableCompositionLocal<MutableState<Color?>> =
    staticCompositionLocalOf { mutableStateOf(null) }

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
 * Keeps both system bars fully transparent, with icons that stay readable on what is behind them.
 *
 * The status bar icons follow, in order: the content a collapsed header uncovered, the background of
 * a screen that declared [AukPlainStatusBar], or the accent header. The navigation bar sits on the
 * accent band the theme paints while edge to edge is off, and on the app itself once it is on.
 *
 * The bar styles are explicit rather than auto(), which keeps the system from laying its own
 * translucent scrim over three button navigation.
 *
 * Its own composable so a screen changing the status bar recomposes this alone, not the theme and
 * everything under it.
 */
@Composable
internal fun AukSystemBars(
    accentIsDark: Boolean,
    backgroundIsDark: Boolean,
    darkTheme: Boolean,
    edgeToEdge: Boolean,
    plainStatusBar: State<Boolean>,
    statusBarBackground: State<Color?>
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>()
            .firstOrNull()
    } ?: return

    val isStatusBarDark = statusBarBackground.value?.let { it.luminance() < 0.5f }
        ?: if (plainStatusBar.value) backgroundIsDark else accentIsDark
    val isNavigationBarDark = if (edgeToEdge) darkTheme else accentIsDark
    DisposableEffect(activity, isStatusBarDark, isNavigationBarDark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) {
                isStatusBarDark
            },
            navigationBarStyle = if (isNavigationBarDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
        )
        onDispose {}
    }
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
 * A Scaffold body's padding without its bottom, for outside a scrolling container. The bottom goes
 * inside it instead, through [aukScrollContentPadding], so the content scrolls on behind the
 * navigation bar and its last item still ends up clear of it.
 */
@Composable
fun PaddingValues.aukExceptBottom(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection)
    )
}

/** [all] around the content of a scrolling container, plus the bottom of the Scaffold [bodyPadding]. */
fun aukScrollContentPadding(all: Dp, bodyPadding: PaddingValues): PaddingValues =
    PaddingValues(start = all, top = all, end = all, bottom = all + bodyPadding.calculateBottomPadding())

/** Records what a screen draws behind the status bar, for [rememberStatusBarSampler]. */
@Stable
class StatusBarSampler internal constructor(internal val layer: GraphicsLayer) {
    internal var isSampling = false
    internal var statusBarHeight = 0

    // Counts the recordings, so that the same content isn't sampled twice.
    internal var version = 0
}

/**
 * Keeps the status bar icons readable once a collapsing header has slid away, with the bar itself
 * left fully transparent.
 *
 * While the header still covers most of the bar there is nothing to do. Past that, what the screen
 * draws behind the bar is sampled a few times a second, and the icons turn white over dark content
 * and black over light content. The screen records that content with [aukSampleStatusBarContent].
 */
@Composable
fun rememberStatusBarSampler(scrollBehavior: TopAppBarScrollBehavior, enabled: Boolean): StatusBarSampler {
    val layer = rememberGraphicsLayer()
    val sampler = remember(layer) { StatusBarSampler(layer) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    SideEffect { sampler.statusBarHeight = statusBarHeight }
    // Where the content leaves the background showing, it draws nothing.
    val backgroundLuminance by rememberUpdatedState(MaterialTheme.colorScheme.background.luminance())
    val background = MaterialTheme.colorScheme.background
    val statusBarBackground = LocalStatusBarBackground.current
    val isHeaderBehindStatusBar by remember(scrollBehavior, statusBarHeight) {
        derivedStateOf {
            val headerHeight = -scrollBehavior.state.heightOffsetLimit
            headerHeight + scrollBehavior.state.heightOffset >= statusBarHeight / 2f
        }
    }
    val isSampling = enabled && !isHeaderBehindStatusBar

    var contentColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(sampler, isSampling) {
        if (!isSampling) {
            contentColor = null
            return@LaunchedEffect
        }
        sampler.isSampling = true
        var sampledVersion = -1
        try {
            while (true) {
                // Lets the content be drawn and recorded.
                withFrameNanos {}
                val version = sampler.version
                if (version != sampledVersion && sampler.layer.size != IntSize.Zero) {
                    sampledVersion = version
                    val luminance = sampler.layer.toImageBitmap().averageLuminance(backgroundLuminance)
                    val isDark = when {
                        luminance < DARK_LUMINANCE_MAX -> true
                        luminance > LIGHT_LUMINANCE_MIN -> false
                        // In between, the icons stay as they are, so that they don't flicker.
                        else -> contentColor?.let { it == Color.Black } ?: (luminance < 0.5f)
                    }
                    contentColor = if (isDark) Color.Black else Color.White
                }
                delay(SAMPLING_INTERVAL_MILLIS)
            }
        } finally {
            sampler.isSampling = false
        }
    }

    val shownBackground = if (isSampling) contentColor ?: background else null
    DisposableEffect(statusBarBackground, shownBackground) {
        statusBarBackground.value = shownBackground
        onDispose { statusBarBackground.value = null }
    }
    return sampler
}

/**
 * Records what this content draws behind the status bar for [sampler]. The content must start at
 * the top of the screen.
 */
fun Modifier.aukSampleStatusBarContent(sampler: StatusBarSampler): Modifier =
    drawWithContent {
        drawContent()
        if (sampler.isSampling && sampler.statusBarHeight > 0) {
            sampler.layer.record(size = IntSize(size.width.roundToInt(), sampler.statusBarHeight)) {
                this@drawWithContent.drawContent()
            }
            ++sampler.version
        }
    }

private const val SAMPLING_INTERVAL_MILLIS = 200L
private const val DARK_LUMINANCE_MAX = 0.4f
private const val LIGHT_LUMINANCE_MIN = 0.6f

// Every few pixels are enough to tell how dark the content is.
private const val SAMPLING_STEP = 8

private fun ImageBitmap.averageLuminance(backgroundLuminance: Float): Float {
    val bitmap = asAndroidBitmap().let {
        // The pixels of a hardware bitmap can't be read.
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }
    var luminanceSum = 0f
    var count = 0
    for (y in 0 until bitmap.height step SAMPLING_STEP) {
        for (x in 0 until bitmap.width step SAMPLING_STEP) {
            val color = Color(bitmap.getPixel(x, y))
            luminanceSum += color.luminance() * color.alpha + backgroundLuminance * (1 - color.alpha)
            ++count
        }
    }
    return if (count > 0) luminanceSum / count else 1f
}
