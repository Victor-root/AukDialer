package auk.dialer.vroot.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.BuildConfig
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.DebugCallSimulator
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingsScreen(
    navigator: DestinationsNavigator
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                AukExpressiveCard {
                    AukListItem(
                        headline = stringResource(R.string.settings_interface_headline),
                        supporting = stringResource(R.string.settings_interface_supporting),
                        leadingIcon = Icons.Outlined.Palette,
                        onClick = { navigator.navigate(InterfaceScreenDestination) }
                    )
                    AukListItem(
                        headline = stringResource(R.string.settings_sound_vibration_headline),
                        supporting = stringResource(R.string.settings_sound_vibration_supporting),
                        leadingIcon = Icons.AutoMirrored.Outlined.VolumeUp,
                        onClick = { navigator.navigate(SoundVibrationScreenDestination) }
                    )
                }
            }

            item {
                AukExpressiveCard {
                    AukListItem(
                        headline = stringResource(R.string.settings_call_settings_headline),
                        supporting = stringResource(R.string.settings_call_settings_supporting),
                        leadingIcon = Icons.Outlined.SimCard,
                        onClick = { navigator.navigate(CallAccountsScreenDestination) }
                    )
                    AukListItem(
                        headline = stringResource(R.string.settings_blocked_numbers_headline),
                        supporting = stringResource(R.string.settings_blocked_numbers_supporting),
                        leadingIcon = Icons.Outlined.Block,
                        onClick = { navigator.navigate(BlockedNumbersScreenDestination) }
                    )
                }
            }

            item {
                AukExpressiveCard {
                    AukListItem(
                        headline = stringResource(R.string.settings_backup_restore_headline),
                        supporting = stringResource(R.string.settings_backup_restore_supporting),
                        leadingIcon = Icons.Outlined.Backup,
                        onClick = { navigator.navigate(BackupRestoreScreenDestination) }
                    )
                    AukListItem(
                        headline = stringResource(R.string.settings_manage_private_contacts),
                        supporting = stringResource(R.string.settings_manage_private_contacts_supporting),
                        leadingIcon = Icons.Outlined.Lock,
                        onClick = { navigator.navigate(PrivateContactsScreenDestination) }
                    )
                    AukListItem(
                        headline = stringResource(R.string.settings_manage_visibility),
                        supporting = stringResource(R.string.settings_manage_visibility_supporting),
                        leadingIcon = Icons.Outlined.Visibility,
                        onClick = { navigator.navigate(ContactVisibilityScreenDestination) }
                    )
                }
            }

            item {
                AukExpressiveCard {
                    AukListItem(
                        headline = stringResource(R.string.settings_about_headline),
                        supporting = stringResource(R.string.settings_about_supporting),
                        leadingIcon = Icons.Outlined.Info,
                        onClick = { navigator.navigate(AboutScreenDestination) }
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    AukExpressiveCard {
                        AukListItem(
                            headline = stringResource(R.string.settings_debug_simulate_call_headline),
                            supporting = stringResource(R.string.settings_debug_simulate_call_supporting),
                            leadingIcon = Icons.Outlined.BugReport,
                            onClick = { DebugCallSimulator.simulateIncomingCall(context) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}
