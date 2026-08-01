package com.grinch.rivo4.debug

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
            text = { Text("Delete all injected") },
            onClick = { run { VoicemailInjector.deleteAllOwned(context) } }
        )
    }
}
