package auk.dialer.vroot.modal.`interface`

import auk.dialer.vroot.modal.data.CallLogEntry

interface ICallLogRepository {
    /** Every call in the system call log, newest first, one entry per call. */
    fun getCalls(): List<CallLogEntry>

    /** Adds [entry] to the call log unless that call is already there. False when the write failed. */
    fun saveCallLog(entry: CallLogEntry): Boolean
    fun deleteCallLog(number: String)
    fun deleteCallLogsByIds(ids: List<Long>)
    fun clearCallLogs()
}
