package auk.dialer.vroot.view.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor

// The drawer of OpenMessages: a sheet rounded on its open side, the app's tile and name on top,
// one colour per entry, thin separators, and the page showing as a pill in the accent.
private val DrawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
private val PillShape = RoundedCornerShape(26.dp)
private val LogoShape = RoundedCornerShape(14.dp)
private val DrawerWidth = 320.dp
private val DrawerMinPeek = 64.dp

/** Each entry's icon colour: deeper on a light drawer, lighter on a dark one. */
private class EntryColor(val light: Color, val dark: Color)

private fun tabColor(tabId: Int): EntryColor = when (tabId) {
    PreferenceManager.TAB_CONTACTS -> EntryColor(Color(0xFF2E9E64), Color(0xFF4CC38A))
    PreferenceManager.TAB_FAVORITES -> EntryColor(Color(0xFFB8901A), Color(0xFFE7C84B))
    PreferenceManager.TAB_VOICEMAIL -> EntryColor(Color(0xFFD9822B), Color(0xFFF2A54A))
    PreferenceManager.TAB_RECORDINGS -> EntryColor(Color(0xFFD9434F), Color(0xFFEF6B75))
    else -> EntryColor(Color(0xFF2C88C9), Color(0xFF4FB3E8))
}

private val SettingsColor = EntryColor(Color(0xFF6B7A88), Color(0xFFA3AFBB))
private val AboutColor = EntryColor(Color(0xFF4E74E0), Color(0xFF7AA7FF))

/**
 * The side menu that stands in for the bottom bar with the "Side menu" setting: the tabs in their
 * bottom bar order, then Settings and About.
 */
@Composable
fun AukNavigationDrawer(
    tabs: List<Int>,
    currentTab: Int?,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // As OpenMessages' drawer: 320dp, and never so wide that the screen behind no longer shows.
    val width = minOf(DrawerWidth, LocalConfiguration.current.screenWidthDp.dp - DrawerMinPeek)
    ModalDrawerSheet(
        modifier = Modifier.width(width),
        drawerShape = DrawerShape,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        // Drawn behind the status bar, as the rest of the app is.
        windowInsets = WindowInsets(0)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            DrawerHeader()
            tabs.forEach { tab ->
                DrawerEntry(
                    icon = navigationTabIcon(tab),
                    label = navigationTabLabel(tab),
                    iconColor = tabColor(tab).let { if (dark) it.dark else it.light },
                    selected = tab == currentTab,
                    onClick = { onTabSelected(tab) }
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            DrawerEntry(
                icon = Icons.Filled.Settings,
                label = stringResource(R.string.settings_title),
                iconColor = if (dark) SettingsColor.dark else SettingsColor.light,
                selected = false,
                onClick = onSettings
            )
            DrawerEntry(
                icon = Icons.Filled.Info,
                label = stringResource(R.string.settings_about_headline),
                iconColor = if (dark) AboutColor.dark else AboutColor.light,
                selected = false,
                onClick = onAbout
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The launcher artwork fills only the middle of its canvas, so it is drawn larger than the
        // tile that clips it, as the launcher does.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(LogoShape)
                .background(LocalAccentBarColor.current),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(72.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = stringResource(R.string.about_app_display_name),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.drawer_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    label: String,
    iconColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalAccentBarColor.current
    val onAccent = LocalOnAccentBarColor.current
    Surface(
        onClick = onClick,
        selected = selected,
        shape = PillShape,
        color = if (selected) accent else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) onAccent else iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) onAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
