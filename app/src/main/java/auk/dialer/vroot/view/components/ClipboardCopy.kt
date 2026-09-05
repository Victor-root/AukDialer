package auk.dialer.vroot.view.components

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch

/**
 * Returns a function that puts plain text on the clipboard.
 *
 * Writing to the clipboard suspends, so the call is launched on the
 * composition's scope and click handlers stay ordinary functions.
 */
@Composable
fun rememberClipboardCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text: String ->
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText("", text).toClipEntry())
            }
            Unit
        }
    }
}
