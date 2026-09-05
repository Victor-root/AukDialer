package auk.dialer.vroot.view.screen.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PhoneMissed
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukDivider
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukListItem
import auk.dialer.vroot.view.components.AukSectionHeader
import auk.dialer.vroot.view.components.AukSelectListItem
import auk.dialer.vroot.view.components.AukSwitchListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SoundVibrationScreen(
    navigator: DestinationsNavigator
) {
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current
    
    var dtmfTone by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, true)) }
    var dialpadVibration by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIALPAD_VIBRATION, true)) }
    var vibrateOnAnswer by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_VIBRATE_ON_ANSWER, true)) }
    var vibrateOnHangup by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_VIBRATE_ON_HANGUP, false)) }
    val settingsState by prefs.settingsChanged.collectAsState()

    var hapticListScroll by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_HAPTIC_LIST_SCROLL, false)) }
    var missedCallNotifications by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_MISSED_CALL_NOTIFICATIONS, true)) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { Text(stringResource(R.string.settings_sound_title), fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                AukExpressiveCard {
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_sound_dtmf_tone),
                        supporting = stringResource(R.string.settings_sound_dtmf_tone_supporting),
                        leadingIcon = Icons.Outlined.Audiotrack,
                        checked = dtmfTone,
                        onCheckedChange = {
                            dtmfTone = it
                            prefs.setBoolean(PreferenceManager.KEY_DTMF_TONE, it)
                        }
                    )
                    AukDivider(Modifier.padding(horizontal = 16.dp))
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_sound_dialpad_vibration),
                        supporting = stringResource(R.string.settings_sound_dialpad_vibration_supporting),
                        leadingIcon = Icons.Outlined.Vibration,
                        checked = dialpadVibration,
                        onCheckedChange = {
                            dialpadVibration = it
                            prefs.setBoolean(PreferenceManager.KEY_DIALPAD_VIBRATION, it)
                        }
                    )
                }
            }

            item {
                AukExpressiveCard {
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_sound_vibrate_on_answer),
                        supporting = stringResource(R.string.settings_sound_vibrate_on_answer_supporting),
                        leadingIcon = Icons.Outlined.Vibration,
                        checked = vibrateOnAnswer,
                        onCheckedChange = {
                            vibrateOnAnswer = it
                            prefs.setBoolean(PreferenceManager.KEY_VIBRATE_ON_ANSWER, it)
                        }
                    )
                    AukDivider(Modifier.padding(horizontal = 16.dp))
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_sound_vibrate_on_hangup),
                        supporting = stringResource(R.string.settings_sound_vibrate_on_hangup_supporting),
                        leadingIcon = Icons.Outlined.Vibration,
                        checked = vibrateOnHangup,
                        onCheckedChange = {
                            vibrateOnHangup = it
                            prefs.setBoolean(PreferenceManager.KEY_VIBRATE_ON_HANGUP, it)
                        }
                    )
                    AukDivider(Modifier.padding(horizontal = 16.dp))
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_sound_haptic_scroll),
                        supporting = stringResource(R.string.settings_sound_haptic_scroll_supporting),
                        leadingIcon = Icons.Outlined.Gesture,
                        checked = hapticListScroll,
                        onCheckedChange = {
                            hapticListScroll = it
                            prefs.setBoolean(PreferenceManager.KEY_HAPTIC_LIST_SCROLL, it)
                        }
                    )
                }
            }

            item {
                AukExpressiveCard {
                    AukSwitchListItem(
                        headline = stringResource(R.string.settings_sound_missed_call_notifications),
                        supporting = stringResource(R.string.settings_sound_missed_call_notifications_supporting),
                        leadingIcon = Icons.AutoMirrored.Outlined.PhoneMissed,
                        checked = missedCallNotifications,
                        onCheckedChange = {
                            missedCallNotifications = it
                            prefs.setBoolean(PreferenceManager.KEY_MISSED_CALL_NOTIFICATIONS, it)
                        }
                    )
                    AukDivider(Modifier.padding(horizontal = 16.dp))
                    AukListItem(
                        headline = stringResource(R.string.settings_sound_ringtone_settings),
                        supporting = stringResource(R.string.settings_sound_ringtone_settings_supporting),
                        leadingIcon = Icons.Outlined.MusicNote,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                        }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
