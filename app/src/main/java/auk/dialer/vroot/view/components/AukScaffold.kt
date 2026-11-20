package auk.dialer.vroot.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import auk.dialer.vroot.view.theme.LocalCollapsingHeader
import auk.dialer.vroot.view.theme.LocalEdgeToEdge
import auk.dialer.vroot.view.theme.aukCollapsingHeader
import auk.dialer.vroot.view.theme.aukSampleStatusBarContent
import auk.dialer.vroot.view.theme.rememberStatusBarSampler

/**
 * The Scaffold of a screen under an accent header, which behaves as the main screen's does: with
 * edge to edge on, the header slides away as the content scrolls up and comes back as it scrolls
 * down, the status bar icons keep readable over what then shows behind them, and a scroll to top
 * button brings the header back with the list. With edge to edge off the header stays put.
 * [collapsing] off keeps a header still, for content hosted under another screen's collapsing header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AukScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    collapsing: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val collapses = collapsing && LocalEdgeToEdge.current
    val statusBarSampler = rememberStatusBarSampler(scrollBehavior, enabled = collapses)

    CompositionLocalProvider(LocalCollapsingHeader provides scrollBehavior.takeIf { collapses }) {
        Scaffold(
            modifier = if (collapses) {
                modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .aukSampleStatusBarContent(statusBarSampler)
            } else {
                modifier
            },
            topBar = {
                Box(modifier = if (collapses) Modifier.aukCollapsingHeader(scrollBehavior) else Modifier) {
                    topBar()
                }
            },
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            contentWindowInsets = contentWindowInsets,
            content = content
        )
    }
}
