package auk.dialer.vroot.view.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.controller.util.SocialUtils
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.*
import auk.dialer.vroot.modal.data.Contact
import org.koin.compose.koinInject

class VideoLauncher(
    private val onInitiate: (String, Contact?) -> Unit
) {
    fun startVideoCall(number: String, contact: Contact? = null) {
        onInitiate(number, contact)
    }
}

@Composable
fun rememberVideoLauncher(): VideoLauncher {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val videoCallTitle = stringResource(R.string.video_call_title)
    val videoCallChooserWith = stringResource(R.string.video_call_chooser_with)

    var showAppPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }

    val launchCallApp = { pkg: String, number: String ->
        val uri = Uri.parse("tel:$number")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(pkg)
            if (pkg == "com.google.android.apps.meetings") {
                data = Uri.parse("https://meet.google.com/")
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), videoCallChooserWith)
            context.startActivity(chooser)
        }
    }

    val launchApp = { pkg: String, number: String ->
        // The messaging apps open the conversation with the number, its call buttons one tap away.
        when (pkg) {
            SIGNAL_PACKAGE -> SocialUtils.openSignal(context, number)
            WHATSAPP_PACKAGE -> SocialUtils.openWhatsApp(context, number)
            TELEGRAM_PACKAGE -> SocialUtils.openTelegram(context, number)
            else -> launchCallApp(pkg, number)
        }
    }

    val launchSystemVideoCall = { number: String ->
        val uri = Uri.parse("tel:$number")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setDataAndType(uri, "vnd.android.cursor.item/video-chat-address")
        }
        context.startActivity(Intent.createChooser(intent, videoCallTitle))
    }

    // Only the apps on the phone: one that is not would fall back to a plain voice call.
    val videoApps = listOf(
        stringResource(R.string.brand_signal) to SIGNAL_PACKAGE,
        stringResource(R.string.brand_whatsapp) to WHATSAPP_PACKAGE,
        stringResource(R.string.brand_google_meet) to "com.google.android.apps.meetings",
        stringResource(R.string.brand_zoom) to "us.zoom.videomeetings",
        stringResource(R.string.brand_telegram) to TELEGRAM_PACKAGE
    )
    val installedApps = remember(videoApps) {
        videoApps.filter { (_, pkg) -> context.packageManager.getLaunchIntentForPackage(pkg) != null }
    }

    val videoLauncher = remember(installedApps) {
        VideoLauncher { number, _ ->
            if (installedApps.isEmpty()) {
                launchSystemVideoCall(number)
            } else {
                pendingNumber = number
                showAppPicker = true
            }
        }
    }

    if (showAppPicker) {
        AukSelectionDialog(
            onDismissRequest = { showAppPicker = false },
            title = videoCallTitle,
            items = installedApps + (stringResource(R.string.video_call_system) to "system"),
            itemLabel = { it.first },
            onItemSelected = { (name, pkg) ->
                if (pkg == "system") {
                    launchSystemVideoCall(pendingNumber)
                } else {
                    launchApp(pkg, pendingNumber)
                }
            },
            icon = Icons.Default.VideoCall,
            itemIcon = { Icons.Default.VideoCall }
        )
    }

    return videoLauncher
}

private const val SIGNAL_PACKAGE = "org.thoughtcrime.securesms"
private const val WHATSAPP_PACKAGE = "com.whatsapp"
private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
