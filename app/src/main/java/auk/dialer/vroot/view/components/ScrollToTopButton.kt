package auk.dialer.vroot.view.components

import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.launch
import auk.dialer.vroot.view.theme.LocalCollapsingHeader
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor

// The same button as OpenMessages': a small disc in the accent, centred on the FAB below it.
private val ScrollToTopSize = 40.dp
private val FabWidth = 56.dp
private const val ScrollToTopAnimationMillis = 200
private const val ScrollToTopHiddenScale = 0.7f

/** The gap between this button and the FAB under it, in the Column holding both. */
val ScrollToTopFabGap = 8.dp

/**
 * Meant for a Scaffold's `floatingActionButton` slot: alone on screens with no FAB of their own,
 * so it lands where a FAB would; stacked above the real FAB (in a Column) otherwise.
 */
@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val header = LocalCollapsingHeader.current
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(ScrollToTopAnimationMillis), initialScale = ScrollToTopHiddenScale) +
            fadeIn(tween(ScrollToTopAnimationMillis)),
        exit = scaleOut(tween(ScrollToTopAnimationMillis), targetScale = ScrollToTopHiddenScale) +
            fadeOut(tween(ScrollToTopAnimationMillis)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.width(FabWidth), contentAlignment = Alignment.Center) {
            Surface(
                onClick = {
                    header?.let { collapsing ->
                        scope.launch {
                            animate(collapsing.state.heightOffset, 0f) { value, _ ->
                                collapsing.state.heightOffset = value
                            }
                        }
                    }
                    onClick()
                },
                modifier = Modifier.size(ScrollToTopSize),
                shape = CircleShape,
                color = LocalAccentBarColor.current,
                contentColor = LocalOnAccentBarColor.current,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.content_desc_scroll_to_top)
                    )
                }
            }
        }
    }
}
