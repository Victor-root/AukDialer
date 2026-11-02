package auk.dialer.vroot.view.screen.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.view.components.AukTopBarTitle
import auk.dialer.vroot.controller.CallRecorder
import auk.dialer.vroot.controller.util.SpeechPlayer
import auk.dialer.vroot.controller.util.areNumbersEqual
import auk.dialer.vroot.controller.util.formatDateHeader
import auk.dialer.vroot.controller.util.formatDuration
import auk.dialer.vroot.controller.util.formatPhoneNumber
import auk.dialer.vroot.controller.util.formatTime
import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import auk.dialer.vroot.view.components.AudioMessageRow
import auk.dialer.vroot.view.components.AudioRowAction
import auk.dialer.vroot.view.components.AukConfirmationDialog
import auk.dialer.vroot.view.components.AukDivider
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukLoadingIndicatorView
import auk.dialer.vroot.view.components.AukSectionHeader
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import auk.dialer.vroot.view.theme.aukExceptBottom
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Destination<RootGraph>
@Composable
fun CallRecordingsScreen(
    navigator: DestinationsNavigator
) {
    CallRecordingsContent(onNavigateUp = { navigator.navigateUp() })
}

/**
 * The saved call recordings, laid out and played like voicemails. With [onNavigateUp] it is a
 * screen of its own with a back arrow, without it a tab of the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecordingsContent(
    onNavigateUp: (() -> Unit)? = null,
    // False while another tab of the main screen is shown: the pager keeps this one composed.
    isShown: Boolean = true
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val contactsRepo = koinInject<IContactsRepository>()
    val callLogRepo = koinInject<ICallLogRepository>()
    val unknownLabel = stringResource(R.string.label_unknown)
    var recordings by remember { mutableStateOf<List<RecordingEntry>?>(null) }
    var pendingDelete by remember { mutableStateOf<CallRecorder.Recording?>(null) }
    val shareTitle = stringResource(R.string.call_recordings_share)

    // Same player as voicemails: loudspeaker or earpiece, with the screen off against the ear.
    var playingPath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    val playbackFailedText = stringResource(R.string.call_recordings_playback_failed)
    val player = remember {
        SpeechPlayer<String>(
            context = context.applicationContext,
            onUpdate = { path, playing, position, duration ->
                playingPath = path
                isPlaying = playing
                positionMs = position
                durationMs = duration
            },
            onError = { Toast.makeText(context, playbackFailedText, Toast.LENGTH_SHORT).show() },
            onAudioUnavailable = { Toast.makeText(context, playbackFailedText, Toast.LENGTH_SHORT).show() },
        )
    }

    // Playback stops with the list out of sight, and the list is read again on each return, a
    // call having possibly been recorded meanwhile.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> refreshKey++
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(isShown) {
        if (!isShown) player.release()
    }

    LaunchedEffect(refreshKey) {
        recordings = withContext(Dispatchers.IO) {
            identifyCallers(CallRecorder.loadRecordings(context), contactsRepo, callLogRepo, unknownLabel)
        }
    }

    Scaffold(
        topBar = {
            if (onNavigateUp != null) {
                TopAppBar(
                    colors = aukAccentTopAppBarColors(),
                    title = { AukTopBarTitle(stringResource(R.string.call_recordings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                )
            }
        },
        // As a tab, the main screen's header and bottom bar already clear the system bars.
        contentWindowInsets = if (onNavigateUp != null) ScaffoldDefaults.contentWindowInsets else WindowInsets(0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding.aukExceptBottom()).fillMaxSize()) {
            val list = recordings
            when {
                list == null -> AukLoadingIndicatorView(modifier = Modifier.fillMaxSize())
                list.isEmpty() -> EmptyRecordingsState()
                else -> {
                    val grouped = remember(list) { list.groupBy { formatDateHeader(context, it.recording.startedAt) } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + padding.calculateBottomPadding())
                    ) {
                        grouped.forEach { (header, group) ->
                            item(key = header) {
                                AukSectionHeader(title = header)
                                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                    AukExpressiveCard(isCompact = true) {
                                        group.forEachIndexed { index, entry ->
                                            val recording = entry.recording
                                            val path = recording.file.absolutePath
                                            val isCurrent = playingPath == path
                                            RecordingRow(
                                                entry = entry,
                                                isCurrent = isCurrent,
                                                isPlaying = isCurrent && isPlaying,
                                                positionMs = positionMs,
                                                durationMs = durationMs,
                                                isSpeakerOn = isSpeakerOn,
                                                onToggle = { player.toggle(path, Uri.fromFile(recording.file)) },
                                                onSeek = { player.seekTo(it) },
                                                onToggleSpeaker = {
                                                    isSpeakerOn = !isSpeakerOn
                                                    player.setSpeakerOn(isSpeakerOn)
                                                },
                                                onShare = { CallRecorder.share(context, recording.file, shareTitle) },
                                                onDelete = {
                                                    if (isCurrent) player.release()
                                                    pendingDelete = recording
                                                }
                                            )
                                            if (index < group.size - 1) {
                                                AukDivider(Modifier.padding(horizontal = 16.dp))
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { recording ->
        AukConfirmationDialog(
            onDismissRequest = { pendingDelete = null },
            onConfirm = {
                CallRecorder.delete(recording.file)
                pendingDelete = null
                refreshKey++
            },
            title = stringResource(R.string.call_recordings_delete_title),
            message = stringResource(R.string.call_recordings_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            icon = Icons.Default.Delete,
            isDestructive = true
        )
    }
}

/**
 * A recording with who it was with, as known now rather than when it was recorded: a number not in
 * the contacts then may be a contact since, or carry the name the call log got after the call
 * (a business name, for instance).
 */
private data class RecordingEntry(
    val recording: CallRecorder.Recording,
    val name: String,
    val photoUri: String?,
    val isIdentified: Boolean
)

/** Blocking: looks numbers up in the contacts, then in the call log. */
private fun identifyCallers(
    recordings: List<CallRecorder.Recording>,
    contacts: IContactsRepository,
    callLog: ICallLogRepository,
    unknownLabel: String
): List<RecordingEntry> {
    // Only read when a recording carries a number nobody in the contacts has.
    val calls by lazy { runCatching { callLog.getCalls() }.getOrDefault(emptyList()) }
    return recordings.map { recording ->
        val caller = recording.caller
        // Recorded under the contact's name when it was known, under the number otherwise.
        val isNumber = caller.isNotEmpty() && caller.all { it.isDigit() || it == '+' }
        if (!isNumber) {
            return@map RecordingEntry(recording, caller.ifEmpty { unknownLabel }, null, caller.isNotEmpty())
        }
        val contact = runCatching { contacts.getContactByNumber(caller) }.getOrNull()
        val name = contact?.name ?: calls.firstOrNull { call ->
            call.name != null && call.name != call.number && areNumbersEqual(call.number, caller)
        }?.name
        RecordingEntry(recording, name ?: formatPhoneNumber(caller), contact?.photoUri, name != null)
    }
}

@Composable
private fun RecordingRow(
    entry: RecordingEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    isSpeakerOn: Boolean,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleSpeaker: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val recording = entry.recording

    key(recording.file.absolutePath) {
        AudioMessageRow(
            title = entry.name,
            subtitle = recordingSubtitle(context, recording),
            avatarName = entry.name,
            photoUri = entry.photoUri,
            avatarIcon = if (entry.isIdentified) null else Icons.Outlined.MicNone,
            emphasized = false,
            canPlay = true,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            isSpeakerOn = isSpeakerOn,
            onToggle = onToggle,
            onSeek = onSeek,
            onToggleSpeaker = onToggleSpeaker,
            actions = listOf(
                AudioRowAction(stringResource(R.string.call_recordings_share), Icons.Outlined.Share, onShare),
                AudioRowAction(stringResource(R.string.action_delete), Icons.Outlined.Delete, onDelete)
            )
        )
    }
}

/** Time, then length, like a voicemail's details: the day is the group the row sits in. */
private fun recordingSubtitle(context: Context, recording: CallRecorder.Recording): String = buildString {
    append(formatTime(context, recording.startedAt))
    if (recording.durationMs > 0) {
        append("  ·  ")
        append(formatDuration(recording.durationMs / 1000))
    }
}

@Composable
private fun EmptyRecordingsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MicNone,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.call_recordings_empty),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.call_recordings_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}
