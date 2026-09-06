package auk.dialer.vroot.view.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.GITHUB_URL
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.LauncherIconManager
import auk.dialer.vroot.controller.util.getAppVersion
import auk.dialer.vroot.controller.util.openLink
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukListItem
import auk.dialer.vroot.view.components.AukSectionHeader
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AboutScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val appInfo = getAppVersion(context)
    val launcherIconManager = koinInject<LauncherIconManager>()
    // The badge below is a small copy of the launcher icon, so it takes that icon's own colour
    // rather than the in-app theme accent, which is a separate setting.
    val logoColor = remember { Color(launcherIconManager.currentColor()) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = aukAccentTopAppBarColors(),
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AukExpressiveCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = logoColor,
                        contentColor = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = stringResource(R.string.about_logo_content_desc),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.about_app_display_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "v${appInfo.first} (${appInfo.second})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                AukSectionHeader(
                    title = stringResource(R.string.about_author_title),
                    contentPadding = PaddingValues(vertical = 4.dp)
                )
                AukExpressiveCard {
                    val authorGithubUrl = "https://github.com/Victor-root"
                    AukListItem(
                        headline = "Victor-root",
                        supporting = stringResource(
                            R.string.about_author_repo_supporting,
                            stringResource(R.string.about_app_display_name)
                        ),
                        leadingIcon = Icons.Outlined.Person,
                        onClick = { openLink(context, authorGithubUrl) }
                    )
                    AukListItem(
                        headline = stringResource(R.string.about_follow_github),
                        leadingIcon = Icons.AutoMirrored.Outlined.Launch,
                        onClick = { openLink(context, authorGithubUrl) }
                    )
                }
            }

            AukExpressiveCard {
                AukListItem(
                    headline = stringResource(R.string.about_source_code),
                    supporting = stringResource(R.string.about_source_code_supporting),
                    leadingIcon = Icons.Outlined.Code,
                    onClick = { openLink(context, GITHUB_URL) }
                )
                AukListItem(
                    headline = stringResource(R.string.about_check_updates),
                    supporting = stringResource(R.string.about_current_version, appInfo.first),
                    leadingIcon = Icons.Outlined.SystemUpdate,
                    onClick = { openLink(context, "$GITHUB_URL/releases") }
                )
            }

            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}
