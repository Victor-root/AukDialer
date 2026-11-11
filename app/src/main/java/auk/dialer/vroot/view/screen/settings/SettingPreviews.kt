package auk.dialer.vroot.view.screen.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.aukAvatarShape
import auk.dialer.vroot.view.theme.callColors

@Composable
fun IncomingCallUiPreview(mode: Int) {
    val scheme = MaterialTheme.colorScheme
    val answer = MaterialTheme.callColors.answer
    val decline = MaterialTheme.callColors.decline

    val transition = rememberInfiniteTransition(label = "previewAnim")
    val slideAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "slide"
    )
    val pulseAnim by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    when (mode) {
        0 -> Box(
            modifier = Modifier
                .width(52.dp)
                .height(22.dp)
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = slideAnim.dp)
                    .size(20.dp)
                    .background(answer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = scheme.surface, modifier = Modifier.size(10.dp))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).align(Alignment.CenterEnd)
            )
        }
        1 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(20.dp).background(decline, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CallEnd, contentDescription = null, tint = scheme.surface, modifier = Modifier.size(11.dp))
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .scale(pulseAnim)
                    .background(answer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = scheme.surface, modifier = Modifier.size(11.dp))
            }
        }
        2 -> Box(
            modifier = Modifier
                .width(52.dp)
                .height(20.dp)
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = slideAnim.dp)
                    .size(18.dp)
                    .background(scheme.primary, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp).align(Alignment.CenterEnd)
            )
        }
        else -> Column(
            modifier = Modifier
                .width(22.dp)
                .height(52.dp)
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(11.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .offset(y = (-slideAnim).dp)
                    .size(20.dp)
                    .background(answer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = scheme.surface, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
fun AvatarShapePreview(shapeType: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(aukAvatarShape(shapeType))
            .background(scheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun CardRoundnessPreview(roundnessDp: Int) {
    val scheme = MaterialTheme.colorScheme
    val cornerRadius by animateDpAsState(
        targetValue = (roundnessDp / 3).coerceIn(2, 10).dp,
        label = "RoundnessPreview"
    )
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 26.dp)
            .background(scheme.surfaceContainerHighest, RoundedCornerShape(cornerRadius))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(cornerRadius))
    )
}

/**
 * A tiny call screen for each background style, so the choice shows what it looks like: the
 * caller's tile on the style's background, with the photo style using a stand-in picture.
 */
@Composable
fun CallBackgroundPreview(style: Int) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val background = when (style) {
        PreferenceManager.CALL_BACKGROUND_GRADIENT -> Brush.verticalGradient(
            0f to accent,
            0.3f to accent.copy(alpha = 0.6f),
            0.7f to scheme.surface
        )
        PreferenceManager.CALL_BACKGROUND_PHOTO -> Brush.linearGradient(
            listOf(scheme.tertiary, scheme.primary, scheme.secondary)
        )
        else -> SolidColor(scheme.surface)
    }
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(7.dp))
    ) {
        if (style == PreferenceManager.CALL_BACKGROUND_ANIMATED) {
            Box(
                modifier = Modifier
                    .offset(x = (-6).dp, y = (-6).dp)
                    .size(20.dp)
                    .background(scheme.primaryContainer.copy(alpha = 0.5f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(22.dp)
                    .background(scheme.tertiaryContainer.copy(alpha = 0.5f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 10.dp)
                .size(12.dp)
                .background(accent, RoundedCornerShape(4.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-6).dp)
                .width(14.dp)
                .height(5.dp)
                .background(MaterialTheme.callColors.decline, RoundedCornerShape(3.dp))
        )
    }
}
