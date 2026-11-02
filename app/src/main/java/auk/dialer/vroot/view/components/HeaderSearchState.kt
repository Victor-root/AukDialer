package auk.dialer.vroot.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The search a header's magnifier unrolls: whether it is open and what is typed in it. Kept by the
 * screen around that header, which shows the results in place of its own body meanwhile.
 */
@Stable
class HeaderSearchState(expanded: Boolean = false, query: String = "") {
    var expanded by mutableStateOf(expanded)
        private set
    var query by mutableStateOf(query)

    fun open() {
        expanded = true
    }

    fun close() {
        expanded = false
        query = ""
    }

    companion object {
        val Saver: Saver<HeaderSearchState, Any> = listSaver(
            save = { listOf(it.expanded, it.query) },
            restore = { HeaderSearchState(it[0] as Boolean, it[1] as String) }
        )
    }
}

@Composable
fun rememberHeaderSearchState(): HeaderSearchState =
    rememberSaveable(saver = HeaderSearchState.Saver) { HeaderSearchState() }
