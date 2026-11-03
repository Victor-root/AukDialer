package auk.dialer.vroot.view.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import auk.dialer.vroot.controller.util.AukHaptics
import auk.dialer.vroot.controller.util.PreferenceManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * A light tick each time another row reaches the top of [listState], when the setting asks for it.
 * Followed outside composition: reading the first visible item in composition would recompose the
 * whole list on every row scrolled past.
 */
@Composable
fun ListScrollHaptics(listState: LazyListState, prefs: PreferenceManager) {
    if (!prefs.getBoolean(PreferenceManager.KEY_HAPTIC_LIST_SCROLL, false)) return
    val context = LocalContext.current
    LaunchedEffect(listState, context) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .drop(1)
            .collect { AukHaptics.play(context, AukHaptics.Effect.Tick) }
    }
}
