package auk.dialer.vroot.view.components

import android.provider.CallLog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.formatDate
import auk.dialer.vroot.controller.util.formatPhoneNumber
import auk.dialer.vroot.controller.util.formatTime
import auk.dialer.vroot.modal.data.CallLogEntry

@Composable
fun CallLogTileSimple(
    log: CallLogEntry,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    selected: Boolean = false
) {
    val prefs = org.koin.compose.koinInject<auk.dialer.vroot.controller.util.PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()

    val icon = when (log.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
        else -> Icons.Default.Call
    }

    val badgeColor = if (log.type == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val headlineColor = if (log.type == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else Color.Unspecified

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AukListItem(
                    headline = when (log.type) {
                        CallLog.Calls.INCOMING_TYPE -> stringResource(R.string.call_type_incoming)
                        CallLog.Calls.OUTGOING_TYPE -> stringResource(R.string.call_type_outgoing)
                        CallLog.Calls.MISSED_TYPE -> stringResource(R.string.call_type_missed)
                        else -> stringResource(R.string.action_call)
                    },
                    supporting = buildString {
                        append(formatDate(context, log.date))
                        if (log.duration > 0) append(" • ${android.text.format.DateUtils.formatElapsedTime(log.duration)}")
                    },
                    avatarName = "", 
                    badgeIcon = icon,
                    badgeColor = badgeColor,
                    headlineColor = headlineColor,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    selected = selected
                )
            }
            
            if (!selected) {
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.action_call),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CallLogTile(
    log: CallLogEntry,
    onTileClick: (CallLogEntry) -> Unit,
    onButtonClick: (CallLogEntry) -> Unit,
    onLongClick: (CallLogEntry) -> Unit = {},
    selected: Boolean = false,
    displayOrder: Int = 0
) {
    val prefs = org.koin.compose.koinInject<auk.dialer.vroot.controller.util.PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()

    val icon = when (log.type) {
        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.Default.Call
    }
    
    val badgeColor = if (log.type == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val headlineColor = if (log.type == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else Color.Unspecified
    
    val favNum = log.contactId?.let { prefs.getFavoriteNumber(it) }
    val isFavorite = auk.dialer.vroot.controller.util.areNumbersEqual(log.number, favNum)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val displayName = remember(log.name, displayOrder) {
                    log.name?.let { 
                        if (it.isNotEmpty()) auk.dialer.vroot.controller.util.ContactUtils.formatContactName(it, displayOrder) else null
                    } ?: formatPhoneNumber(log.number)
                }

                AukListItem(
                    headline = buildString {
                        append(displayName)
                        if (log.count > 1) append(" (${log.count})")
                    },
                    // One line of detail, not two. The number is dropped when the
                    // contact is known, since the name already identifies them and
                    // spelling it out is what pushed the time off the row.
                    supportingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatTime(context, log.date),
                                style = AukListItemDefaults.supportingStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    avatarName = log.name ?: formatPhoneNumber(log.number),
                    photoUri = log.photoUri,
                    headlineColor = headlineColor,
                    trailingIcon = if (isFavorite) Icons.Default.Star else null,
                    onClick = { onTileClick(log) },
                    onLongClick = { onLongClick(log) },
                    selected = selected
                )
            }
            
            if (!selected) {
                // 40dp rather than the default 48dp touch target, and no trailing
                // padding: the card and the screen already inset this edge, and
                // every point reserved here is a point the name does not get.
                IconButton(
                    onClick = { onButtonClick(log) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.action_call),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun BatchCallLogActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onAddContact: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    AukAccentHeader {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_clear_selection))
                }
                Text(
                    text = stringResource(R.string.selection_count_selected, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                if (onAddContact != null) {
                    IconButton(onClick = onAddContact) {
                        Icon(Icons.Default.PersonAdd, stringResource(R.string.contact_add_to_contacts))
                    }
                }
                if (onCopy != null) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.action_copy_number))
                    }
                }
                IconButton(onClick = { showBlockConfirm = true }) {
                    Icon(Icons.Default.Block, stringResource(R.string.action_block_number))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.content_desc_delete_selected))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AukConfirmationDialog(
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = onDelete,
            title = stringResource(R.string.call_log_delete_title),
            message = stringResource(R.string.call_log_delete_confirm, selectedCount),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            icon = Icons.Default.Delete,
            isDestructive = true
        )
    }

    if (showBlockConfirm) {
        AukConfirmationDialog(
            onDismissRequest = { showBlockConfirm = false },
            onConfirm = onBlock,
            title = stringResource(R.string.call_log_block_title),
            message = stringResource(R.string.call_log_block_message, selectedCount),
            confirmLabel = stringResource(R.string.action_block),
            dismissLabel = stringResource(R.string.action_cancel),
            icon = Icons.Default.Block,
            isDestructive = true
        )
    }
}
