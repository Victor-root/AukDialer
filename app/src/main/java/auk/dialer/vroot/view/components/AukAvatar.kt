package auk.dialer.vroot.view.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.RoundedPolygon
import coil.compose.AsyncImage
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.theme.AUK_AVATAR_SHAPE_SQUIRCLE
import auk.dialer.vroot.view.theme.AukMaterialShapes
import auk.dialer.vroot.view.theme.AukMotion
import auk.dialer.vroot.view.theme.rememberAukMorphShape
import auk.dialer.vroot.view.theme.aukAvatarShape
import org.koin.compose.koinInject
import kotlin.math.abs

@Immutable
data class AukAvatarStyle(
    val showPicture: Boolean,
    val showFirstLetter: Boolean,
    val colorful: Boolean,
    val gradient: Boolean,
    val shapeIndex: Int,
    val shape: Shape,
    /** Vivid colours: full tints with white letters rather than pastel ones, in both themes. */
    val vivid: Boolean
)

@Immutable
data class AukAvatarColors(
    val container: Color,
    val content: Color
)

object AukAvatarDefaults {
    const val HueCount: Int = 12

    val IconSize: Dp = 24.dp
    val BadgeSize: Dp = 18.dp
    val BadgeIconSize: Dp = 12.dp

    const val LightContainerSaturation: Float = 0.62f
    const val LightContainerLightness: Float = 0.82f
    const val LightContentSaturation: Float = 0.88f
    const val LightContentLightness: Float = 0.22f

    const val DarkContainerSaturation: Float = 0.34f
    const val DarkContainerLightness: Float = 0.26f
    const val DarkContentSaturation: Float = 0.80f
    const val DarkContentLightness: Float = 0.88f

    const val VividContainerSaturation: Float = 0.62f
    const val VividContainerLightness: Float = 0.40f
}

val LocalAukAvatarStyle: ProvidableCompositionLocal<AukAvatarStyle?> =
    staticCompositionLocalOf { null }

/**
 * The icons avatars fall back on, shared app wide. A painter turns its vector into a bitmap the first
 * time it draws: one per avatar meant doing that again for every row scrolling into a list. Only
 * used at the usual icon size, so one bitmap fits every avatar drawing it.
 */
@Stable
class AukAvatarIcons(val person: Painter, val voicemail: Painter)

val LocalAukAvatarIcons: ProvidableCompositionLocal<AukAvatarIcons?> =
    staticCompositionLocalOf { null }

@Composable
fun rememberAukAvatarIcons(): AukAvatarIcons {
    val person = rememberVectorPainter(Icons.Default.Person)
    val voicemail = rememberVectorPainter(Icons.Outlined.Voicemail)
    return remember(person, voicemail) { AukAvatarIcons(person, voicemail) }
}

@Composable
private fun avatarFallbackIcon(vector: ImageVector, iconSize: Dp, shared: (AukAvatarIcons) -> Painter): Painter {
    val icons = LocalAukAvatarIcons.current
    return if (icons != null && iconSize == AukAvatarDefaults.IconSize) shared(icons) else rememberVectorPainter(vector)
}

@Composable
fun rememberAukAvatarStyle(prefs: PreferenceManager = koinInject()): AukAvatarStyle {
    val settingsVersion by prefs.settingsChanged.collectAsState()
    val showPicture = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true)
    }
    val showFirstLetter = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true)
    }
    val colorful = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, true)
    }
    val gradient = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_GRADIENT_AVATARS, false)
    }
    val shapeIndex = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, AUK_AVATAR_SHAPE_SQUIRCLE)
    }
    val vivid = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_VIVID_COLORS, true)
    }
    val shape = aukAvatarShape(shapeIndex)
    return remember(showPicture, showFirstLetter, colorful, gradient, shapeIndex, shape, vivid) {
        AukAvatarStyle(showPicture, showFirstLetter, colorful, gradient, shapeIndex, shape, vivid)
    }
}

@Composable
fun aukAvatarStyle(): AukAvatarStyle {
    val provided = LocalAukAvatarStyle.current
    if (provided != null) return provided
    return rememberAukAvatarStyle()
}

fun aukAvatarHueIndex(name: String): Int =
    (abs(name.hashCode().toLong()) % AukAvatarDefaults.HueCount).toInt()

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))

private fun aukTintedAvatarColors(name: String, dark: Boolean, vivid: Boolean): AukAvatarColors {
    val hue = aukAvatarHueIndex(name) * (360f / AukAvatarDefaults.HueCount)
    return if (vivid) {
        AukAvatarColors(
            container = hslColor(
                hue,
                AukAvatarDefaults.VividContainerSaturation,
                AukAvatarDefaults.VividContainerLightness
            ),
            content = Color.White
        )
    } else if (dark) {
        AukAvatarColors(
            container = hslColor(
                hue,
                AukAvatarDefaults.DarkContainerSaturation,
                AukAvatarDefaults.DarkContainerLightness
            ),
            content = hslColor(
                hue,
                AukAvatarDefaults.DarkContentSaturation,
                AukAvatarDefaults.DarkContentLightness
            )
        )
    } else {
        AukAvatarColors(
            container = hslColor(
                hue,
                AukAvatarDefaults.LightContainerSaturation,
                AukAvatarDefaults.LightContainerLightness
            ),
            content = hslColor(
                hue,
                AukAvatarDefaults.LightContentSaturation,
                AukAvatarDefaults.LightContentLightness
            )
        )
    }
}

@Composable
fun aukAvatarColors(name: String, colorful: Boolean = true, vivid: Boolean = aukAvatarStyle().vivid): AukAvatarColors {
    val scheme = MaterialTheme.colorScheme
    val tinted = colorful && name.any { it.isLetter() }
    val dark = scheme.surface.luminance() < 0.5f
    val neutralContainer = scheme.secondaryContainer
    val neutralContent = scheme.onSecondaryContainer
    return remember(name, tinted, dark, vivid, neutralContainer, neutralContent) {
        if (tinted) {
            aukTintedAvatarColors(name, dark, vivid)
        } else {
            AukAvatarColors(neutralContainer, neutralContent)
        }
    }
}

private fun gradientAvatarBrush(name: String, dark: Boolean): Brush {
    val baseHue = aukAvatarHueIndex(name) * (360f / AukAvatarDefaults.HueCount)
    val accentHue = (baseHue + 180f) % 360f

    return if (dark) {
        Brush.radialGradient(
            0.0f to hslColor(baseHue, 0.75f, 0.45f),
            1.0f to hslColor(accentHue, 0.60f, 0.22f),
            center = Offset(0.25f, 0.15f),
            radius = 1.3f
        )
    } else {
        Brush.radialGradient(
            0.0f to hslColor(baseHue, 0.80f, 0.85f),
            1.0f to hslColor(accentHue, 0.65f, 0.55f),
            center = Offset(0.25f, 0.15f),
            radius = 1.3f
        )
    }
}

private fun contactInitials(name: String, useTwo: Boolean): String {
    val letters = name.filter { it.isLetter() }
    if (letters.isEmpty()) return ""
    if (!useTwo) return letters.first().uppercase()
    val words = name.trim().split(Regex("\\s+")).filter { it.any { c -> c.isLetter() } }
    return if (words.size >= 2) {
        words.take(2).joinToString("") { word ->
            word.first { it.isLetter() }.uppercase()
        }
    } else {
        letters.take(2).uppercase()
    }
}

@Composable
fun AukAvatar(
    name: String,
    photoUri: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    badgeIcon: ImageVector? = null,
    badgeColor: Color? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    iconSize: Dp = AukAvatarDefaults.IconSize,
    style: AukAvatarStyle = aukAvatarStyle(),
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    morphOnPress: Boolean = false,
    morphSelected: Boolean = false,
    morphStart: RoundedPolygon = AukMaterialShapes.AvatarMorphStart,
    morphEnd: RoundedPolygon = AukMaterialShapes.AvatarMorphEnd
) {
    val ownInteractionSource = remember { MutableInteractionSource() }
    val resolvedInteractionSource = interactionSource ?: ownInteractionSource

    // Only set up for an avatar that morphs: building the morph is costly, and a list holds many
    // avatars that never do.
    val avatarShape = when {
        morphOnPress || morphSelected -> {
            val pressed = morphOnPress && resolvedInteractionSource.collectIsPressedAsState().value
            val morphProgress by animateFloatAsState(
                targetValue = if (morphSelected || pressed) 1f else 0f,
                animationSpec = AukMotion.shapeMorph(),
                label = "AukAvatarMorph"
            )
            rememberAukMorphShape(morphStart, morphEnd) { morphProgress }
        }
        shape != null -> shape
        else -> style.shape
    }

    val colors = aukAvatarColors(name, style.colorful, style.vivid)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gradientBrush = if (style.gradient) {
        remember(name, dark) { gradientAvatarBrush(name, dark) }
    } else null
    val hasLetters = name.any { it.isLetter() }
    val description = contentDescription

    val rootModifier = modifier
        .then(
            if (description != null) {
                Modifier.semantics { this.contentDescription = description }
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier
                    .clip(avatarShape)
                    .clickable(
                        interactionSource = resolvedInteractionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onClick = onClick
                    )
            } else {
                Modifier
            }
        )

    val backgroundModifier = if (gradientBrush != null) {
        Modifier.background(gradientBrush, avatarShape)
    } else {
        Modifier.background(colors.container, avatarShape)
    }

    Box(modifier = rootModifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .clip(avatarShape),
            contentAlignment = Alignment.Center
        ) {
            if (style.showPicture && !photoUri.isNullOrEmpty() && photoUri != "voicemail://icon") {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(iconSize)
                )
            } else if (photoUri == "voicemail://icon" || name.equals("Voicemail", ignoreCase = true) || name.equals("Messagerie vocale", ignoreCase = true) || name.equals("Poczta głosowa", ignoreCase = true)) {
                Icon(
                    painter = avatarFallbackIcon(Icons.Outlined.Voicemail, iconSize) { it.voicemail },
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(iconSize)
                )
            } else if (style.showFirstLetter && hasLetters) {
                Text(
                    text = contactInitials(name, style.gradient),
                    style = textStyle,
                    color = colors.content
                )
            } else {
                Icon(
                    painter = avatarFallbackIcon(Icons.Default.Person, iconSize) { it.person },
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        if (badgeIcon != null) {
            Surface(
                modifier = Modifier
                    .size(AukAvatarDefaults.BadgeSize)
                    .align(Alignment.BottomEnd),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = AukElevation.Raised
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AukAvatarDefaults.BadgeIconSize)
                    )
                }
            }
        }
    }
}
