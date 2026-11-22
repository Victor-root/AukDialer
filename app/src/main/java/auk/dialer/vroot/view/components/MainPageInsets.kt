package auk.dialer.vroot.view.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The room the main screen's pages keep at their bottom for the navigation bar. Zero, except with
 * the side menu and edge to edge: the pages then run on behind the bar, and their last item and
 * buttons stay this far clear of it.
 */
val LocalMainPageBottomInset = compositionLocalOf { 0.dp }

/** [bottom] plus the room for the navigation bar, for the end of a main screen page's list. */
@androidx.compose.runtime.Composable
fun mainPageBottom(bottom: Dp): Dp = bottom + LocalMainPageBottomInset.current
