package auk.dialer.vroot

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.controller.util.isAlreadyDefaultDialer
import auk.dialer.vroot.controller.util.makeCall
import auk.dialer.vroot.view.screen.onboarding.MorphingOnboardingScreen
import auk.dialer.vroot.view.components.PermissionPopup
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
        intentState = intent
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(appModule)
            }
        }

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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        DestinationsNavHost(
                            navGraph = NavGraphs.root,
                            navController = navController,
                            defaultTransitions = getAppTransition(transitionStyle)
                        )
                    }

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

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavController) {
        intent ?: return
        val data = intent.data
        val action = intent.action

        when (action) {
            "auk.dialer.vroot.ACTION_VIEW_RECENTS" -> {
                navController.navigate(MainScreenDestination(initialTab = 0).route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, Intent.ACTION_CALL -> {
                if (data?.scheme == "tel") {
                    val rawNumber = data.schemeSpecificPart
                    val number = android.net.Uri.decode(rawNumber) ?: rawNumber
                    val cleanNumber = number.replace(" ", "")
                    val isSecretOrImei = cleanNumber == "*#06#" || cleanNumber.startsWith("*#*#") || cleanNumber.startsWith("##") || (cleanNumber.startsWith("*#") && cleanNumber.endsWith("#"))
                    if (action == Intent.ACTION_CALL && isAlreadyDefaultDialer(this) && !isSecretOrImei) {
                        makeCall(this, number)
                    } else {
                        navController.navigate(DialPadScreenDestination(initialNumber = number).route)
                    }
                } else if (data?.toString()?.contains("contacts") == true || data?.toString()?.contains("com.android.contacts") == true || intent.hasExtra("contact_id")) {
                    val id = data?.lastPathSegment ?: intent.getStringExtra("contact_id")
                    if (id != null) {
                        navController.navigate(ContactDetailsScreenDestination(contactId = id).route)
                    }
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
