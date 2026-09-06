package auk.dialer.vroot.modal.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import auk.dialer.vroot.R

@Composable
fun CallLogFilter.displayLabel(): String = when (this) {
    CallLogFilter.All -> stringResource(R.string.filter_all)
    CallLogFilter.Contacts -> stringResource(R.string.nav_contacts)
    CallLogFilter.Incoming -> stringResource(R.string.call_type_incoming)
    CallLogFilter.Outgoing -> stringResource(R.string.call_type_outgoing)
    CallLogFilter.Missed -> stringResource(R.string.call_type_missed)
}

enum class CallLogFilter {
    All,
    Contacts,
    Incoming,
    Outgoing,
    Missed
}