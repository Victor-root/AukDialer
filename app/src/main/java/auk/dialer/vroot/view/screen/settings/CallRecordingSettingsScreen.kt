package auk.dialer.vroot.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.CallRecorder
import auk.dialer.vroot.controller.ShizukuBridge
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukListItem
import auk.dialer.vroot.view.components.AukSwitchListItem
import auk.dialer.vroot.view.components.AukTopBarTitle
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import auk.dialer.vroot.view.theme.aukExceptBottom
import auk.dialer.vroot.view.theme.aukScrollContentPadding
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CallRecordingsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

/** Call recording on its own: the switches, Shizuku for capturing both sides, and the saved recordings. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Destination<RootGraph>
@Composable
fun CallRecordingSettingsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    var callRecording by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_CALL_RECORDING, false)) }
    var autoRecord by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_CALL_RECORDING_AUTO, false)) }

    val recordAudioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val shizukuStatus by ShizukuBridge.status.collectAsState()
    LaunchedEffect(Unit) { ShizukuBridge.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { AukTopBarTitle(stringResource(R.string.settings_call_recording)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding.aukExceptBottom()),
            contentPadding = aukScrollContentPadding(16.dp, padding),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                AukExpressiveCard {
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_call_recording),
                        supporting = stringResource(R.string.settings_call_recording_supporting),
                        leadingIcon = Icons.Outlined.FiberManualRecord,
                        checked = callRecording,
                        onCheckedChange = { enabled ->
                            if (enabled && !CallRecorder.hasPermission(context)) {
                                recordAudioPermission.launchPermissionRequest()
                            }
                            callRecording = enabled
                            prefs.setBoolean(PreferenceManager.KEY_CALL_RECORDING, enabled)
                            if (!enabled) {
                                autoRecord = false
                                prefs.setBoolean(PreferenceManager.KEY_CALL_RECORDING_AUTO, false)
                            }
                        }
                    )
                    if (callRecording) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_call_recording_auto),
                            supporting = stringResource(R.string.settings_call_recording_auto_supporting),
                            leadingIcon = Icons.Outlined.Autorenew,
                            checked = autoRecord,
                            onCheckedChange = {
                                if (it && !CallRecorder.hasPermission(context)) {
                                    recordAudioPermission.launchPermissionRequest()
                                }
                                autoRecord = it
                                prefs.setBoolean(PreferenceManager.KEY_CALL_RECORDING_AUTO, it)
                            }
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        AukListItem(
                            headline = stringResource(R.string.settings_call_recording_shizuku),
                            supporting = stringResource(
                                when (shizukuStatus) {
                                    ShizukuBridge.Status.Unavailable -> R.string.settings_call_recording_shizuku_unavailable
                                    ShizukuBridge.Status.NeedsPermission -> R.string.settings_call_recording_shizuku_needs_permission
                                    ShizukuBridge.Status.Denied -> R.string.settings_call_recording_shizuku_denied
                                    ShizukuBridge.Status.Ready -> R.string.settings_call_recording_shizuku_ready
                                }
                            ),
                            leadingIcon = Icons.Outlined.Security,
                            onClick = {
                                if (shizukuStatus == ShizukuBridge.Status.NeedsPermission) {
                                    ShizukuBridge.requestPermission()
                                } else {
                                    ShizukuBridge.openShizuku(context)
                                }
                            }
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        AukListItem(
                            headline = stringResource(R.string.call_recordings_title),
                            supporting = stringResource(R.string.call_recordings_supporting),
                            leadingIcon = Icons.Outlined.LibraryMusic,
                            onClick = { navigator.navigate(CallRecordingsScreenDestination) }
                        )
                    }
                }
            }
        }
    }
}
