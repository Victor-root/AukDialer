package auk.dialer.vroot.controller

import auk.dialer.vroot.modal.`interface`.ICallLogRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import auk.dialer.vroot.modal.data.CallLogEntry
import auk.dialer.vroot.modal.data.CallLogFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CallLogFilter.All)
    val selectedFilter = _selectedFilter.asStateFlow()

    // The system writes the log several times as a call ends: those changes are taken as one.
    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reload(CHANGE_DEBOUNCE_MS)
        }
    }

    private var fetchJob: Job? = null

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

    fun fetchLogs() = reload(0)

    /** One load at a time, the latest: an older one finishing last would bring back deleted calls. */
    private fun reload(debounceMs: Long) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(debounceMs)
            if (_allCalls.value.isEmpty()) {
                _isLoading.value = true
            }
            val calls = callLogRepo.getCalls()
            ensureActive()
            _allCalls.value = calls
            _isLoading.value = false
        }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 250L
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
