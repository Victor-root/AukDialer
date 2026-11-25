package auk.dialer.vroot.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PerfLog
import auk.dialer.vroot.controller.util.areNumbersEqual
import auk.dialer.vroot.controller.util.formatPhoneNumber
import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A recording with who it was with, as known now rather than when it was recorded: a number not in
 * the contacts then may be a contact since, or carry the name the call log got after the call
 * (a business name, for instance).
 */
data class RecordingEntry(
    val recording: CallRecorder.Recording,
    val name: String,
    val photoUri: String?,
    val isIdentified: Boolean
)

/**
 * The saved call recordings, kept for the whole activity so the list shows at once on each return
 * while the folder is read again behind it, as voicemails do.
 */
class CallRecordingsViewModel(
    private val context: Context,
    private val contacts: IContactsRepository,
    private val callLog: ICallLogRepository,
) : ViewModel() {

    private val _recordings = MutableStateFlow<List<RecordingEntry>?>(null)
    val recordings: StateFlow<List<RecordingEntry>?> = _recordings.asStateFlow()

    // Asks made while a read runs fold into one more read after it.
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (request in refreshRequests) {
                _recordings.value = PerfLog.timed("recordings read") {
                    identifyCallers(CallRecorder.loadRecordings(context))
                }
            }
        }
    }

    /** Reads the folder again, a call having possibly been recorded or a recording deleted. */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    /** Blocking: looks numbers up in the contacts, then in the call log. */
    private fun identifyCallers(recordings: List<CallRecorder.Recording>): List<RecordingEntry> {
        val unknownLabel = context.getString(R.string.label_unknown)
        // Only read when a recording carries a number nobody in the contacts has.
        val calls by lazy { runCatching { callLog.getCalls() }.getOrDefault(emptyList()) }
        return recordings.map { recording ->
            val caller = recording.caller
            // Recorded under the contact's name when it was known, under the number otherwise.
            val isNumber = caller.isNotEmpty() && caller.all { it.isDigit() || it == '+' }
            if (!isNumber) {
                return@map RecordingEntry(recording, caller.ifEmpty { unknownLabel }, null, caller.isNotEmpty())
            }
            val contact = runCatching { contacts.getContactByNumber(caller) }.getOrNull()
            val name = contact?.name ?: calls.firstOrNull { call ->
                call.name != null && call.name != call.number && areNumbersEqual(call.number, caller)
            }?.name
            RecordingEntry(recording, name ?: formatPhoneNumber(caller), contact?.photoUri, name != null)
        }
    }
}
