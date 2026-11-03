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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.view.components.AukTopBarTitle
import auk.dialer.vroot.controller.util.AukHaptics
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukDialog
import auk.dialer.vroot.view.components.AukDialogAction
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
import kotlin.math.roundToInt
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import auk.dialer.vroot.view.theme.aukExceptBottom
import auk.dialer.vroot.view.theme.aukScrollContentPadding

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
    var showIntensityDialog by remember { mutableStateOf(false) }
    var missedCallNotifications by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_MISSED_CALL_NOTIFICATIONS, true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { AukTopBarTitle(stringResource(R.string.settings_sound_title)) },
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
                    AukDivider(Modifier.padding(horizontal = 16.dp))
                    AukListItem(
                        headline = stringResource(R.string.settings_sound_vibration_intensity),
                        supporting = stringResource(R.string.settings_sound_vibration_intensity_supporting),
                        leadingIcon = Icons.Outlined.Tune,
                        onClick = { showIntensityDialog = true }
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

    if (showIntensityDialog) {
        VibrationIntensityDialog(prefs = prefs, onDismissRequest = { showIntensityDialog = false })
    }
}

/**
 * One slider per vibration, and one above them that sets them all at once. Letting go of a slider
 * plays its vibration at the new strength, so it can be felt rather than guessed.
 */
@Composable
private fun VibrationIntensityDialog(prefs: PreferenceManager, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val intensities = remember {
        mutableStateMapOf<AukHaptics.Vibration, Int>().apply {
            AukHaptics.Vibration.entries.forEach { put(it, AukHaptics.intensityOf(prefs, it)) }
        }
    }
    var all by remember {
        mutableIntStateOf(intensities.values.average().roundToInt())
    }

    AukDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_sound_vibration_intensity),
        icon = Icons.Outlined.Vibration,
        confirmAction = AukDialogAction(
            label = stringResource(R.string.action_done),
            onClick = onDismissRequest
        )
    ) {
        IntensitySlider(
            label = stringResource(R.string.settings_sound_vibration_intensity_all),
            value = all,
            onValueChange = { value ->
                all = value
                AukHaptics.Vibration.entries.forEach { intensities[it] = value }
            },
            onValueChangeFinished = {
                AukHaptics.Vibration.entries.forEach { prefs.setInt(it.intensityKey, all) }
                AukHaptics.play(context, AukHaptics.Vibration.Dialpad, all)
            }
        )
        AukHaptics.Vibration.entries.forEach { vibration ->
            val value = intensities.getValue(vibration)
            IntensitySlider(
                label = stringResource(vibration.labelRes()),
                value = value,
                onValueChange = { intensities[vibration] = it },
                onValueChangeFinished = {
                    prefs.setInt(vibration.intensityKey, intensities.getValue(vibration))
                    AukHaptics.play(context, vibration, intensities.getValue(vibration))
                }
            )
        }
    }
}

@Composable
private fun IntensitySlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.settings_sound_vibration_intensity_value, value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = IntensityRange,
            steps = IntensitySteps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun AukHaptics.Vibration.labelRes(): Int = when (this) {
    AukHaptics.Vibration.Dialpad -> R.string.settings_sound_dialpad_vibration
    AukHaptics.Vibration.Answer -> R.string.settings_sound_vibrate_on_answer
    AukHaptics.Vibration.Hangup -> R.string.settings_sound_vibrate_on_hangup
    AukHaptics.Vibration.Scroll -> R.string.settings_sound_haptic_scroll
}

// From a tenth of full strength to full, by tenths.
private val IntensityRange = 10f..100f
private const val IntensitySteps = 8
