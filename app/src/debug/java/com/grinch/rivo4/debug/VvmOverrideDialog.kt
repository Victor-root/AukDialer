package com.grinch.rivo4.debug

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.grinch.rivo4.controller.vvm.VvmConfigOverride

/**
 * Lets a tester supply the voicemail settings a device does not carry.
 *
 * Android's carrier database is not open: AOSP ships a handful of carriers and
 * a single visual voicemail entry, the useful one comes from the vendor. On a
 * phone whose vendor left a carrier out, the only way to find out whether the
 * service answers is to enter where to reach it and try.
 *
 * The override applies to every active SIM, which is safe because a SIM the
 * platform already describes ignores it.
 */
@Composable
fun VvmOverrideDialog(
    onDismiss: () -> Unit,
    onApplied: (String) -> Unit,
) {
    val context = LocalContext.current
    val existing = remember { firstStoredOverride(context) }
    var destination by remember { mutableStateOf(existing?.destinationNumber ?: "") }
    var port by remember { mutableStateOf(existing?.portNumber?.takeIf { it > 0 }?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carrier voicemail config") },
        text = {
            Column {
                Text(
                    "Used only when the phone carries no voicemail config for the SIM. " +
                        "Leave the port empty unless the carrier sends binary SMS."
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { input -> port = input.filter { it.isDigit() } },
                    label = { Text("Port (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = destination.isNotBlank(),
                onClick = {
                    val subs = activeSubscriptionIds(context)
                    subs.forEach {
                        VvmConfigOverride.save(context, it, destination, port.toIntOrNull() ?: 0)
                    }
                    onDismiss()
                    onApplied(
                        if (subs.isEmpty()) {
                            "No active SIM to apply it to"
                        } else {
                            "Saved for ${subs.size} SIM(s), asking the carrier"
                        }
                    )
                }
            ) { Text("Save and test") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    activeSubscriptionIds(context).forEach { VvmConfigOverride.clear(context, it) }
                    onDismiss()
                    onApplied("Override cleared")
                }
            ) { Text("Clear") }
        }
    )
}

private fun activeSubscriptionIds(context: Context): List<Int> {
    return try {
        context.getSystemService(SubscriptionManager::class.java)
            ?.activeSubscriptionInfoList
            ?.map { it.subscriptionId }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun firstStoredOverride(context: Context): VvmConfigOverride.Settings? =
    activeSubscriptionIds(context).firstNotNullOfOrNull { VvmConfigOverride.load(context, it) }
