package auk.dialer.vroot.view.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * The colour wash behind the app: a slow diagonal sweep through a set of colours built from the
 * chosen accent by rotating its hue, so it runs from one side of the accent's neighbourhood to the
 * other rather than being one flat tint.
 *
 * It replaces the accent tint Material used to bake into every surface. The pages themselves stay
 * plain white or plain dark so text is readable, and the colour lives here instead.
 *
 * Every colour is mixed with the page colour up front and painted opaque. Translucent fields
 * blended in their own compositing layer, which is how this started, came out as a solid black
 * rectangle on a real device, and an opaque gradient reaches the same look with nothing to lose.
 */
@Composable
fun AukAuroraBackground(modifier: Modifier = Modifier) {
    val strength = if (isSystemInDarkTheme()) DarkStrength else LightStrength
    val accent = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.background
    val colors = remember(accent, base, strength) {
        auroraPaletteFrom(accent).map { lerp(base, it, strength) }
    }

    // The sweep drifts slowly between two diagonals, so the wash is never quite static without ever
    // being something the eye can catch moving.
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = DriftDurationMs, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraDrift"
    )

    Canvas(modifier.fillMaxSize()) {
        val bleed = size.height * DriftAmount * (drift - 0.5f)
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(size.width * (0.5f - DriftAmount * drift), -bleed),
                end = Offset(size.width * (0.5f + DriftAmount * drift), size.height + bleed)
            )
        )
    }
}

/**
 * The colours the wash runs through, one step at a time around the accent's own hue. Saturation and
 * value move a little too, so the sweep has some variety rather than reading as one hue fading.
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

/** Hue shift in degrees, saturation multiplier, value multiplier, one per stop. */
private val AuroraHueShifts = listOf(
    Triple(-50f, 0.9f, 1.05f),
    Triple(-24f, 1f, 0.9f),
    Triple(0f, 1f, 1f),
    Triple(20f, 0.85f, 1.1f),
    Triple(42f, 1f, 0.95f),
    Triple(65f, 0.8f, 1f)
)

/** How far each colour is mixed into the page. Dark pages swallow colour, so they take more of it. */
private const val LightStrength = 0.18f
private const val DarkStrength = 0.30f

private const val DriftDurationMs = 25000
private const val DriftAmount = 0.25f

/**
 * How much of a surface's own colour sits over the wash drawn behind it: still enough to read as
 * that surface's own chrome, but low enough that the wash keeps showing through it instead of being
 * blocked by a flat, unrelated block of colour. Shared by anything that would otherwise break the
 * one background the theme paints into its own separate patch, cards and the bottom bar alike.
 */
internal const val AukVeilAlpha = 0.55f

/** [this] with [AukVeilAlpha] applied. */
internal fun Color.aukVeiled(alpha: Float = AukVeilAlpha): Color = copy(alpha = alpha)
