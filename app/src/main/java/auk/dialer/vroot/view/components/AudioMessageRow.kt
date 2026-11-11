package auk.dialer.vroot.view.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import auk.dialer.vroot.view.theme.softAccentColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R

/** One entry of an [AudioMessageRow]'s menu. */
data class AudioRowAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * A playable message in a list, voicemail or call recording: who it is from, its details, a play
 * button and a menu, then a seek bar and the speaker / earpiece switch while it is the one playing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioMessageRow(
    title: String,
    subtitle: String,
    avatarName: String,
    photoUri: String?,
    avatarIcon: ImageVector?,
    emphasized: Boolean,
    canPlay: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    isSpeakerOn: Boolean,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleSpeaker: () -> Unit,
    actions: List<AudioRowAction>,
    // Selecting several rows, as in the call log: a long press starts it, then a tap adds or
    // removes a row. Playback and the menu step aside meanwhile.
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) MaterialTheme.softAccentColors.selection else Color.Transparent)
            .then(
                if (onToggleSelection != null) {
                    Modifier.combinedClickable(
                        onClick = { if (selectionMode) onToggleSelection() },
                        onLongClick = onToggleSelection
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, aukAvatarStyle().shape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.content_desc_selected_item),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                AukAvatar(
                    name = avatarName,
                    photoUri = photoUri,
                    icon = avatarIcon,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canPlay && !selectionMode) {
                IconButton(onClick = onToggle) {
                    Icon(
                        // Rounded variants: same glyphs with softened corners.
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.playback_pause else R.string.playback_play
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        // A triangle and two bars cover less of their box than
                        // the overflow dots, so at equal size they read smaller.
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (!selectionMode) Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.playback_more_options)
                    )
                }
                AukDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    actions.forEach { action ->
                        AukDropdownMenuItem(
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                action.onClick()
                            }
                        )
                    }
                }
            }
        }

        if (isCurrent && durationMs > 0) {
            // While dragging, follow the finger rather than the player: the
            // player only reports the new position once the seek lands, which
            // otherwise makes the handle snap back under the finger.
            var scrubPositionMs by remember { mutableStateOf<Float?>(null) }
            val shownPositionMs = scrubPositionMs?.toInt() ?: positionMs.coerceIn(0, durationMs)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = formatPlaybackTime(shownPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = shownPositionMs.toFloat(),
                    onValueChange = { scrubPositionMs = it },
                    onValueChangeFinished = {
                        scrubPositionMs?.let { onSeek(it.toInt()) }
                        scrubPositionMs = null
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onToggleSpeaker) {
                    Icon(
                        imageVector = if (isSpeakerOn) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.Default.Phone
                        },
                        contentDescription = stringResource(
                            if (isSpeakerOn) R.string.playback_route_speaker
                            else R.string.playback_route_earpiece
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Playback clock as m:ss, independent of the locale-aware list duration label. */
private fun formatPlaybackTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
