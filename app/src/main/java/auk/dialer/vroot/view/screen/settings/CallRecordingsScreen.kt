package auk.dialer.vroot.view.screen.settings

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.MicNone
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
import auk.dialer.vroot.controller.CallRecorder
import auk.dialer.vroot.controller.util.SpeechPlayer
import auk.dialer.vroot.view.components.AukConfirmationDialog
import auk.dialer.vroot.view.components.AukDivider
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import auk.dialer.vroot.view.theme.aukExceptBottom
import auk.dialer.vroot.view.theme.aukScrollContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallRecordingsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var recordings by remember { mutableStateOf<List<File>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    val shareTitle = stringResource(R.string.call_recordings_share)

    // Same player as voicemails: loudspeaker or earpiece, with the screen off against the ear.
    var playingPath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    val playbackFailedText = stringResource(R.string.call_recordings_playback_failed)
    val player = remember {
        SpeechPlayer<String>(
            context = context.applicationContext,
            onUpdate = { path, playing, _, _ ->
                playingPath = path
                isPlaying = playing
            },
            onError = { Toast.makeText(context, playbackFailedText, Toast.LENGTH_SHORT).show() },
            onAudioUnavailable = { Toast.makeText(context, playbackFailedText, Toast.LENGTH_SHORT).show() },
        )
    }

    fun togglePlayback(file: File) = player.toggle(file.absolutePath, Uri.fromFile(file))

    // Playback stops with the list out of sight: nothing else could stop it.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(refreshKey) {
        recordings = CallRecorder.listRecordings(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { Text(stringResource(R.string.call_recordings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (recordings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.MicNone,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.call_recordings_empty),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.call_recordings_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding.aukExceptBottom()),
                contentPadding = aukScrollContentPadding(16.dp, padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recordings, key = { it.absolutePath }) { file ->
                    val isCurrent = playingPath == file.absolutePath
                    val isFilePlaying = isCurrent && isPlaying
                    AukExpressiveCard {
                        AukListItem(
                            headline = file.nameWithoutExtension,
                            supporting = recordingSubtitle(file),
                            headlineMaxLines = 2,
                            supportingMaxLines = 2,
                            leadingIcon = Icons.Outlined.MicNone,
                            onClick = { togglePlayback(file) }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isCurrent) {
                                IconButton(onClick = {
                                    isSpeakerOn = !isSpeakerOn
                                    player.setSpeakerOn(isSpeakerOn)
                                }) {
                                    Icon(
                                        if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Phone,
                                        contentDescription = stringResource(
                                            if (isSpeakerOn) R.string.playback_route_speaker
                                            else R.string.playback_route_earpiece
                                        )
                                    )
                                }
                            }
                            IconButton(onClick = { togglePlayback(file) }) {
                                Icon(
                                    if (isFilePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(
                                        if (isFilePlaying) R.string.call_recordings_pause else R.string.call_recordings_play
                                    )
                                )
                            }
                            IconButton(onClick = { CallRecorder.share(context, file, shareTitle) }) {
                                Icon(Icons.Default.Share, contentDescription = shareTitle)
                            }
                            IconButton(onClick = {
                                if (isCurrent) player.release()
                                pendingDelete = file
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AukConfirmationDialog(
            onDismissRequest = { pendingDelete = null },
            onConfirm = {
                CallRecorder.delete(file)
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

private fun recordingSubtitle(file: File): String {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    val sizeKb = file.length() / 1024
    return "$date  •  ${sizeKb} KB"
}
