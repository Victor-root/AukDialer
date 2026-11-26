package auk.dialer.vroot.view.screen.settings

import auk.dialer.vroot.view.components.mainPageBottom
import auk.dialer.vroot.view.components.LocalPrivacyMask
import auk.dialer.vroot.view.components.AukScaffold
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import java.io.File
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.view.components.rememberCurrentDay
import auk.dialer.vroot.R
import auk.dialer.vroot.view.components.AukTopBarTitle
import auk.dialer.vroot.controller.CallRecorder
import auk.dialer.vroot.controller.CallRecordingsViewModel
import auk.dialer.vroot.controller.RecordingEntry
import auk.dialer.vroot.controller.util.SpeechPlayer
import auk.dialer.vroot.controller.util.formatDateHeader
import auk.dialer.vroot.controller.util.formatDuration
import auk.dialer.vroot.controller.util.formatTime
import auk.dialer.vroot.view.components.AudioMessageRow
import auk.dialer.vroot.view.components.AudioRowAction
import auk.dialer.vroot.view.components.AukConfirmationDialog
import auk.dialer.vroot.view.components.AukExpressiveCardSegment
import auk.dialer.vroot.view.components.AukLoadingIndicatorView
import auk.dialer.vroot.view.components.AukSectionHeader
import auk.dialer.vroot.view.components.SelectionDeleteActionBar
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import auk.dialer.vroot.view.theme.aukExceptBottom
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.viewmodel.koinActivityViewModel

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
    isShown: Boolean = true,
    // Hosted by the main screen, which shows the selection header in its own collapsing header.
    onSelectionStateChange: ((Boolean, (@Composable () -> Unit)?) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: CallRecordingsViewModel = koinActivityViewModel()
    val recordings by viewModel.recordings.collectAsState()
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
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.refresh()
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

    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val isSelecting = selectedPaths.isNotEmpty()
    // A recording deleted or gone from the folder leaves the selection.
    LaunchedEffect(recordings) {
        val present = recordings.orEmpty().map { it.recording.file.absolutePath }.toSet()
        selectedPaths = selectedPaths.filter { it in present }.toSet()
    }
    BackHandler(enabled = isSelecting) { selectedPaths = emptySet() }
    val selectionActionBar: @Composable () -> Unit = {
        SelectionDeleteActionBar(
            selectedCount = selectedPaths.size,
            onClearSelection = { selectedPaths = emptySet() },
            confirmTitle = pluralStringResource(R.plurals.call_recordings_delete_selected_title, selectedPaths.size, selectedPaths.size),
            confirmMessage = pluralStringResource(R.plurals.call_recordings_delete_selected_message, selectedPaths.size),
            onDelete = {
                if (playingPath in selectedPaths) player.release()
                selectedPaths.forEach { CallRecorder.delete(File(it)) }
                selectedPaths = emptySet()
                viewModel.refresh()
            }
        )
    }
    LaunchedEffect(isSelecting, selectedPaths.size) {
        onSelectionStateChange?.invoke(isSelecting, if (isSelecting) selectionActionBar else null)
    }

    AukScaffold(
        topBar = {
            if (onNavigateUp != null && isSelecting) {
                selectionActionBar()
            } else if (onNavigateUp != null) {
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
        // As a tab, the main screen's header and bottom bar already clear the system bars, and its
        // header is the one that collapses.
        contentWindowInsets = if (onNavigateUp != null) ScaffoldDefaults.contentWindowInsets else WindowInsets(0),
        collapsing = onNavigateUp != null
    ) { padding ->
        Box(modifier = Modifier.padding(padding.aukExceptBottom()).fillMaxSize()) {
            val list = recordings
            when {
                list == null -> AukLoadingIndicatorView(modifier = Modifier.fillMaxSize())
                list.isEmpty() -> EmptyRecordingsState()
                else -> {
                    val today = rememberCurrentDay()
                    val grouped = remember(list, today) { list.groupBy { formatDateHeader(context, it.recording.startedAt) } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = mainPageBottom(16.dp + padding.calculateBottomPadding()))
                    ) {
                        grouped.forEach { (header, group) ->
                            item(key = "header:$header", contentType = "header") {
                                AukSectionHeader(title = header)
                            }
                            itemsIndexed(
                                group,
                                key = { _, entry -> entry.recording.file.absolutePath },
                                contentType = { _, _ -> "recording" }
                            ) { index, entry ->
                                val isLast = index == group.lastIndex
                                val recording = entry.recording
                                val path = recording.file.absolutePath
                                val isCurrent = playingPath == path
                                AukExpressiveCardSegment(
                                    isFirst = index == 0,
                                    isLast = isLast,
                                    isCompact = true,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = if (isLast) 8.dp else 0.dp)
                                ) {
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
                                        },
                                        selected = path in selectedPaths,
                                        selectionMode = isSelecting,
                                        onToggleSelection = {
                                            selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                                        }
                                    )
                                }
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
                viewModel.refresh()
            },
            title = stringResource(R.string.call_recordings_delete_title),
            message = stringResource(R.string.call_recordings_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            icon = Icons.Default.Delete,
            isDestructive = true
        )
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
    onDelete: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit
) {
    val context = LocalContext.current
    val recording = entry.recording
    val privacy = LocalPrivacyMask.current
    // An unknown caller goes by their number, which the mask then treats as one.
    val shownName = privacy.name(entry.name)

    key(recording.file.absolutePath) {
        AudioMessageRow(
            title = shownName,
            subtitle = recordingSubtitle(context, recording),
            avatarName = shownName,
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
            ),
            selected = selected,
            selectionMode = selectionMode,
            onToggleSelection = onToggleSelection
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
                    color = MaterialTheme.colorScheme.surfaceContainer,
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
