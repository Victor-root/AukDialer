package auk.dialer.vroot.view.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
    val animated = style == PreferenceManager.CALL_BACKGROUND_ANIMATED ||
        (style == PreferenceManager.CALL_BACKGROUND_PHOTO && !hasPhoto)
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
        if (!customVisible && animated) {
            AmbientBlobs()
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
