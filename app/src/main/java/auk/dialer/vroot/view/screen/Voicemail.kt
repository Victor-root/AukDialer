package auk.dialer.vroot.view.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.VoicemailViewModel
import auk.dialer.vroot.controller.util.formatDateHeader
import auk.dialer.vroot.controller.util.formatDuration
import auk.dialer.vroot.controller.util.formatPhoneNumber
import auk.dialer.vroot.controller.util.formatTime
import auk.dialer.vroot.controller.util.getDefaultDialerIntent
import auk.dialer.vroot.debug.VoicemailDebugMenu
import auk.dialer.vroot.modal.data.Voicemail
import auk.dialer.vroot.modal.data.VoicemailStatus
import auk.dialer.vroot.view.components.AudioMessageRow
import auk.dialer.vroot.view.components.AudioRowAction
import auk.dialer.vroot.view.components.AukExpressiveCardSegment
import auk.dialer.vroot.view.components.AukLoadingIndicatorView
import auk.dialer.vroot.view.components.AukPullToRefreshIndicator
import auk.dialer.vroot.view.components.AukSectionHeader
import auk.dialer.vroot.view.components.PermissionDeniedView
import auk.dialer.vroot.view.components.SelectionDeleteActionBar
import auk.dialer.vroot.view.components.TopBar
import auk.dialer.vroot.view.components.rememberHeaderSearchState
import auk.dialer.vroot.view.components.navigationTabLabel
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.screen.transitions.NoTransitions
import auk.dialer.vroot.view.theme.aukExceptBottom
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.VoicemailScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.viewmodel.koinActivityViewModel

@Destination<RootGraph>(style = NoTransitions::class)
@Composable
fun VoicemailListScreen(navigator: DestinationsNavigator) {
    VoicemailScreenContent(navigator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreenContent(
    navigator: DestinationsNavigator,
    showTopBar: Boolean = true,
    // False while another tab of the main screen is shown: the pager keeps this one composed.
    isShown: Boolean = true,
    // Hosted by the main screen, which shows the selection header in its own collapsing header.
    onSelectionStateChange: ((Boolean, (@Composable () -> Unit)?) -> Unit)? = null
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

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelecting = selectedIds.isNotEmpty()
    // A message deleted or gone from the mailbox leaves the selection.
    LaunchedEffect(voicemails) {
        val present = voicemails.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in present }.toSet()
    }
    BackHandler(enabled = isSelecting) { selectedIds = emptySet() }
    val selectionActionBar: @Composable () -> Unit = {
        SelectionDeleteActionBar(
            selectedCount = selectedIds.size,
            onClearSelection = { selectedIds = emptySet() },
            confirmTitle = pluralStringResource(R.plurals.voicemail_delete_selected_title, selectedIds.size, selectedIds.size),
            confirmMessage = stringResource(R.string.voicemail_delete_selected_message),
            onDelete = {
                viewModel.delete(selectedIds)
                selectedIds = emptySet()
            }
        )
    }
    LaunchedEffect(isSelecting, selectedIds.size) {
        onSelectionStateChange?.invoke(isSelecting, if (isSelecting) selectionActionBar else null)
    }

    var isDefaultDialer by remember { mutableStateOf(viewModel.isDefaultDialer()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val audioUnavailableText = stringResource(R.string.voicemail_audio_unavailable)

    // The list itself is read on ON_RESUME below, which the observer receives as soon as it is added.
    LaunchedEffect(Unit) {
        if (isDefaultDialer) viewModel.registerSmsFilter()
    }

    // Re-read on every return to the screen. The provider does notify us of new
    // rows, but the system freezes backgrounded apps, so a message imported
    // while the phone was locked can land without that notice ever arriving,
    // leaving a stale list in front of someone who just tapped the notification.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Playback stops with the list out of sight (other tab, other screen, app in the background):
    // nothing else on screen could stop it.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    isDefaultDialer = viewModel.isDefaultDialer()
                    if (isDefaultDialer) viewModel.fetchVoicemails()
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.stopPlayback()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPlayback()
        }
    }
    LaunchedEffect(isShown) {
        if (!isShown) viewModel.stopPlayback()
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

    val headerSearch = rememberHeaderSearchState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                if (isSelecting) {
                    selectionActionBar()
                } else {
                    TopBar(navigator, navigationTabLabel(PreferenceManager.TAB_VOICEMAIL), headerSearch)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Debug builds only: the release source set makes this an empty function.
        floatingActionButton = { VoicemailDebugMenu(onChanged = { viewModel.fetchVoicemails() }) },
        // Inside the main screen, its bottom bar already clears the navigation bar.
        contentWindowInsets = if (showTopBar) {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        } else {
            WindowInsets(0)
        }
    ) { padding ->
        HeaderSearchContent(headerSearch, navigator, Modifier.padding(padding.aukExceptBottom()).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !isDefaultDialer -> PermissionDeniedView(
                        icon = Icons.Outlined.Voicemail,
                        title = stringResource(R.string.voicemail_needs_default_dialer_title),
                        description = stringResource(R.string.voicemail_needs_default_dialer_description),
                        buttonText = stringResource(R.string.default_dialer_set_as_default),
                        onGrantClick = { context.startActivity(getDefaultDialerIntent(context)) }
                    )

                    isLoading -> AukLoadingIndicatorView(modifier = Modifier.fillMaxSize())

                    else -> {
                        val pullToRefreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = isSyncing,
                            onRefresh = { viewModel.syncNow() },
                            modifier = Modifier.fillMaxSize(),
                            state = pullToRefreshState,
                            indicator = {
                                AukPullToRefreshIndicator(state = pullToRefreshState, isRefreshing = isSyncing)
                            }
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
                                    contentPadding = PaddingValues(
                                        top = 8.dp,
                                        bottom = 16.dp + padding.calculateBottomPadding()
                                    )
                                ) {
                                    groupedVoicemails.forEach { (header, group) ->
                                        item(key = "header:$header", contentType = "header") {
                                            AukSectionHeader(title = header)
                                        }
                                        itemsIndexed(
                                            group,
                                            key = { _, voicemail -> voicemail.id },
                                            contentType = { _, _ -> "voicemail" }
                                        ) { index, voicemail ->
                                            val isLast = index == group.lastIndex
                                            AukExpressiveCardSegment(
                                                isFirst = index == 0,
                                                isLast = isLast,
                                                isCompact = true,
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp)
                                                    .padding(bottom = if (isLast) 8.dp else 0.dp)
                                            ) {
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
                                                    onDelete = { viewModel.delete(setOf(voicemail.id)) },
                                                    selected = voicemail.id in selectedIds,
                                                    selectionMode = isSelecting,
                                                    onToggleSelection = {
                                                        selectedIds = if (voicemail.id in selectedIds) {
                                                            selectedIds - voicemail.id
                                                        } else {
                                                            selectedIds + voicemail.id
                                                        }
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
    onDelete: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit
) {
    val context = LocalContext.current
    val displayName = voicemail.contactName
        ?: voicemail.number?.takeIf { it.isNotBlank() }?.let { formatPhoneNumber(it) }
        ?: stringResource(R.string.label_unknown)

    key(voicemail.id) {
        AudioMessageRow(
            title = displayName,
            subtitle = buildRowSubtitle(context, voicemail),
            avatarName = displayName,
            photoUri = voicemail.photoUri,
            avatarIcon = if (voicemail.contactName == null) Icons.Outlined.Voicemail else null,
            emphasized = !voicemail.isRead,
            canPlay = voicemail.hasContent,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            isSpeakerOn = isSpeakerOn,
            onToggle = onToggle,
            onSeek = onSeek,
            onToggleSpeaker = onToggleSpeaker,
            actions = listOf(
                AudioRowAction(
                    label = stringResource(
                        if (voicemail.isRead) R.string.voicemail_mark_unread else R.string.voicemail_mark_read
                    ),
                    icon = if (voicemail.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
                    onClick = onToggleRead
                ),
                AudioRowAction(
                    label = stringResource(R.string.action_delete),
                    icon = Icons.Outlined.Delete,
                    onClick = onDelete
                )
            ),
            selected = selected,
            selectionMode = selectionMode,
            onToggleSelection = onToggleSelection
        )
    }
}

/**
 * Mirrors the call log's supporting line: SIM, then time. The day is omitted
 * because the group this row sits in already carries it.
 */
private fun buildRowSubtitle(
    context: android.content.Context,
    voicemail: Voicemail
): String = buildString {
    if (voicemail.simLabel != null) {
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
