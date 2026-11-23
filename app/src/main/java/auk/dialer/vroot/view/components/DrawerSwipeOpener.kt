package auk.dialer.vroot.view.components

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val SwipeThreshold = 48.dp

/**
 * Opens the side menu with the sideways swipe the pages leave over, which only happens on the first one:
 * the pages take every swipe they can use, so the menu's own drag never sees them.
 */
@Composable
fun rememberDrawerSwipeOpener(drawerState: DrawerState): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val towardMenu = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return remember(drawerState, threshold, towardMenu) {
        object : NestedScrollConnection {
            private var pulled = 0f
            private var opening = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val toward = available.x * towardMenu
                if (source != NestedScrollSource.UserInput || toward <= 0f) return Offset.Zero
                pulled += toward
                if (!opening && pulled >= threshold) {
                    opening = true
                    scope.launch { drawerState.open() }
                }
                return Offset(available.x, 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                opening = false
                return Velocity.Zero
            }
        }
    }
}
