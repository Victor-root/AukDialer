package auk.dialer.vroot.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor
import com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

private val TopBarActionButtonSize = 44.dp
private const val SearchRevealMillis = 300

/**
 * The tabs' header: the section's [title] and its actions. The magnifier unrolls a search field over
 * the whole row from the right edge, and rolls it back the same way; the screen shows the results
 * while [search] is open.
 */
@Composable
fun TopBar(navigator: DestinationsNavigator, title: String, search: HeaderSearchState) {
    val prefs = koinInject<PreferenceManager>()
    val filtersVisible by prefs.showHeaderFilters.collectAsState()
    // With the filter row hidden this is the last thing in the header, and 4dp put it right against
    // the header's own edge. Only this collapsed case changes, so the spacing above the filter row,
    // when it's shown, stays exactly as it was.
    val rowPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = if (filtersVisible && !search.expanded) 4.dp else 12.dp
    )

    AukAccentHeader {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AukTopBarTitle(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                TopBarAction(
                    icon = Icons.Default.Search,
                    contentDescription = stringResource(R.string.top_bar_search_placeholder),
                    onClick = search::open
                )

                Surface(
                    onClick = {
                        prefs.setShowHeaderFilters(!filtersVisible)
                    },
                    modifier = Modifier.size(TopBarActionButtonSize),
                    shape = CircleShape,
                    color = if (filtersVisible) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (filtersVisible) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        LocalOnAccentBarColor.current
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (filtersVisible) Icons.Default.FilterAltOff else Icons.Default.FilterAlt,
                            contentDescription = stringResource(
                                if (filtersVisible) R.string.content_desc_hide_filters else R.string.content_desc_show_filters
                            )
                        )
                    }
                }

                TopBarAction(
                    icon = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.settings_title),
                    onClick = { navigator.navigate(SettingsScreenDestination) }
                )
            }

            AnimatedVisibility(
                visible = search.expanded,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = expandHorizontally(animationSpec = tween(SearchRevealMillis), expandFrom = Alignment.End),
                exit = shrinkHorizontally(animationSpec = tween(SearchRevealMillis), shrinkTowards = Alignment.End)
            ) {
                HeaderSearchField(search = search, padding = rowPadding)
            }
        }
    }
}

/**
 * A plain IconButton carries Material's own 48dp minimum touch target, which quietly forced this row
 * back up to 56dp tall whatever height it asked for. Sized directly instead.
 */
@Composable
private fun TopBarAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(TopBarActionButtonSize),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = LocalOnAccentBarColor.current
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

/** The search row, on the header's own colour so it covers the row it unrolls over. */
@Composable
private fun HeaderSearchField(search: HeaderSearchState, padding: PaddingValues) {
    val onAccent = LocalOnAccentBarColor.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // A fresh search opens the keyboard; coming back to one that already holds a query does not.
    LaunchedEffect(Unit) { if (search.query.isEmpty()) focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAccentBarColor.current)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TopBarAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = {
                focusManager.clearFocus()
                search.close()
            }
        )
        BasicTextField(
            value = search.query,
            onValueChange = { search.query = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = onAccent),
            cursorBrush = SolidColor(onAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Results follow the typing already: the keyboard's search key only puts the keyboard away.
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (search.query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_contacts_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = onAccent.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    inner()
                }
            }
        )
        if (search.query.isNotEmpty()) {
            TopBarAction(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_clear),
                onClick = { search.query = "" }
            )
        } else {
            Spacer(Modifier.size(TopBarActionButtonSize))
        }
    }
}
