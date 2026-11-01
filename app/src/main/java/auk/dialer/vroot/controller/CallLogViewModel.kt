package auk.dialer.vroot.controller

import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import auk.dialer.vroot.modal.data.CallLogEntry
import auk.dialer.vroot.modal.data.CallLogFilter
import auk.dialer.vroot.modal.data.groupedConsecutive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog

class CallLogViewModel(
    private val callLogRepo: ICallLogRepository,
    private val contentResolver: ContentResolver
) : ViewModel() {

    /** One entry per call, for anything showing or deleting single calls. */
    private val _allCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val allCalls: StateFlow<List<CallLogEntry>> = _allCalls.asStateFlow()

    /** Back-to-back calls with one number folded into a single row, for the recents list. */
    private val _allCallLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val allCallLogs: StateFlow<List<CallLogEntry>> = _allCallLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CallLogFilter.All)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            fetchLogs()
        }
    }

    init {
        try {
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, contentObserver)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setFilter(newFilter: CallLogFilter) {
        _selectedFilter.value = newFilter
    }

    fun fetchLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_allCallLogs.value.isEmpty()) {
                _isLoading.value = true
            }
            val calls = callLogRepo.getCalls()
            _allCalls.value = calls
            _allCallLogs.value = calls.groupedConsecutive()
            _isLoading.value = false
        }
    }

    fun deleteCallLog(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.deleteCallLog(number)
            fetchLogs()
        }
    }

    fun deleteCallLogsByIds(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.deleteCallLogsByIds(ids)
            fetchLogs()
        }
    }

    fun clearCallLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.clearCallLogs()
            fetchLogs()
        }
    }
}
