package com.grinch.rivo4.debug

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.grinch.rivo4.modal.`interface`.IContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Debug-build toolbar menu for populating the voicemail list without a carrier.
 * The release source set provides an empty implementation of this function, so
 * none of it reaches a published build.
 */
@Composable
fun VoicemailDebugMenu(onChanged: () -> Unit) {
    val context = LocalContext.current
    val contactsRepo = koinInject<IContactsRepository>()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        expanded = false
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            onChanged()
        }
    }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.BugReport, contentDescription = "Debug tools")
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Add 1 voicemail") },
            onClick = { run { VoicemailInjector.insertOne(context) } }
        )
        DropdownMenuItem(
            text = { Text("Add 5 voicemails") },
            onClick = { run { VoicemailInjector.insertBatch(context) } }
        )
        DropdownMenuItem(
            text = { Text("Add from a random contact") },
            onClick = {
                run {
                    // Random rather than the first match: names, photos and
                    // number formats vary wildly, and always picking the same
                    // contact would only ever exercise one layout.
                    val number = contactsRepo.getContacts()
                        .filter { it.phoneNumbers.isNotEmpty() }
                        .randomOrNull()
                        ?.phoneNumbers
                        ?.random()
                    if (number != null) {
                        VoicemailInjector.insertFromNumber(context, number)
                    } else {
                        VoicemailInjector.insertOne(context)
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Add one without audio") },
            onClick = { run { VoicemailInjector.insertWithoutAudio(context) } }
        )
        DropdownMenuItem(
            text = { Text("Add a 60s one") },
            onClick = { run { VoicemailInjector.insertLong(context) } }
        )
        DropdownMenuItem(
            text = { Text("Restore deleted rows") },
            onClick = {
                expanded = false
                scope.launch {
                    val restored = withContext(Dispatchers.IO) {
                        VoicemailInjector.restoreDeleted(context)
                    }
                    val message = if (restored > 0) {
                        "Restored $restored row(s)"
                    } else {
                        "Nothing to restore"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Delete all injected") },
            onClick = { run { VoicemailInjector.deleteAllOwned(context) } }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Copy diagnostics") },
            onClick = {
                expanded = false
                scope.launch {
                    val report = withContext(Dispatchers.IO) { VoicemailDiagnostics.build(context) }
                    clipboard.setText(AnnotatedString(report))
                    // Also emitted to logcat, so the report can be grabbed from
                    // the IDE without going through the clipboard.
                    VoicemailDiagnostics.log(report)
                    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
