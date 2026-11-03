package auk.dialer.vroot

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import auk.dialer.vroot.controller.util.PerfLog
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.controller.util.isAlreadyDefaultDialer
import auk.dialer.vroot.controller.util.makeCall
import auk.dialer.vroot.controller.vvm.VvmNotifier
import auk.dialer.vroot.view.screen.onboarding.MorphingOnboardingScreen
import auk.dialer.vroot.view.components.PermissionPopup
import auk.dialer.vroot.view.components.navigationTabRoute
import auk.dialer.vroot.controller.util.isCustomPermissionDevice
import auk.dialer.vroot.view.screen.transitions.AppTransitions
import auk.dialer.vroot.view.screen.transitions.getAppTransition
import auk.dialer.vroot.view.theme.AukTheme
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {
    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> }
    private var intentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The launcher icon starts us through an alias. When the task was opened another way first
        // (a notification, another app), its root is MainActivity itself, so the system does not
        // recognise the task and stacks a fresh copy on top, back on the default tab. Close that
        // copy and let the screen already open show through, where the user left it.
        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        ) {
            finish()
            return
        }
        // Handled once: a rebuilt screen (rotation, theme change) or a relaunch from Recents would
        // otherwise open its dialer, contact or form a second time.
        val launchedFromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !launchedFromHistory) intentState = intent

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(appModule)
            }
        }

        // TEMPORARY: the previous log confirmed the right (and only) alias is
        // enabled, so the splash-always-violet bug is not a state problem on
        // our side. This checks whether the OS actually starts the activity
        // through that alias, to tell apart a wrong launch from the OS simply
        // not honouring the alias's own splash theme.
        if (BuildConfig.DEBUG_TOOLS) {
            Log.d("AukIconDebug", "launched via component=${intent?.component}")
        }

        PerfLog.watchFrames(window)

        setContent {
            AukTheme {
                val navController = rememberNavController()

                val prefs = koinInject<PreferenceManager>()
                val defBar = prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0)
                val transitionStyle = prefs.getInt(PreferenceManager.KEY_TRANSITION_STYLE, 0)
                val onboardingShown = remember { prefs.getBoolean(PreferenceManager.KEY_ONBOARDING_SHOWN, false) }
                val permissionPopupShown = remember { prefs.getBoolean(PreferenceManager.KEY_PERMISSION_POPUP_SHOWN, false) }

                var showOnboarding by remember { mutableStateOf(!onboardingShown) }
                var showPermissionPopup by remember { mutableStateOf(onboardingShown && !permissionPopupShown && isCustomPermissionDevice()) }

                if (showOnboarding) {
                    MorphingOnboardingScreen(
                        onFinished = {
                            prefs.setBoolean(PreferenceManager.KEY_ONBOARDING_SHOWN, true)
                            showOnboarding = false
                            if (!permissionPopupShown && isCustomPermissionDevice()) {
                                showPermissionPopup = true
                            }
                        }
                    )
                } else if (showPermissionPopup) {
                    PermissionPopup(
                        onDismiss = {
                            prefs.setBoolean(PreferenceManager.KEY_PERMISSION_POPUP_SHOWN, true)
                            showPermissionPopup = false
                        }
                    )
                } else {
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                        defaultTransitions = getAppTransition(transitionStyle)
                    )

                    LaunchedEffect(Unit) {
                        if (!isAlreadyDefaultDialer(this@MainActivity)) {
                            navController.navigate(DefaultDialerScreenDestination.route) {
                                popUpTo(MainScreenDestination.route) {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    LaunchedEffect(intentState) {
                        handleIntent(intentState, navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState = intent
    }

    /** Straight to [tab] when the bottom bar carries it, otherwise to its screen on its own. */
    private fun openTab(navController: androidx.navigation.NavController, tab: Int) {
        val route = if (tab in get<PreferenceManager>().getVisibleBottomNavTabs()) {
            MainScreenDestination(initialTab = tab).route
        } else {
            navigationTabRoute(tab)
        }
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId)
            launchSingleTop = true
        }
    }

    /**
     * The contact a link from another app points to. Its last number is not always the contact's id:
     * a link to one of the contact's numbers or to one account's copy of the contact ends with the
     * id of that row instead, which belongs to some other contact. The contacts provider says which
     * contact the row belongs to.
     */
    private fun resolveContactId(uri: android.net.Uri): String? {
        if (uri.authority == ContactsContract.AUTHORITY) {
            val column = when (uri.pathSegments.firstOrNull()) {
                "raw_contacts", "data" -> ContactsContract.Data.CONTACT_ID
                else -> ContactsContract.Contacts._ID
            }
            try {
                contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0).toString()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG_TOOLS) Log.d("AukIntent", "contact link not resolved: $uri", e)
            }
        }
        return uri.lastPathSegment
    }

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavController) {
        intent ?: return
        val data = intent.data
        val action = intent.action

        when (action) {
            "auk.dialer.vroot.ACTION_VIEW_RECENTS" -> openTab(navController, PreferenceManager.TAB_RECENTS)
            VvmNotifier.ACTION_VIEW_VOICEMAIL -> openTab(navController, PreferenceManager.TAB_VOICEMAIL)
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, Intent.ACTION_CALL -> {
                if (data?.scheme == "tel") {
                    val rawNumber = data.schemeSpecificPart
                    val number = android.net.Uri.decode(rawNumber) ?: rawNumber
                    val cleanNumber = number.replace(" ", "")
                    val isSecretOrImei = cleanNumber == "*#06#" || cleanNumber.startsWith("*#*#") || cleanNumber.startsWith("##") || (cleanNumber.startsWith("*#") && cleanNumber.endsWith("#"))
                    if (action == Intent.ACTION_CALL && isAlreadyDefaultDialer(this) && !isSecretOrImei) {
                        makeCall(this, number)
                    } else {
                        navController.navigate(DialPadScreenDestination(initialNumber = number, openedByOtherApp = true).route)
                    }
                } else if (data?.toString()?.contains("contacts") == true || data?.toString()?.contains("com.android.contacts") == true || intent.hasExtra("contact_id")) {
                    val id = data?.let { resolveContactId(it) } ?: intent.getStringExtra("contact_id")
                    if (id != null) {
                        navController.navigate(ContactDetailsScreenDestination(contactId = id).route)
                    }
                } else if (action == Intent.ACTION_DIAL && data == null) {
                    // "Add call" from the call screen, or another app opening the dialer empty.
                    navController.navigate(DialPadScreenDestination(openedByOtherApp = true).route)
                }
            }
            Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                navController.navigate(ContactEditScreenDestination(initialName = name, initialPhone = phone).route)
            }
            Intent.ACTION_EDIT -> {
                val id = data?.lastPathSegment
                if (id != null) {
                    navController.navigate(ContactEditScreenDestination(contactId = id).route)
                }
            }
        }
    }
}
