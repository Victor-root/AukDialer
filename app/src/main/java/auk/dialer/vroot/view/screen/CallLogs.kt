package auk.dialer.vroot.view.screen

import android.content.Context
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.CallLogViewModel
import auk.dialer.vroot.controller.util.formatDateHeader
import auk.dialer.vroot.controller.util.makeCall
import auk.dialer.vroot.controller.util.formatPhoneNumber
import auk.dialer.vroot.controller.util.normalizePhoneNumber
import auk.dialer.vroot.controller.util.areNumbersEqual
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.modal.data.CallLogFilter
import auk.dialer.vroot.modal.data.CallLogEntry
import auk.dialer.vroot.modal.data.displayLabel
import auk.dialer.vroot.view.components.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallLogFullScreen(
    navigator: DestinationsNavigator,
    contactId: String? = null,
    phoneNumber: String? = null
) {
    val viewModel: CallLogViewModel = koinActivityViewModel()
    val prefs: PreferenceManager = koinInject()

    LaunchedEffect(Unit) {
        viewModel.fetchLogs()
    }

    val allLogs by viewModel.allCallLogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val settingsState by prefs.settingsChanged.collectAsState(initial = 0)

    var selectedEntries by remember { mutableStateOf(setOf<CallLogEntry>()) }
    
    BackHandler(enabled = selectedEntries.isNotEmpty()) {
        selectedEntries = emptySet()
    }
    
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }

    var showSimPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var pendingContactId by remember { mutableStateOf<String?>(null) }

    val filteredLogsByContact = remember(allLogs, contactId, phoneNumber) {
        if (contactId == null && phoneNumber == null) allLogs
        else allLogs.filter { log ->
            (contactId != null && contactId != "null" && log.contactId == contactId) || 
            (phoneNumber != null && log.number.replace(" ", "").contains(phoneNumber.replace(" ", "")))
        }
    }

    val contactName = remember(filteredLogsByContact) {
        filteredLogsByContact.firstOrNull { it.name != null && it.name != it.number }?.name ?: (if (phoneNumber != null) formatPhoneNumber(phoneNumber) else null)
    }

    if (showSimPicker && pendingNumber != null) {
        SimPickerDialog(
            onDismissRequest = { showSimPicker = false },
            onSimSelected = { handle ->
                makeCall(context, pendingNumber!!, handle, contactId = pendingContactId)
                showSimPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = selectedEntries.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "TopBarTransition"
            ) { isSelecting ->
                if (!isSelecting) {
                    TopAppBar(
                        title = {
                            Text(
                                if (contactName != null) stringResource(R.string.call_history_with_contact, contactName) else stringResource(R.string.call_history_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { navigator.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                        colors = aukAccentTopAppBarColors()
                    )
                } else {
                    BatchCallLogActionBar(
                        selectedCount = selectedEntries.size,
                        onClearSelection = { selectedEntries = emptySet() },
                        onDelete = {
                            val allIdsToDelete = selectedEntries.flatMap { it.ids }
                            viewModel.deleteCallLogsByIds(allIdsToDelete)
                            selectedEntries = emptySet()
                        },
                        onBlock = {
                            selectedEntries.forEach { entry ->
                                auk.dialer.vroot.controller.util.BlockedNumbersManager.block(context, entry.number)
                            }
                            selectedEntries = emptySet()
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AukAccentFilterBar {
                    items(CallLogFilter.entries) { filter ->
                        AukFilterChip(filter.displayLabel(), selectedFilter == filter, {
                            _ ->
                            viewModel.setFilter(filter)
                        }, isAllFilter = filter == CallLogFilter.All, onAccentBar = true)
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AukLoadingIndicatorView()
                    }
                } else if (filteredLogsByContact.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.History, 
                                contentDescription = null, 
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.call_log_no_history_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val finalLogs = when (selectedFilter) {
                        CallLogFilter.All -> filteredLogsByContact
                        CallLogFilter.Missed -> filteredLogsByContact.filter { it.type == CallLog.Calls.MISSED_TYPE }
                        CallLogFilter.Incoming -> filteredLogsByContact.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                        CallLogFilter.Outgoing -> filteredLogsByContact.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                        CallLogFilter.Contacts -> filteredLogsByContact.filter { it.name != null && it.name != it.number }
                    }

                    if (finalLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.call_log_no_filter_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val groupedLogs = finalLogs.groupBy { formatDateHeader(context, it.date) }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            groupedLogs.forEach { (header, logsInGroup) ->
                                item {
                                    AukSectionHeader(title = header)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AukExpressiveCard {
                                        logsInGroup.forEachIndexed { index, lg ->
                                            CallLogTileSimple(
                                                log = lg,
                                                onClick = {
                                                    if (selectedEntries.isNotEmpty()) {
                                                        selectedEntries = if (selectedEntries.any { it.id == lg.id }) {
                                                            selectedEntries.filter { it.id != lg.id }.toSet()
                                                        } else {
                                                            selectedEntries + lg
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (selectedEntries.none { it.id == lg.id }) {
                                                        selectedEntries = selectedEntries + lg
                                                    }
                                                },
                                                onCallClick = {
                                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                        context,
                                                        android.Manifest.permission.READ_PHONE_STATE
                                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                                    val targetContactId = lg.contactId ?: contactId

                                                    if (hasPermission) {
                                                        val accounts = telecomManager.callCapablePhoneAccounts
                                                        if (accounts.size > 1) {
                                                            pendingNumber = lg.number
                                                            pendingContactId = targetContactId
                                                            showSimPicker = true
                                                        } else {
                                                            makeCall(context, lg.number, contactId = targetContactId)
                                                        }
                                                    } else {
                                                        makeCall(context, lg.number, contactId = targetContactId)
                                                    }
                                                },
                                                selected = selectedEntries.any { it.id == lg.id }
                                            )
                                            
                                            if (index < logsInGroup.size - 1) {
                                                AukDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                }
            }

            ScrollToTopButton(
                visible = showButton && selectedEntries.isEmpty(),
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            )
        }
    }
}
