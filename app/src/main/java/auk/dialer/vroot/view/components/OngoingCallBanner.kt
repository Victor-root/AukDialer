package auk.dialer.vroot.view.components

import android.content.Intent
import android.telecom.Call
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.CallService
import auk.dialer.vroot.controller.util.LauncherIconManager
import auk.dialer.vroot.view.screen.formatDuration
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

/** A bar telling a call is in progress while the app is shown, opening the call screen on tap. */
@Composable
fun OngoingCallBanner(modifier: Modifier = Modifier) {
    val session by CallService.currentCallSession.collectAsState()
    val ongoing = session?.state?.let { it != Call.STATE_DISCONNECTED } == true
    val connectTime = session?.connectTimeMillis ?: 0L

    AnimatedVisibility(
        visible = ongoing,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val context = LocalContext.current
        val launcherIconManager = koinInject<LauncherIconManager>()
        val label = stringResource(R.string.notif_active_call)

        var elapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(connectTime) {
            if (connectTime <= 0L) return@LaunchedEffect
            while (true) {
                elapsed = (System.currentTimeMillis() - connectTime) / 1000
                delay(1.seconds)
            }
        }

        Surface(
            color = LocalAccentBarColor.current,
            contentColor = LocalOnAccentBarColor.current,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.ongoing_call_return),
                    role = Role.Button
                ) {
                    context.startActivity(
                        Intent()
                            .setComponent(launcherIconManager.callScreenComponent())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (connectTime > 0L) {
                    Text(text = formatDuration(elapsed), style = MaterialTheme.typography.titleSmall)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
