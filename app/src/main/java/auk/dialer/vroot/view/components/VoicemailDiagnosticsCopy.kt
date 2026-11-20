package auk.dialer.vroot.view.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.vvm.VoicemailDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Copies the voicemail diagnostics, built off the main thread since they test the mailbox connection. */
class VoicemailDiagnosticsCopy(val inProgress: Boolean, val copy: () -> Unit)

/** [onReport] also gets the report, for a debug build to log it. */
@Composable
fun rememberVoicemailDiagnosticsCopy(onReport: (String) -> Unit = {}): VoicemailDiagnosticsCopy {
    val context = LocalContext.current
    val copyToClipboard = rememberClipboardCopy()
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.voicemail_diagnostics_copied)
    var inProgress by remember { mutableStateOf(false) }
    return VoicemailDiagnosticsCopy(inProgress) {
        if (!inProgress) {
            inProgress = true
            scope.launch {
                val report = withContext(Dispatchers.IO) { VoicemailDiagnostics.build(context) }
                copyToClipboard(report)
                onReport(report)
                inProgress = false
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
