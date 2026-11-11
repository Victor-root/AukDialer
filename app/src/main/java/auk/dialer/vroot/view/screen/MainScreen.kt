package auk.dialer.vroot.view.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavController
import auk.dialer.vroot.controller.util.PerfLog
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.components.BottomBar
import auk.dialer.vroot.view.components.TopBar
import auk.dialer.vroot.view.components.navigationTabLabel
import auk.dialer.vroot.view.components.rememberHeaderSearchState
import auk.dialer.vroot.view.screen.settings.CallRecordingsContent
import auk.dialer.vroot.view.screen.transitions.NoTransitions
import auk.dialer.vroot.view.theme.AukStaticMotion
import auk.dialer.vroot.view.theme.LocalEdgeToEdge
import auk.dialer.vroot.view.theme.aukCollapsingHeader
import auk.dialer.vroot.view.theme.aukSampleStatusBarContent
import auk.dialer.vroot.view.theme.rememberStatusBarSampler
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
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

    val requestedTab = initialTab ?: defaultTab
    val startPage = visibleTabs.indexOf(requestedTab).coerceAtLeast(0)

    val pagerState = rememberPagerState(initialPage = startPage) { visibleTabs.size }
    val scope = rememberCoroutineScope()
    // Fades the pages through when a tab is picked from the bottom bar.
    val pagerAlpha = remember { Animatable(1f) }

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

    // Edge to edge lets the header leave on scroll so the content reaches behind the status bar.
    // With it off the header stays put, which is what keeps the status bar accent coloured.
    val edgeToEdge = LocalEdgeToEdge.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Switching tab brings the header back: a short list has nothing left to scroll up, and would
    // otherwise leave it stuck off screen.
    LaunchedEffect(pagerState.currentPage) {
        scrollBehavior.state.heightOffset = 0f
    }

    val statusBarSampler = rememberStatusBarSampler(scrollBehavior, enabled = edgeToEdge)
    val headerSearch = rememberHeaderSearchState()
    val headerTitle = navigationTabLabel(currentTab ?: defaultTab)

    Scaffold(
        modifier = if (edgeToEdge) {
            Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .aukSampleStatusBarContent(statusBarSampler)
        } else {
            Modifier
        },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(modifier = if (edgeToEdge) Modifier.aukCollapsingHeader(scrollBehavior) else Modifier) {
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
                            else -> TopBar(navigator, headerTitle, headerSearch)
                        }
                    } else {
                        Column {
                            TopBar(navigator, headerTitle, headerSearch)
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
            }
        },
        bottomBar = {
            BottomBar(
                navController = navController,
                navigator = navigator,
                pagerState = pagerState,
                visibleTabs = visibleTabs,
                onPageSelected = { page ->
                    PerfLog.d("tab tapped: page $page")
                    if (page != pagerState.currentPage) {
                        scope.launch {
                            pagerAlpha.animateTo(0f, AukStaticMotion.tabFadeOut())
                            pagerState.scrollToPage(page)
                            pagerAlpha.animateTo(1f, AukStaticMotion.tabFadeIn())
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
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
}
