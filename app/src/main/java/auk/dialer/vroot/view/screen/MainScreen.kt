package auk.dialer.vroot.view.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import auk.dialer.vroot.controller.util.PerfLog
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.AukNavigationDrawer
import auk.dialer.vroot.view.components.AukScaffold
import auk.dialer.vroot.view.components.BottomBar
import auk.dialer.vroot.view.components.LocalMainPageBottomInset
import auk.dialer.vroot.view.components.OngoingCallBanner
import auk.dialer.vroot.view.components.TopBar
import auk.dialer.vroot.view.components.navigationTabLabel
import auk.dialer.vroot.view.components.rememberHeaderSearchState
import auk.dialer.vroot.view.screen.settings.CallRecordingsContent
import auk.dialer.vroot.view.screen.transitions.NoTransitions
import auk.dialer.vroot.view.theme.AukPlainNavigationBar
import auk.dialer.vroot.view.theme.AukPlainStatusBar
import auk.dialer.vroot.view.theme.AukStaticMotion
import auk.dialer.vroot.view.theme.LocalEdgeToEdge
import auk.dialer.vroot.view.theme.LocalHeaderColor
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Destination<RootGraph>(start = true, style = NoTransitions::class)
@Composable
fun MainScreen(
    navController: NavController,
    navigator: DestinationsNavigator,
    initialTab: Int? = null
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()

    val visibleTabs = remember(settingsState) { prefs.getVisibleBottomNavTabs() }
    val defaultTab = remember(settingsState) {
        prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, PreferenceManager.TAB_RECENTS)
    }
    val filtersVisible by prefs.showHeaderFilters.collectAsState()
    val sideMenu = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_SIDE_MENU, false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val requestedTab = initialTab ?: defaultTab
    val startPage = visibleTabs.indexOf(requestedTab).coerceAtLeast(0)

    val pagerState = rememberPagerState(initialPage = startPage) { visibleTabs.size }
    val scope = rememberCoroutineScope()
    // Fades the pages through when a tab is picked from the bottom bar.
    val pagerAlpha = remember { Animatable(1f) }
    // The tab switch under way. Taps come faster than a switch ends, and the pager only reaches the
    // new page halfway through, so each tap is weighed against where the last one is heading.
    var tabSwitch by remember { mutableStateOf<Job?>(null) }
    var tabSwitchTarget by remember { mutableIntStateOf(0) }

    var isSelectingVoicemail by remember { mutableStateOf(false) }
    var voicemailActionBar by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var isSelectingRecordings by remember { mutableStateOf(false) }
    var recordingsActionBar by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var isSelectingRecents by remember { mutableStateOf(false) }
    var recentsActionBar by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var recentsFilterBar by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var isSelectingContacts by remember { mutableStateOf(false) }
    var contactsActionBar by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var contactsFilterBar by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    val currentTab = visibleTabs.getOrNull(pagerState.currentPage)
    val isSelecting = when (currentTab) {
        PreferenceManager.TAB_RECENTS -> isSelectingRecents
        PreferenceManager.TAB_VOICEMAIL -> isSelectingVoicemail
        PreferenceManager.TAB_RECORDINGS -> isSelectingRecordings
        PreferenceManager.TAB_CONTACTS -> isSelectingContacts
        else -> false
    }

    // The pager only remembers a position. Keep the tab itself, so reordering the bottom bar in
    // Settings brings the user back to the tab they were on rather than whatever now sits there.
    var shownTab by rememberSaveable { mutableStateOf(visibleTabs.getOrNull(startPage)) }
    // Only real page changes update it: the page restored on coming back may now hold another tab.
    LaunchedEffect(visibleTabs) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { page ->
            PerfLog.d("current page now $page")
            visibleTabs.getOrNull(page)?.let { shownTab = it }
        }
    }

    // A requested tab is shown once; coming back from another screen keeps the one the user picked since.
    var initialTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialTab, visibleTabs) {
        val target = visibleTabs.indexOf(requestedTab)
        val shownIndex = visibleTabs.indexOf(shownTab)
        if (initialTab != null && !initialTabApplied && target >= 0) {
            initialTabApplied = true
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        } else if (shownIndex >= 0 && pagerState.currentPage != shownIndex) {
            pagerState.scrollToPage(shownIndex)
        } else if (pagerState.currentPage > visibleTabs.lastIndex) {
            pagerState.scrollToPage(visibleTabs.lastIndex.coerceAtLeast(0))
        }
    }

    // The header the Scaffold collapses with edge to edge, kept here to bring it back on a tab change.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Switching tab brings the header back: a short list has nothing left to scroll up, and would
    // otherwise leave it stuck off screen.
    LaunchedEffect(pagerState.currentPage) {
        scrollBehavior.state.heightOffset = 0f
    }

    val headerSearch = rememberHeaderSearchState()
    val headerTitle = navigationTabLabel(currentTab ?: defaultTab)
    // Only these two tabs have a filter row.
    val tabHasFilters = (currentTab ?: defaultTab).let {
        it == PreferenceManager.TAB_RECENTS || it == PreferenceManager.TAB_CONTACTS
    }

    val selectPage: (Int) -> Unit = { page ->
        PerfLog.d("tab tapped: page $page")
        val heading = if (tabSwitch?.isActive == true) tabSwitchTarget else pagerState.currentPage
        if (page != heading) {
            tabSwitch?.cancel()
            tabSwitchTarget = page
            tabSwitch = scope.launch {
                pagerAlpha.animateTo(0f, AukStaticMotion.tabFadeOut())
                pagerState.scrollToPage(page)
                pagerAlpha.animateTo(1f, AukStaticMotion.tabFadeIn())
            }
        }
    }
    val openMenu: (() -> Unit)? = if (sideMenu) ({ scope.launch { drawerState.open() } }) else null
    // Without the bottom bar, the pages meet the navigation bar: behind it with edge to edge, which
    // they then keep their end and buttons clear of; above its opaque band otherwise.
    val pagesBehindNavigationBar = sideMenu && LocalEdgeToEdge.current
    val pageBottomInset = if (pagesBehindNavigationBar) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

    val screen: @Composable () -> Unit = { AukScaffold(
        scrollBehavior = scrollBehavior,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedContent(
                targetState = isSelecting,
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "MainTopBarTransition"
            ) { selecting ->
                if (selecting) {
                    when (currentTab) {
                        PreferenceManager.TAB_RECENTS -> recentsActionBar?.invoke()
                        PreferenceManager.TAB_VOICEMAIL -> voicemailActionBar?.invoke()
                        PreferenceManager.TAB_RECORDINGS -> recordingsActionBar?.invoke()
                        PreferenceManager.TAB_CONTACTS -> contactsActionBar?.invoke()
                        else -> TopBar(navigator, headerTitle, headerSearch, hasFilters = tabHasFilters, onMenuClick = openMenu)
                    }
                } else {
                    Column {
                        TopBar(navigator, headerTitle, headerSearch, hasFilters = tabHasFilters, onMenuClick = openMenu)
                        AnimatedVisibility(
                            visible = filtersVisible && !headerSearch.expanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            when (currentTab) {
                                PreferenceManager.TAB_RECENTS -> recentsFilterBar?.invoke()
                                PreferenceManager.TAB_CONTACTS -> contactsFilterBar?.invoke()
                                else -> {}
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                OngoingCallBanner()
                if (sideMenu) {
                    if (!pagesBehindNavigationBar) {
                        // No bottom bar to hold the content clear of the navigation bar: this does.
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                } else {
                    BottomBar(
                        navController = navController,
                        navigator = navigator,
                        pagerState = pagerState,
                        visibleTabs = visibleTabs,
                        onPageSelected = selectPage
                    )
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalMainPageBottomInset provides pageBottomInset) {
            HeaderSearchContent(headerSearch, navigator, Modifier.padding(innerPadding).fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pagerAlpha.value }) {
                    // Every page is kept, but only filled once it comes within a page of the current one,
                    // as before. Dropping a page when it went out of reach meant building it again each
                    // time it came back, which on a phone took a whole tab's worth of work in one frame,
                    // in the middle of the page animation.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = visibleTabs.size
                    ) { page ->
                        val inReach by remember(page) {
                            derivedStateOf { abs(pagerState.currentPage - page) <= 1 }
                        }
                        var built by remember(page) { mutableStateOf(false) }
                        LaunchedEffect(inReach) { if (inReach) built = true }
                        if (!inReach && !built) return@HorizontalPager
                        LaunchedEffect(Unit) { PerfLog.d("page $page built") }
                        when (visibleTabs.getOrNull(page)) {
                            PreferenceManager.TAB_RECENTS -> RecentScreenContent(
                                navController = navController,
                                navigator = navigator,
                                onSelectionStateChange = { selecting, actionBar ->
                                    isSelectingRecents = selecting
                                    recentsActionBar = actionBar
                                },
                                onFilterBarChange = { filterBar -> recentsFilterBar = filterBar },
                                isShown = pagerState.currentPage == page
                            )
                            PreferenceManager.TAB_FAVORITES -> FavoritesScreenContent(
                                navController = navController,
                                navigator = navigator,
                                showTopBar = false
                            )
                            PreferenceManager.TAB_CONTACTS -> ContactScreenContent(
                                navController = navController,
                                navigator = navigator,
                                onSelectionStateChange = { selecting, actionBar ->
                                    isSelectingContacts = selecting
                                    contactsActionBar = actionBar
                                },
                                onFilterBarChange = { filterBar -> contactsFilterBar = filterBar }
                            )
                            PreferenceManager.TAB_VOICEMAIL -> VoicemailScreenContent(
                                navigator = navigator,
                                showTopBar = false,
                                isShown = pagerState.currentPage == page,
                                onSelectionStateChange = { selecting, actionBar ->
                                    isSelectingVoicemail = selecting
                                    voicemailActionBar = actionBar
                                }
                            )
                            PreferenceManager.TAB_RECORDINGS -> CallRecordingsContent(
                                isShown = pagerState.currentPage == page,
                                onSelectionStateChange = { selecting, actionBar ->
                                    isSelectingRecordings = selecting
                                    recordingsActionBar = actionBar
                                }
                            )
                        }
                    }
                }
            }
        }
    } }

    if (sideMenu) {
        // The open menu lies behind both system bars, as VFiles' does: their icons read on it.
        if (drawerState.targetValue == DrawerValue.Open) {
            AukPlainStatusBar()
            AukPlainNavigationBar()
        }
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        val edgeToEdge = LocalEdgeToEdge.current
        // Opened from its button only: a swipe from the edge would fight the pages' own swipe.
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                AukNavigationDrawer(
                    tabs = visibleTabs,
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        scope.launch { drawerState.close() }
                        selectPage(visibleTabs.indexOf(tab))
                    },
                    onOpen = { direction ->
                        scope.launch { drawerState.close() }
                        navigator.navigate(direction)
                    }
                )
            }
        ) {
            Box {
                screen()
                // Without edge to edge, the header-coloured band over the navigation bar, under the
                // menu: the theme's own band, drawn over everything, gives way while the menu is open.
                if (!edgeToEdge) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(LocalHeaderColor.current)
                    )
                }
            }
        }
    } else {
        screen()
    }
}
