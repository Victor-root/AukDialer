package com.grinch.rivo4.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.grinch.rivo4.controller.util.PreferenceManager
import com.grinch.rivo4.controller.util.formatDateHeader
import com.grinch.rivo4.controller.util.formatDuration
import com.grinch.rivo4.controller.util.formatPhoneNumber
import com.grinch.rivo4.controller.util.formatTime
import com.grinch.rivo4.controller.util.getDefaultDialerIntent
import com.grinch.rivo4.debug.VoicemailDebugMenu
import com.grinch.rivo4.modal.data.Voicemail
import com.grinch.rivo4.modal.data.VoicemailStatus
import com.grinch.rivo4.view.components.BottomBar
import com.grinch.rivo4.view.components.PermissionDeniedView
import com.grinch.rivo4.view.components.RivoAvatar
import com.grinch.rivo4.view.components.RivoDivider
import com.grinch.rivo4.view.components.RivoExpressiveCard
import com.grinch.rivo4.view.components.RivoSectionHeader
import com.grinch.rivo4.view.screen.transitions.NoTransitions
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.VoicemailScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
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
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val status by viewModel.status.collectAsState()
    val syncFailed by viewModel.syncFailed.collectAsState()
    val syncFoundNothing by viewModel.syncFoundNothing.collectAsState()

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

    // Re-read on every return to the screen. The provider does notify us of new
    // rows, but the system freezes backgrounded apps, so a message imported
    // while the phone was locked can land without that notice ever arriving,
    // leaving a stale list in front of someone who just tapped the notification.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDefaultDialer = viewModel.isDefaultDialer()
                if (isDefaultDialer) viewModel.fetchVoicemails()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    val syncFailedText = stringResource(statusTitle(status))
    LaunchedEffect(syncFailed) {
        if (syncFailed) {
            snackbarHostState.showSnackbar(syncFailedText)
            viewModel.consumeSyncFailed()
        }
    }

    val syncFoundNothingText = stringResource(R.string.voicemail_sync_no_new)
    LaunchedEffect(syncFoundNothing) {
        if (syncFoundNothing) {
            snackbarHostState.showSnackbar(syncFoundNothingText)
            viewModel.consumeSyncFoundNothing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.voicemail_title), fontWeight = FontWeight.Bold)
                },
                actions = {
                    VoicemailDebugMenu(onChanged = { viewModel.fetchVoicemails() })
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
                        VoicemailStatusState(
                            status = status,
                            onOpenSettings = { navigator.navigate(VoicemailScreenDestination) }
                        )
                    } else {
                        val groupedVoicemails = remember(voicemails) {
                            voicemails.groupBy { formatDateHeader(context, it.date) }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            groupedVoicemails.forEach { (header, group) ->
                                item(key = header) {
                                    RivoSectionHeader(title = header)
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        RivoExpressiveCard {
                                            group.forEachIndexed { index, voicemail ->
                                                VoicemailRow(
                                                    voicemail = voicemail,
                                                    isCurrent = playback.playingId == voicemail.id,
                                                    isPlaying = playback.isPlaying && playback.playingId == voicemail.id,
                                                    positionMs = playback.positionMs,
                                                    durationMs = playback.durationMs,
                                                    isSpeakerOn = isSpeakerOn,
                                                    onToggle = { viewModel.togglePlayback(voicemail) },
                                                    onSeek = { viewModel.seekTo(it) },
                                                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                                                    onToggleRead = {
                                                        viewModel.markAsRead(voicemail.id, !voicemail.isRead)
                                                    },
                                                    onDelete = { viewModel.delete(voicemail.id) }
                                                )
                                                if (index < group.size - 1) {
                                                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
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
    isSpeakerOn: Boolean,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val showSim = prefs.getBoolean(PreferenceManager.KEY_SHOW_SIM_ICON_HISTORY, true)
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
                    text = buildRowSubtitle(context, voicemail, showSim),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (voicemail.hasContent) {
                IconButton(onClick = onToggle) {
                    Icon(
                        // Rounded variants: same glyphs with softened corners.
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.voicemail_pause else R.string.voicemail_play
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        // A triangle and two bars cover less of their box than
                        // the overflow dots, so at equal size they read smaller.
                        modifier = Modifier.size(28.dp)
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
            // While dragging, follow the finger rather than the player: the
            // player only reports the new position once the seek lands, which
            // otherwise makes the handle snap back under the finger.
            var scrubPositionMs by remember(voicemail.id) { mutableStateOf<Float?>(null) }
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
                            if (isSpeakerOn) R.string.voicemail_route_speaker
                            else R.string.voicemail_route_earpiece
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

/**
 * Mirrors the call log's supporting line: SIM, then time. The day is omitted
 * because the group this row sits in already carries it.
 */
private fun buildRowSubtitle(
    context: android.content.Context,
    voicemail: Voicemail,
    showSim: Boolean
): String = buildString {
    if (showSim && voicemail.simLabel != null) {
        append(voicemail.simLabel)
        append(" • ")
    }
    append(formatTime(context, voicemail.date))
    if (voicemail.durationSeconds > 0) {
        append("  ·  ")
        append(formatDuration(voicemail.durationSeconds.toLong()))
    }
}

/**
 * Fills the empty list with what is actually going on. An empty mailbox and a
 * carrier that never answered look identical otherwise, and only one of them is
 * something the user can act on.
 */
@Composable
private fun VoicemailStatusState(
    status: VoicemailStatus,
    onOpenSettings: () -> Unit
) {
    val title = stringResource(statusTitle(status))
    val description = stringResource(statusDescription(status))
    val offersSettings = status == VoicemailStatus.NotProvisioned ||
        status == VoicemailStatus.AuthenticationRejected

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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
        if (offersSettings) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.voicemail_status_open_settings))
            }
        }
    }
}

private fun statusTitle(status: VoicemailStatus): Int = when (status) {
    VoicemailStatus.CarrierUnsupported -> R.string.voicemail_status_unsupported_title
    VoicemailStatus.NotProvisioned -> R.string.voicemail_status_not_provisioned_title
    VoicemailStatus.ActivationPending -> R.string.voicemail_status_activation_title
    VoicemailStatus.ServiceRefused -> R.string.voicemail_status_refused_title
    VoicemailStatus.AuthenticationRejected -> R.string.voicemail_status_auth_title
    VoicemailStatus.ServerUnreachable -> R.string.voicemail_status_unreachable_title
    // NotDefaultDialer never reaches here: the screen shows its own prompt.
    VoicemailStatus.Ready, VoicemailStatus.NotDefaultDialer -> R.string.voicemail_empty_title
}

private fun statusDescription(status: VoicemailStatus): Int = when (status) {
    VoicemailStatus.CarrierUnsupported -> R.string.voicemail_status_unsupported_description
    VoicemailStatus.NotProvisioned -> R.string.voicemail_status_not_provisioned_description
    VoicemailStatus.ActivationPending -> R.string.voicemail_status_activation_description
    VoicemailStatus.ServiceRefused -> R.string.voicemail_status_refused_description
    VoicemailStatus.AuthenticationRejected -> R.string.voicemail_status_auth_description
    VoicemailStatus.ServerUnreachable -> R.string.voicemail_status_unreachable_description
    VoicemailStatus.Ready, VoicemailStatus.NotDefaultDialer -> R.string.voicemail_empty_description
}
