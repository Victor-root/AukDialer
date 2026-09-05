package auk.dialer.vroot.view.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import auk.dialer.vroot.R

@Composable
fun SimPickerDialog(
    onDismissRequest: () -> Unit,
    onSimSelected: (PhoneAccountHandle) -> Unit,
    selectedAccount: PhoneAccountHandle? = null
) {
    val context = LocalContext.current
    val telecomManager = remember(context) {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    val phoneAccounts = remember(telecomManager, context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                telecomManager.callCapablePhoneAccounts
            } catch (e: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    if (phoneAccounts.isEmpty()) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }

    // Read here rather than inside the plain (T) -> String lambdas below: stringResource is a
    // composable function, and those lambdas are called outside composition. The patterns still
    // carry their %1$d / %2$s placeholders; each per-item index is substituted with String.format.
    val unknownSimLabel = stringResource(R.string.sim_picker_unknown_sim)
    val unknownSimSlotPattern = stringResource(R.string.sim_slot_unknown_label)
    val slotSupportingPattern = stringResource(R.string.sim_slot_supporting)

    AukSelectionDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sim_picker_title),
        items = phoneAccounts,
        itemLabel = { handle ->
            val account = telecomManager.getPhoneAccount(handle)
            val labelStr = account?.label?.toString()?.takeIf { it.isNotBlank() }
            if (labelStr != null) {
                labelStr
            } else {
                val index = phoneAccounts.indexOf(handle) + 1
                String.format(unknownSimSlotPattern, index, unknownSimLabel)
            }
        },
        onItemSelected = onSimSelected,
        itemSupporting = { handle ->
            val account = telecomManager.getPhoneAccount(handle)
            val address = account?.address?.schemeSpecificPart
            val desc = account?.shortDescription?.toString()
            if (!address.isNullOrBlank()) {
                address
            } else if (!desc.isNullOrBlank()) {
                desc
            } else {
                String.format(slotSupportingPattern, phoneAccounts.indexOf(handle) + 1)
            }
        },
        icon = Icons.Outlined.SimCard,
        itemIcon = { Icons.Outlined.SimCard },
        isSelected = { handle -> selectedAccount != null && handle == selectedAccount }
    )
}
