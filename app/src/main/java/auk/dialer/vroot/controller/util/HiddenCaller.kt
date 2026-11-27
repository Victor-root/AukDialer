package auk.dialer.vroot.controller.util

import android.content.Context
import android.provider.CallLog
import auk.dialer.vroot.R

/**
 * What to call a caller who left no number to show, null for one who did. [presentation] is the
 * call log's, or Telecom's, which uses the same values. Some phones log such a call with Android's
 * old placeholder numbers instead: "-1" unknown, "-2" withheld, "-3" payphone.
 */
fun hiddenCallerLabel(context: Context, number: String?, presentation: Int): String? = when {
    presentation == CallLog.Calls.PRESENTATION_RESTRICTED || number == "-2" ->
        context.getString(R.string.label_private_number)
    presentation == CallLog.Calls.PRESENTATION_PAYPHONE || number == "-3" ->
        context.getString(R.string.label_payphone)
    presentation != CallLog.Calls.PRESENTATION_ALLOWED || number.isNullOrBlank() || number == "-1" ->
        context.getString(R.string.label_unknown_number)
    else -> null
}
