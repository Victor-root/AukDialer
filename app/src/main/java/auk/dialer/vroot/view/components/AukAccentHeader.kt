package auk.dialer.vroot.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor

/**
 * The accent band the main screen headers sit on.
 *
 * It paints behind the status bar, which is what gives the bar its colour, and insets its content
 * below it. Screens built on a Material [androidx.compose.material3.TopAppBar] get the same for
 * free; this is for the headers that are not one.
 */
@Composable
fun AukAccentHeader(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LocalAccentBarColor.current,
        contentColor = LocalOnAccentBarColor.current
    ) {
        Box(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            ),
            content = content
        )
    }
}
