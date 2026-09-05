package auk.dialer.vroot.view.screen.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.LauncherIconManager
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukColorSelectListItem
import auk.dialer.vroot.view.components.AukDialog
import auk.dialer.vroot.view.components.AukDialogAction
import auk.dialer.vroot.view.components.AukDivider
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukListItem
import auk.dialer.vroot.view.components.AukListItemDefaults
import auk.dialer.vroot.view.components.AukSelectListItem
import auk.dialer.vroot.view.components.AukSwitchListItem
import auk.dialer.vroot.view.components.ScrollToTopButton
import auk.dialer.vroot.view.theme.CUSTOM_PRIMARY_COLOR_UNSET
import auk.dialer.vroot.view.theme.KEY_CUSTOM_PRIMARY_COLOR
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BottomNavScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors

private val RoundnessRange = 1f..32f
private const val RoundnessSteps = 7

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun InterfaceScreen(
    navigator: DestinationsNavigator
) {
    val prefs = koinInject<PreferenceManager>()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val showButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }

    var dynamicColors by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, true)) }
    var amoledMode by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AMOLED_MODE, false)) }
    var edgeToEdge by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_EDGE_TO_EDGE, false)) }
    var defaultBottomBar by remember { mutableStateOf(prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, PreferenceManager.TAB_RECENTS)) }
    var mergeFavorites by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_MERGE_FAVORITES_RECENTS, true)) }
    var colorfulAvatars by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, true)) }
    var gradientAvatars by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_GRADIENT_AVATARS, false)) }
    var showPicture by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true)) }
    var iconOnlyNav by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_ICON_ONLY_NAV, false)) }
    var transitionStyle by remember { mutableStateOf(prefs.getInt(PreferenceManager.KEY_TRANSITION_STYLE, 0)) }
    var customPrimaryColor by remember { mutableStateOf(prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, CUSTOM_PRIMARY_COLOR_UNSET)) }
    var avatarShape by remember { mutableStateOf(prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 0)) }
    var cardRoundness by remember { mutableStateOf(prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, 28)) }
    var showRoundnessDialog by remember { mutableStateOf(false) }

    // One palette for both pickers: the launcher aliases already pin these
    // twenty Material colours, and the theme has no reason to offer others.
    val paletteColors = remember { LauncherIconManager.ICONS.map { Color(it.first) } }

    val launcherIconManager = koinInject<LauncherIconManager>()
    // Read from the package manager, not from the preference: only the enabled
    // alias says which icon is really on screen.
    var appIconColor by remember { mutableStateOf(launcherIconManager.currentColor()) }
    var pendingIconColor by remember { mutableStateOf<Int?>(null) }

    val restartRequiredMessage = stringResource(R.string.settings_interface_restart_required)
    val restartActionLabel = stringResource(R.string.settings_interface_restart_action)

    fun showRestartPrompt() {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = restartRequiredMessage,
                actionLabel = restartActionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                (context as? Activity)?.recreate()
            }
        }
    }

    val avatarShapeOptions = listOf(
        stringResource(R.string.settings_interface_avatar_shape_squircle) to 0,
        stringResource(R.string.settings_interface_avatar_shape_circle) to 1,
        stringResource(R.string.settings_interface_avatar_shape_square) to 2,
        stringResource(R.string.settings_interface_avatar_shape_cookie) to 3,
        stringResource(R.string.settings_interface_avatar_shape_clover) to 4,
        stringResource(R.string.settings_interface_avatar_shape_arch) to 5,
        stringResource(R.string.settings_interface_avatar_shape_pill) to 6,
        stringResource(R.string.settings_interface_avatar_shape_gem) to 7,
        stringResource(R.string.settings_interface_avatar_shape_sunny) to 8,
        stringResource(R.string.settings_interface_avatar_shape_heart) to 9,
        stringResource(R.string.settings_interface_avatar_shape_burst) to 10
    )
    val transitionOptions = listOf(
        stringResource(R.string.option_standard) to 0,
        stringResource(R.string.settings_interface_transition_slide) to 1,
        stringResource(R.string.settings_interface_transition_fade) to 2,
        stringResource(R.string.settings_interface_transition_none) to 3
    )
    val bottomBarOptions = listOf(
        stringResource(R.string.nav_recents) to PreferenceManager.TAB_RECENTS,
        stringResource(R.string.nav_favorites) to PreferenceManager.TAB_FAVORITES,
        stringResource(R.string.nav_contacts) to PreferenceManager.TAB_CONTACTS
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { Text(stringResource(R.string.settings_interface_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    AukExpressiveCard(title = stringResource(R.string.settings_group_color)) {
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_material_you),
                            supporting = stringResource(R.string.settings_interface_material_you_supporting),
                            leadingIcon = Icons.Outlined.Palette,
                            checked = dynamicColors,
                            onCheckedChange = {
                                dynamicColors = it
                                prefs.setBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, it)
                                showRestartPrompt()
                            }
                        )

                        if (!dynamicColors) {
                            AukDivider(Modifier.padding(horizontal = 16.dp))
                            AukColorSelectListItem(
                                headline = stringResource(R.string.settings_interface_primary_color),
                                supporting = stringResource(R.string.settings_interface_primary_color_supporting),
                                leadingIcon = Icons.Outlined.ColorLens,
                                colors = paletteColors,
                                selectedColor = paletteColors.firstOrNull { it.toArgb() == customPrimaryColor },
                                onColorSelected = { color ->
                                    customPrimaryColor = color.toArgb()
                                    prefs.setInt(KEY_CUSTOM_PRIMARY_COLOR, customPrimaryColor)
                                    showRestartPrompt()
                                }
                            )
                        }

                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_amoled),
                            supporting = stringResource(R.string.settings_interface_amoled_supporting),
                            leadingIcon = Icons.Outlined.DarkMode,
                            checked = amoledMode,
                            onCheckedChange = {
                                amoledMode = it
                                prefs.setBoolean(PreferenceManager.KEY_AMOLED_MODE, it)
                                showRestartPrompt()
                            }
                        )

                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_edge_to_edge),
                            supporting = stringResource(R.string.settings_interface_edge_to_edge_supporting),
                            leadingIcon = Icons.Outlined.Fullscreen,
                            checked = edgeToEdge,
                            onCheckedChange = {
                                edgeToEdge = it
                                prefs.setBoolean(PreferenceManager.KEY_EDGE_TO_EDGE, it)
                            }
                        )
                    }
                }

                item {
                    AukExpressiveCard(title = stringResource(R.string.settings_group_app_icon)) {
                        AukColorSelectListItem(
                            headline = stringResource(R.string.settings_interface_app_icon_color),
                            supporting = stringResource(R.string.settings_interface_app_icon_color_supporting),
                            leadingIcon = Icons.Outlined.AppShortcut,
                            colors = paletteColors,
                            selectedColor = paletteColors.firstOrNull { it.toArgb() == appIconColor },
                            onColorSelected = { color ->
                                // Picking the icon already in place would close the
                                // app for nothing.
                                val target = color.toArgb()
                                if (launcherIconManager.isChangeNeeded(target)) {
                                    pendingIconColor = target
                                }
                            }
                        )
                    }
                }

                item {
                    AukExpressiveCard(title = stringResource(R.string.settings_group_avatars)) {
                        AukSelectListItem(
                            headline = stringResource(R.string.settings_interface_avatar_shape),
                            supporting = stringResource(R.string.settings_interface_avatar_shape_supporting),
                            leadingIcon = Icons.Outlined.AccountBox,
                            options = avatarShapeOptions,
                            selectedValue = avatarShape,
                            onValueChange = { selected ->
                                avatarShape = selected
                                prefs.setInt(PreferenceManager.KEY_AVATAR_SHAPE, selected)
                            },
                            preview = { shape -> AvatarShapePreview(shape) }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_show_picture),
                            supporting = stringResource(R.string.settings_interface_show_picture_supporting),
                            leadingIcon = Icons.Outlined.AccountCircle,
                            checked = showPicture,
                            onCheckedChange = {
                                showPicture = it
                                prefs.setBoolean(PreferenceManager.KEY_SHOW_PICTURE, it)
                            }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_colorful_avatars),
                            supporting = stringResource(R.string.settings_interface_colorful_avatars_supporting),
                            leadingIcon = Icons.Outlined.Palette,
                            checked = colorfulAvatars,
                            onCheckedChange = {
                                colorfulAvatars = it
                                prefs.setBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, it)
                            }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_gradient_avatars),
                            supporting = stringResource(R.string.settings_interface_gradient_avatars_supporting),
                            leadingIcon = Icons.Outlined.Gradient,
                            checked = gradientAvatars,
                            onCheckedChange = {
                                gradientAvatars = it
                                prefs.setBoolean(PreferenceManager.KEY_GRADIENT_AVATARS, it)
                            }
                        )
                    }
                }

                item {
                    AukExpressiveCard(title = stringResource(R.string.settings_group_shape_motion)) {
                        AukListItem(
                            headline = stringResource(R.string.settings_interface_card_roundness),
                            supporting = stringResource(R.string.settings_interface_card_roundness_supporting),
                            leadingIcon = Icons.Outlined.RoundedCorner,
                            onClick = { showRoundnessDialog = true },
                            trailingContent = {
                                CardRoundnessPreview(cardRoundness)
                                Spacer(modifier = Modifier.width(AukListItemDefaults.TrailingSpacing))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.content_desc_select_option),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(AukListItemDefaults.TrailingIconSize)
                                )
                            }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSelectListItem(
                            headline = stringResource(R.string.settings_interface_transition_animation),
                            supporting = stringResource(R.string.settings_interface_transition_animation_supporting),
                            leadingIcon = Icons.Outlined.Animation,
                            options = transitionOptions,
                            selectedValue = transitionStyle,
                            onValueChange = {
                                transitionStyle = it
                                prefs.setInt(PreferenceManager.KEY_TRANSITION_STYLE, it)
                                showRestartPrompt()
                            },
                            preview = { value -> OptionIconPreview(transitionStyleIcon(value)) }
                        )
                    }
                }

                item {
                    AukExpressiveCard(title = stringResource(R.string.settings_group_navigation)) {
                        AukSelectListItem(
                            headline = stringResource(R.string.settings_interface_default_bottom_bar),
                            supporting = stringResource(R.string.settings_interface_default_bottom_bar_supporting),
                            leadingIcon = Icons.Outlined.SpaceDashboard,
                            options = bottomBarOptions,
                            selectedValue = defaultBottomBar,
                            onValueChange = {
                                defaultBottomBar = it
                                prefs.setInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, it)
                            },
                            preview = { value -> OptionIconPreview(defaultTabIcon(value)) }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukListItem(
                            headline = stringResource(R.string.settings_bottom_nav_title),
                            supporting = stringResource(R.string.settings_bottom_nav_supporting),
                            leadingIcon = Icons.Outlined.SwapHoriz,
                            onClick = { navigator.navigate(BottomNavScreenDestination) }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_icon_only_bar),
                            supporting = stringResource(R.string.settings_interface_icon_only_bar_supporting),
                            leadingIcon = Icons.Outlined.ViewStream,
                            checked = iconOnlyNav,
                            onCheckedChange = {
                                iconOnlyNav = it
                                prefs.setBoolean(PreferenceManager.KEY_ICON_ONLY_NAV, it)
                            }
                        )
                        AukDivider(Modifier.padding(horizontal = 16.dp))
                        AukSwitchListItem(
                            headline = stringResource(R.string.settings_interface_merge_favorites),
                            supporting = stringResource(R.string.settings_interface_merge_favorites_supporting),
                            leadingIcon = Icons.Outlined.Star,
                            checked = mergeFavorites,
                            onCheckedChange = {
                                mergeFavorites = it
                                prefs.setBoolean(PreferenceManager.KEY_MERGE_FAVORITES_RECENTS, it)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }

            if (showRoundnessDialog) {
                CardRoundnessDialog(
                    value = cardRoundness,
                    onValueChange = { cardRoundness = it },
                    onValueChangeFinished = {
                        prefs.setInt(PreferenceManager.KEY_CARD_ROUNDNESS, cardRoundness)
                    },
                    onDismissRequest = { showRoundnessDialog = false }
                )
            }

            // Confirm before acting: applying the icon closes the app, and doing
            // that straight off a colour tap reads like a crash.
            pendingIconColor?.let { target ->
                AukDialog(
                    onDismissRequest = { pendingIconColor = null },
                    title = stringResource(R.string.app_icon_change_title),
                    icon = Icons.Outlined.AppShortcut,
                    supportingText = stringResource(R.string.app_icon_change_message),
                    confirmAction = AukDialogAction(
                        label = stringResource(R.string.app_icon_change_confirm),
                        onClick = {
                            pendingIconColor = null
                            appIconColor = target
                            // finishAffinity, not finish: the task was started from
                            // the alias being disabled, so its base intent stops
                            // resolving and the next launch has to start clean.
                            launcherIconManager.apply(target) {
                                (context as? Activity)?.finishAffinity()
                            }
                        }
                    ),
                    dismissAction = AukDialogAction(
                        label = stringResource(R.string.app_icon_change_cancel),
                        onClick = { pendingIconColor = null }
                    )
                ) {}
            }

            ScrollToTopButton(
                visible = showButton,
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            )
        }
    }
}

@Composable
private fun CardRoundnessDialog(
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AukDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_interface_card_roundness),
        icon = Icons.Outlined.RoundedCorner,
        supportingText = stringResource(R.string.settings_interface_card_roundness_supporting),
        confirmAction = AukDialogAction(
            label = stringResource(R.string.action_done),
            onClick = onDismissRequest
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(value.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.settings_interface_card_roundness_value, value),
                    style = MaterialTheme.typography.titleMediumEmphasized
                )
            }
        }
        Slider(
            value = value.toFloat().coerceIn(RoundnessRange),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = RoundnessRange,
            steps = RoundnessSteps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OptionIconPreview(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun transitionStyleIcon(style: Int): ImageVector = when (style) {
    1 -> Icons.AutoMirrored.Outlined.CompareArrows
    2 -> Icons.Outlined.AutoAwesome
    3 -> Icons.Outlined.Block
    else -> Icons.Outlined.Animation
}

private fun defaultTabIcon(tab: Int): ImageVector = when (tab) {
    PreferenceManager.TAB_FAVORITES -> Icons.Outlined.Star
    PreferenceManager.TAB_CONTACTS -> Icons.Outlined.Person
    else -> Icons.Outlined.History
}
