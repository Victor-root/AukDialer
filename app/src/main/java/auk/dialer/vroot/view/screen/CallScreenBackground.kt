package auk.dialer.vroot.view.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.AukDurations

private const val TopScrimFraction = 0.36f
private const val BottomScrimFraction = 0.54f

// The gradient style: the accent fades out over the top half, behind the caller's name.
private const val GradientFadeStart = 0.22f
private const val GradientFadeEnd = 0.55f

// Where the caller's tile sits on the call screen, for the rings to start from.
private const val RingsCenterFraction = 0.28f
private const val RingCount = 4
private const val BubbleCount = 18

/** One loop of [periodMillis], linear and endless: the clock every animated background runs on. */
@Composable
private fun rememberLoop(label: String, periodMillis: Int): State<Float> =
    rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing)),
        label = label
    )

private const val TwoPi = (2 * PI).toFloat()

/**
 * The animated style: three wide halos of the theme's colours drifting across the whole screen.
 * Each follows a closed curve, whole turns per loop, so the drift never jumps.
 */
@Composable
private fun AmbientBlobs() {
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primaryContainer, scheme.tertiaryContainer, LocalAccentBarColor.current)
    val loop by rememberLoop("halos", 48000)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.maxDimension * 0.55f
        colors.forEachIndexed { index, color ->
            val angle = loop * TwoPi + index * TwoPi / 3f
            val center = Offset(
                size.width * (0.5f + 0.38f * sin(angle)),
                size.height * (0.5f + 0.36f * sin(2f * angle + index))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.42f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}

/** Waves stacked from the top of the screen to the bottom, each rolling slowly along. */
@Composable
private fun Waves() {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val colors = listOf(scheme.tertiaryContainer, scheme.primaryContainer, accent, scheme.tertiaryContainer, accent)
    val loop by rememberLoop("waves", 36000)
    Canvas(modifier = Modifier.fillMaxSize()) {
        colors.forEachIndexed { index, color ->
            val baseline = size.height * (0.12f + index * 0.19f)
            val amplitude = size.height * 0.035f
            // Each wave runs a whole number of times per loop, so it never jumps.
            val phase = loop * TwoPi * (if (index % 2 == 0) 1 else -1) + index
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width + 8f) {
                    val wave = sin(x / size.width * TwoPi * (0.8f + index * 0.15f) + phase)
                    lineTo(x, baseline + amplitude * wave)
                    x += 8f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color.copy(alpha = 0.13f))
        }
    }
}

/** Rings spreading from behind the caller's tile out past the edges of the screen. */
@Composable
private fun Rings() {
    val accent = LocalAccentBarColor.current
    val tint = MaterialTheme.colorScheme.tertiaryContainer
    val loop by rememberLoop("rings", 14000)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * RingsCenterFraction)
        // Far enough to clear the furthest corner.
        val maxRadius = sqrt(size.width * size.width + size.height * size.height)
        repeat(RingCount) { index ->
            val step = (loop + index.toFloat() / RingCount) % 1f
            val fade = 1f - step
            drawCircle(
                color = tint.copy(alpha = 0.22f * fade),
                radius = maxRadius * step,
                center = center
            )
            drawCircle(
                color = accent.copy(alpha = 0.40f * fade),
                radius = maxRadius * step,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}

/** Large bubbles rising slowly up the whole screen. */
@Composable
private fun Bubbles() {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val colors = listOf(scheme.primaryContainer, scheme.tertiaryContainer, accent)
    val bubbles = remember {
        val random = java.util.Random(7)
        List(BubbleCount) {
            Bubble(
                x = random.nextFloat(),
                radius = 24f + random.nextFloat() * 56f,
                // Whole laps per loop, so every bubble wraps without a jump.
                laps = 1 + random.nextInt(2),
                start = random.nextFloat(),
                colorIndex = random.nextInt(3)
            )
        }
    }
    val loop by rememberLoop("bubbles", 70000)
    Canvas(modifier = Modifier.fillMaxSize()) {
        bubbles.forEach { bubble ->
            val radius = bubble.radius.dp.toPx()
            val travel = size.height + radius * 2f
            val rise = (loop * bubble.laps + bubble.start) % 1f
            val center = Offset(
                bubble.x * size.width + radius * 0.4f * sin(rise * TwoPi * 2f),
                size.height + radius - rise * travel
            )
            drawCircle(color = colors[bubble.colorIndex].copy(alpha = 0.26f), radius = radius, center = center)
        }
    }
}

private class Bubble(val x: Float, val radius: Float, val laps: Int, val start: Float, val colorIndex: Int)

/** Broad diagonal bands of the theme's colours sliding slowly across the whole screen. */
@Composable
private fun Aurora() {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val colors = listOf(accent, scheme.tertiaryContainer, scheme.primaryContainer, scheme.tertiary, accent)
    val loop by rememberLoop("aurora", 45000)
    Canvas(modifier = Modifier.fillMaxSize()) {
        // The gradient repeats every period and slides by exactly one, so the loop is seamless.
        val period = size.maxDimension * 1.6f
        val shift = loop * period
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(-shift, -shift * 0.6f),
                end = Offset(period - shift, (period - shift) * 0.6f),
                tileMode = TileMode.Repeated
            ),
            alpha = 0.32f
        )
    }
}

@Composable
fun ExpressiveBackground(photoUri: String?, backgroundUri: String? = null, style: Int = PreferenceManager.CALL_BACKGROUND_ANIMATED) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface

    val hasCustomBackground = !backgroundUri.isNullOrEmpty()
    var customFailed by remember(backgroundUri) { mutableStateOf(false) }

    val customVisible = hasCustomBackground && !customFailed
    val hasPhoto = !photoUri.isNullOrEmpty()
    // The photo style falls back on the animated one for a caller with no photo.
    val shownStyle = if (style == PreferenceManager.CALL_BACKGROUND_PHOTO && !hasPhoto) {
        PreferenceManager.CALL_BACKGROUND_ANIMATED
    } else {
        style
    }
    val photoVisible = hasPhoto && !customVisible &&
        (style == PreferenceManager.CALL_BACKGROUND_ANIMATED || style == PreferenceManager.CALL_BACKGROUND_PHOTO)

    val request = remember(backgroundUri, context) {
        if (backgroundUri.isNullOrEmpty()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(backgroundUri)
                .crossfade(AukDurations.Long1)
                .build()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(surface)) {
        if (!customVisible) {
            when (shownStyle) {
                PreferenceManager.CALL_BACKGROUND_ANIMATED -> AmbientBlobs()
                PreferenceManager.CALL_BACKGROUND_WAVES -> Waves()
                PreferenceManager.CALL_BACKGROUND_RINGS -> Rings()
                PreferenceManager.CALL_BACKGROUND_BUBBLES -> Bubbles()
                PreferenceManager.CALL_BACKGROUND_AURORA -> Aurora()
            }
        }

        if (!customVisible && style == PreferenceManager.CALL_BACKGROUND_GRADIENT) {
            val accent = LocalAccentBarColor.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to accent,
                            GradientFadeStart to accent.copy(alpha = 0.6f),
                            GradientFadeEnd to accent.copy(alpha = 0f)
                        )
                    )
            )
        }

        if (photoVisible) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(56.dp),
                contentScale = ContentScale.Crop,
                // The photo style shows it in full, the animated one as a hint behind the blobs.
                alpha = if (style == PreferenceManager.CALL_BACKGROUND_PHOTO) 1f else 0.42f
            )
        }

        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { customFailed = false },
                onError = { customFailed = true },
                contentScale = ContentScale.Crop
            )
        }

        if (customVisible || photoVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(TopScrimFraction)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.88f),
                            0.5f to surface.copy(alpha = 0.60f),
                            1f to surface.copy(alpha = 0f)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(BottomScrimFraction)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0f),
                            0.34f to surface.copy(alpha = 0.42f),
                            0.72f to surface.copy(alpha = 0.88f),
                            1f to surface.copy(alpha = 0.88f)
                        )
                    )
            )
        }
    }
}
