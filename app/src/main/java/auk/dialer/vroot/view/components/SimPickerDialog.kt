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

/** The SIMs a call can go out on, in slot order. Empty without the phone permission. */
@Composable
fun rememberCallCapablePhoneAccounts(): List<PhoneAccountHandle> {
    val context = LocalContext.current
    return remember(context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).callCapablePhoneAccounts
            } catch (e: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}

/** What each of [accounts] is called on screen: its carrier's label, or its slot when it has none. */
@Composable
fun rememberSimLabels(accounts: List<PhoneAccountHandle>): List<String> {
    val context = LocalContext.current
    val unknownSimLabel = stringResource(R.string.sim_picker_unknown_sim)
    val unknownSimSlotPattern = stringResource(R.string.sim_slot_unknown_label)
    return remember(accounts, unknownSimLabel, unknownSimSlotPattern) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        accounts.mapIndexed { index, handle ->
            telecomManager.getPhoneAccount(handle)?.label?.toString()?.takeIf { it.isNotBlank() }
                ?: String.format(unknownSimSlotPattern, index + 1, unknownSimLabel)
        }
    }
}

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

    val phoneAccounts = rememberCallCapablePhoneAccounts()
    val simLabels = rememberSimLabels(phoneAccounts)

    if (phoneAccounts.isEmpty()) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }

    // Read here rather than inside the plain (T) -> String lambda below: stringResource is a
    // composable function, and that lambda is called outside composition. The pattern still
    // carries its %1$d placeholder; each per-item index is substituted with String.format.
    val slotSupportingPattern = stringResource(R.string.sim_slot_supporting)

    AukSelectionDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sim_picker_title),
        items = phoneAccounts,
        itemLabel = { handle -> simLabels[phoneAccounts.indexOf(handle)] },
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
