package com.grinch.rivo4.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.grinch.rivo4.R
import com.grinch.rivo4.controller.VoicemailViewModel
import com.grinch.rivo4.controller.util.formatDate
import com.grinch.rivo4.controller.util.formatDuration
import com.grinch.rivo4.controller.util.formatPhoneNumber
import com.grinch.rivo4.controller.util.getDefaultDialerIntent
import com.grinch.rivo4.modal.data.Voicemail
import com.grinch.rivo4.view.components.BottomBar
import com.grinch.rivo4.view.components.PermissionDeniedView
import com.grinch.rivo4.view.components.RivoAvatar
import com.grinch.rivo4.view.components.RivoDivider
import com.grinch.rivo4.view.screen.transitions.NoTransitions
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(style = NoTransitions::class)
@Composable
fun VoicemailListScreen(
    navController: NavController,
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val viewModel: VoicemailViewModel = koinActivityViewModel()

    val voicemails by viewModel.voicemails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val message by viewModel.message.collectAsState()
    val audioUnavailable by viewModel.audioUnavailable.collectAsState()

    var isDefaultDialer by remember { mutableStateOf(viewModel.isDefaultDialer()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val audioUnavailableText = stringResource(R.string.voicemail_audio_unavailable)

    LaunchedEffect(Unit) {
        isDefaultDialer = viewModel.isDefaultDialer()
        if (isDefaultDialer) {
            viewModel.registerSmsFilter()
            viewModel.fetchVoicemails()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(audioUnavailable) {
        if (audioUnavailable) {
            snackbarHostState.showSnackbar(audioUnavailableText)
            viewModel.consumeAudioUnavailable()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.voicemail_title), fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (isDefaultDialer) {
                        IconButton(onClick = { viewModel.syncNow() }, enabled = !isSyncing) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.voicemail_sync_now)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { BottomBar(navController, navigator) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !isDefaultDialer -> PermissionDeniedView(
                    icon = Icons.Outlined.Voicemail,
                    title = stringResource(R.string.voicemail_needs_default_dialer_title),
                    description = stringResource(R.string.voicemail_needs_default_dialer_description),
                    buttonText = stringResource(R.string.default_dialer_set_as_default),
                    onGrantClick = { context.startActivity(getDefaultDialerIntent(context)) }
                )

                isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                else -> PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = { viewModel.syncNow() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (voicemails.isEmpty()) {
                        VoicemailEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(voicemails, key = { it.id }) { voicemail ->
                                VoicemailRow(
                                    voicemail = voicemail,
                                    isCurrent = playback.playingId == voicemail.id,
                                    isPlaying = playback.isPlaying && playback.playingId == voicemail.id,
                                    positionMs = playback.positionMs,
                                    durationMs = playback.durationMs,
                                    onToggle = { viewModel.togglePlayback(voicemail) },
                                    onSeek = { viewModel.seekTo(it) },
                                    onToggleRead = { viewModel.markAsRead(voicemail.id, !voicemail.isRead) },
                                    onDelete = { viewModel.delete(voicemail.id) }
                                )
                                RivoDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicemailRow(
    voicemail: Voicemail,
    isCurrent: Boolean,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val displayName = voicemail.contactName
        ?: voicemail.number?.takeIf { it.isNotBlank() }?.let { formatPhoneNumber(it) }
        ?: stringResource(R.string.label_unknown)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RivoAvatar(
                name = displayName,
                photoUri = voicemail.photoUri,
                icon = if (voicemail.contactName == null) Icons.Outlined.Voicemail else null,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (voicemail.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildRowSubtitle(context, voicemail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (voicemail.hasContent) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.voicemail_pause else R.string.voicemail_play
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.voicemail_more_options)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (voicemail.isRead) R.string.voicemail_mark_unread
                                    else R.string.voicemail_mark_read
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (voicemail.isRead) Icons.Outlined.MarkEmailUnread
                                else Icons.Outlined.MarkEmailRead,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleRead()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }

        if (isCurrent && durationMs > 0) {
            Slider(
                value = positionMs.coerceIn(0, durationMs).toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun buildRowSubtitle(
    context: android.content.Context,
    voicemail: Voicemail
): String {
    val date = formatDate(context, voicemail.date)
    return if (voicemail.durationSeconds > 0) {
        "$date  ·  ${formatDuration(voicemail.durationSeconds.toLong())}"
    } else {
        date
    }
}

@Composable
private fun VoicemailEmptyState() {
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
                imageVector = Icons.Outlined.Voicemail,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.voicemail_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.voicemail_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}
