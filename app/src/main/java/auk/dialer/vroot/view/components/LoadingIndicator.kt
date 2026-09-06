package auk.dialer.vroot.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AukLoadingIndicatorView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AukContainedLoadingIndicatorView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ContainedLoadingIndicator(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            indicatorColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AukPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AukLinearProgressIndicator(modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

@Composable
fun AukLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}
