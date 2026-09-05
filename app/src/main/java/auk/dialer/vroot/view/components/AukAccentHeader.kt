package auk.dialer.vroot.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/**
 * The filter chips that sit straight under a header, carried on the same accent band so the two
 * read as one header rather than a coloured strip with a loose row of chips below it.
 */
@Composable
fun AukAccentFilterBar(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LocalAccentBarColor.current,
        contentColor = LocalOnAccentBarColor.current
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
