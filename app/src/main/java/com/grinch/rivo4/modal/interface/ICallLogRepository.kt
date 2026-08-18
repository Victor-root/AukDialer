package com.grinch.rivo4.modal.`interface`

import com.grinch.rivo4.modal.data.CallLogEntry

interface ICallLogRepository {
    fun getCallLogs(): List<CallLogEntry>
    fun saveCallLog(entry: CallLogEntry)
    fun deleteCallLog(number: String)
    fun deleteCallLogsByIds(ids: List<Long>)
    fun clearCallLogs()

    /** Marks missed calls as seen, so the system stops announcing them. */
    fun markMissedCallsAsRead()
}
