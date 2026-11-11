package auk.dialer.vroot.view.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
private const val RingCount = 3
private const val BubbleCount = 16
private const val AuroraHeightFraction = 0.55f

/** The animated style: two soft blobs of the theme's colours drifting behind the call. */
@Composable
private fun BoxScope.AmbientBlobs() {
    val scheme = MaterialTheme.colorScheme
    // Read only while placing and drawing the blobs, so their drift redraws them without
    // recomposing the whole background on every frame of the call.
    val infiniteTransition = rememberInfiniteTransition(label = "ambientBg")
    val blob1Offset = infiniteTransition.animateFloat(
        initialValue = -28f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val blob2Offset = infiniteTransition.animateFloat(
        initialValue = 28f,
        targetValue = -28f,
        animationSpec = infiniteRepeatable(tween(13000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val blobAlpha = infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ba"
    )
    Box(
        modifier = Modifier
            .offset { IntOffset(blob1Offset.value.dp.roundToPx(), (-80).dp.roundToPx()) }
            .size(280.dp)
            .align(Alignment.TopStart)
            .graphicsLayer { alpha = blobAlpha.value }
            .clip(CircleShape)
            .background(scheme.primaryContainer)
            .blur(72.dp)
    )
    Box(
        modifier = Modifier
            .offset { IntOffset(blob2Offset.value.dp.roundToPx(), 100.dp.roundToPx()) }
            .size(320.dp)
            .align(Alignment.BottomEnd)
            .graphicsLayer { alpha = blobAlpha.value }
            .clip(CircleShape)
            .background(scheme.tertiaryContainer)
            .blur(84.dp)
    )
}

/** Three soft waves rolling along the bottom of the screen, under the call controls. */
@Composable
private fun Waves() {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val colors = listOf(scheme.tertiaryContainer, scheme.primaryContainer, accent)
    val phase by rememberInfiniteTransition(label = "waves").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "wavePhase"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        colors.forEachIndexed { index, color ->
            val baseline = size.height * (0.70f + index * 0.08f)
            val amplitude = (14 + index * 6).dp.toPx()
            // Each wave runs a whole number of times per cycle, so the loop never jumps.
            val turns = index + 1
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    val angle = x / size.width * 2f * PI.toFloat() * (1.2f - index * 0.2f) + phase * turns + index
                    lineTo(x, baseline + amplitude * sin(angle))
                    x += 8f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color.copy(alpha = 0.28f + index * 0.08f))
        }
    }
}

/** Rings spreading out from behind the caller's tile, like a signal. */
@Composable
private fun Rings() {
    val accent = LocalAccentBarColor.current
    val progress by rememberInfiniteTransition(label = "rings").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "ringProgress"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * RingsCenterFraction)
        val maxRadius = size.width * 0.9f
        repeat(RingCount) { index ->
            val step = (progress + index.toFloat() / RingCount) % 1f
            drawCircle(
                color = accent.copy(alpha = 0.35f * (1f - step)),
                radius = maxRadius * step,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/** Bubbles drifting slowly up the screen. */
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
                radius = 10f + random.nextFloat() * 26f,
                // Whole cycles per loop, so every bubble wraps without a jump.
                laps = 1 + random.nextInt(2),
                start = random.nextFloat(),
                colorIndex = random.nextInt(3)
            )
        }
    }
    val progress by rememberInfiniteTransition(label = "bubbles").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "bubbleProgress"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        bubbles.forEach { bubble ->
            val radius = bubble.radius.dp.toPx()
            val travel = size.height + radius * 2f
            val rise = (progress * bubble.laps + bubble.start) % 1f
            drawCircle(
                color = colors[bubble.colorIndex].copy(alpha = 0.30f),
                radius = radius,
                center = Offset(bubble.x * size.width, size.height + radius - rise * travel)
            )
        }
    }
}

private class Bubble(val x: Float, val radius: Float, val laps: Int, val start: Float, val colorIndex: Int)

/** A band of the theme's colours sliding slowly across the top half, fading out downwards. */
@Composable
private fun Aurora() {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val colors = listOf(accent, scheme.tertiary, scheme.primaryContainer, accent)
    val shift by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "auroraShift"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val bandHeight = size.height * AuroraHeightFraction
        // The gradient repeats every two widths and slides by exactly that, so the loop is seamless.
        val period = size.width * 2f
        val offset = shift * period
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(-offset, 0f),
                end = Offset(period - offset, bandHeight),
                tileMode = TileMode.Repeated
            ),
            size = Size(size.width, bandHeight),
            alpha = 0.45f
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to scheme.surface.copy(alpha = 0f),
                1f to scheme.surface,
                startY = 0f,
                endY = bandHeight
            ),
            size = Size(size.width, bandHeight)
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
