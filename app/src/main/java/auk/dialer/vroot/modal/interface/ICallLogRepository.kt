package auk.dialer.vroot.modal.`interface`

import auk.dialer.vroot.modal.data.CallLogEntry

interface ICallLogRepository {
    fun getCallLogs(): List<CallLogEntry>
    fun saveCallLog(entry: CallLogEntry)
    fun deleteCallLog(number: String)
    fun deleteCallLogsByIds(ids: List<Long>)
    fun clearCallLogs()
}
