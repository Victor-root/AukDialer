package auk.dialer.vroot.view.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * The slow colour wash behind the app: a handful of large, softly overlapping fields built from the
 * chosen accent, drifting into one another rather than reading as separate dots. Purely decorative,
 * never takes touch.
 *
 * It replaces the accent tint Material used to bake into every surface. The pages themselves stay
 * plain white or plain dark so text is readable, and the colour lives here instead.
 */
@Composable
fun AukAuroraBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val alpha = if (dark) DarkAlpha else LightAlpha
    val accent = MaterialTheme.colorScheme.primary
    val palette = remember(accent) { auroraPaletteFrom(accent) }
    val transition = rememberInfiniteTransition(label = "aurora")
    // One state pair per field, all from the same transition, read inside the draw lambda rather
    // than through `by`, so each frame redraws without recomposing.
    val drifts = AuroraFields.map { spec -> spec.driftXState(transition) to spec.driftYState(transition) }

    // Everything goes into one Canvas with additive blending, not one Box per field: separately
    // composited layers still show as distinct dots where they overlap instead of merging. Plus
    // needs an alpha channel to blend against, hence the offscreen layer.
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        AuroraFields.forEachIndexed { index, spec ->
            val (driftX, driftY) = drifts[index]
            val center = Offset(
                x = size.width * (0.5f + spec.xBias / 2f) + spec.driftDp.dp.toPx() * driftX.value,
                y = size.height * (0.5f + spec.yBias / 2f) + spec.driftDp.dp.toPx() * driftY.value
            )
            val radius = spec.sizeDp.dp.toPx()
            val color = palette[index]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Plus
            )
        }
    }
}

private data class AuroraField(
    val xBias: Float,
    val yBias: Float,
    val sizeDp: Int,
    val driftDp: Int,
    val durationMs: Int,
    val delayMs: Int
)

/**
 * The sizes are deliberately far wider than a phone: several such fields overlapping across the
 * screen and blending additively is what reads as one continuous wash. One entry per colour in
 * [auroraPaletteFrom], matched by index.
 */
private val AuroraFields = listOf(
    AuroraField(-0.85f, -0.9f, 340, 42, 18000, 0),
    AuroraField(0.9f, -0.75f, 300, 38, 21000, 3000),
    AuroraField(-0.8f, 0.15f, 360, 46, 19500, 6000),
    AuroraField(0.85f, 0.4f, 310, 40, 23000, 1500),
    AuroraField(-0.6f, 0.9f, 330, 44, 20000, 4500),
    AuroraField(0.65f, 0.92f, 290, 36, 22000, 7500)
)

/**
 * An analogous palette around the chosen accent, one colour per field, by rotating its hue.
 * Saturation and value move a little too, so the wash has some variety rather than every field
 * reading at the same brightness.
 */
private fun auroraPaletteFrom(seed: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    return AuroraHueShifts.map { (hueOffset, satFactor, valueFactor) ->
        val shifted = floatArrayOf(
            (hsv[0] + hueOffset).mod(360f),
            (hsv[1] * satFactor).coerceIn(0.35f, 1f),
            (hsv[2] * valueFactor).coerceIn(0.55f, 1f)
        )
        Color(android.graphics.Color.HSVToColor(shifted))
    }
}

/** Hue shift in degrees, saturation multiplier, value multiplier, one per field. */
private val AuroraHueShifts = listOf(
    Triple(-50f, 0.9f, 1.05f),
    Triple(-24f, 1f, 0.9f),
    Triple(0f, 1f, 1f),
    Triple(20f, 0.85f, 1.1f),
    Triple(42f, 1f, 0.95f),
    Triple(65f, 0.8f, 1f)
)

// Additive blending brightens every overlap, so a single field stays fainter than it would need to
// be on its own, or three or four stacked up wash the page out.
private const val LightAlpha = 0.14f
private const val DarkAlpha = 0.22f

/**
 * Two axes on odd ratios, so the drift traces a slow loop rather than a straight line back and
 * forth, and each field starts at its own point in that loop.
 */
@Composable
private fun AuroraField.driftXState(transition: InfiniteTransition): State<Float> =
    transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs, StartOffsetType.FastForward)
        ),
        label = "driftX"
    )

@Composable
private fun AuroraField.driftYState(transition: InfiniteTransition): State<Float> =
    transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (durationMs * 1.3f).toInt(), easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs + 900, StartOffsetType.FastForward)
        ),
        label = "driftY"
    )
