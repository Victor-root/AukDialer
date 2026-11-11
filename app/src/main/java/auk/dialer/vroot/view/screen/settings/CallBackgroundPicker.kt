package auk.dialer.vroot.view.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukDialog
import auk.dialer.vroot.view.components.AukDialogAction
import auk.dialer.vroot.view.screen.ExpressiveBackground
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor
import auk.dialer.vroot.view.theme.callColors

/** The call screen backgrounds on offer, in the order the picker shows them. */
val CallBackgroundStyles: List<Int> = listOf(
    PreferenceManager.CALL_BACKGROUND_ANIMATED,
    PreferenceManager.CALL_BACKGROUND_WAVES,
    PreferenceManager.CALL_BACKGROUND_RINGS,
    PreferenceManager.CALL_BACKGROUND_BUBBLES,
    PreferenceManager.CALL_BACKGROUND_AURORA,
    PreferenceManager.CALL_BACKGROUND_PLAIN,
    PreferenceManager.CALL_BACKGROUND_GRADIENT,
    PreferenceManager.CALL_BACKGROUND_PHOTO
)

@Composable
fun callBackgroundStyleLabel(style: Int): String = stringResource(
    when (style) {
        PreferenceManager.CALL_BACKGROUND_WAVES -> R.string.settings_call_background_style_waves
        PreferenceManager.CALL_BACKGROUND_RINGS -> R.string.settings_call_background_style_rings
        PreferenceManager.CALL_BACKGROUND_BUBBLES -> R.string.settings_call_background_style_bubbles
        PreferenceManager.CALL_BACKGROUND_AURORA -> R.string.settings_call_background_style_aurora
        PreferenceManager.CALL_BACKGROUND_PLAIN -> R.string.settings_call_background_style_plain
        PreferenceManager.CALL_BACKGROUND_GRADIENT -> R.string.settings_call_background_style_gradient
        PreferenceManager.CALL_BACKGROUND_PHOTO -> R.string.settings_call_background_style_photo
        else -> R.string.settings_call_background_style_animated
    }
)

/**
 * Picks the call screen background by swiping through full previews of the call screen, each with
 * its real background running behind a sample call.
 */
@Composable
fun CallBackgroundPickerDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = CallBackgroundStyles.indexOf(selected).coerceAtLeast(0)
    ) { CallBackgroundStyles.size }

    AukDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_call_background_style),
        icon = Icons.Outlined.Wallpaper,
        confirmAction = AukDialogAction(
            label = stringResource(R.string.settings_call_background_style_choose),
            onClick = {
                onSelect(CallBackgroundStyles[pagerState.currentPage])
                onDismissRequest()
            }
        ),
        dismissAction = AukDialogAction(
            label = stringResource(R.string.action_cancel),
            onClick = onDismissRequest
        )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CallScreenPreview(CallBackgroundStyles[page])
                Text(
                    text = callBackgroundStyleLabel(CallBackgroundStyles[page]),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            CallBackgroundStyles.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 10.dp else 7.dp)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            CircleShape
                        )
                )
            }
        }
    }
}

// The call screen is laid out at a phone's size and shrunk, so the preview is the screen itself.
private val PreviewScreenWidth = 360.dp
private val PreviewScreenHeight = 760.dp
private val PreviewWidth = 200.dp

/** A sample active call on [style]'s background, at a phone's layout shrunk to fit the dialog. */
@Composable
private fun CallScreenPreview(style: Int) {
    val scale = PreviewWidth / PreviewScreenWidth
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(PreviewWidth, PreviewScreenHeight * scale)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(PreviewScreenWidth, PreviewScreenHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            // The photo style needs a caller with a photo: a stand-in picture shows it.
            val samplePhoto = if (style == PreferenceManager.CALL_BACKGROUND_PHOTO) {
                "android.resource://${LocalContext.current.packageName}/${R.drawable.call_background_sample_photo}"
            } else {
                null
            }
            ExpressiveBackground(photoUri = samplePhoto, backgroundUri = null, style = style)
            SampleCall()
        }
    }
}

@Composable
private fun SampleCall() {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentBarColor.current
    val onAccent = LocalOnAccentBarColor.current
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 120.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(136.dp).background(accent, RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = onAccent, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.settings_call_background_preview_caller),
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .background(scheme.primaryContainer, CircleShape)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("02:47", style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.weight(1f))
        val actions = listOf(
            Icons.Default.Add, Icons.Default.FiberManualRecord, Icons.Default.Pause,
            Icons.Default.Mic, Icons.Default.Dialpad, Icons.AutoMirrored.Filled.VolumeUp
        )
        actions.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { icon -> SampleAction(icon, active = icon == Icons.AutoMirrored.Filled.VolumeUp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(60.dp)
                .background(MaterialTheme.callColors.decline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = null, tint = MaterialTheme.callColors.onDecline, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SampleAction(icon: ImageVector, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .weight(1f)
            .height(72.dp)
            .background(
                if (active) MaterialTheme.callColors.action else scheme.surfaceContainerHigh,
                RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.callColors.onAction else scheme.onSurfaceVariant
        )
    }
}
