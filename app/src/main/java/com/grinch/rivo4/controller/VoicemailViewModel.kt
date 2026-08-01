package com.grinch.rivo4.controller

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.VoicemailContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grinch.rivo4.controller.util.VoicemailPlayer
import com.grinch.rivo4.modal.`interface`.IVoicemailRepository
import com.grinch.rivo4.modal.data.Voicemail
import com.grinch.rivo4.modal.data.VoicemailStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackState(
    val playingId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
)

class VoicemailViewModel(
    private val voicemailRepo: IVoicemailRepository,
    private val contentResolver: ContentResolver,
    context: Context
) : ViewModel() {

    private val _voicemails = MutableStateFlow<List<Voicemail>>(emptyList())
    val voicemails: StateFlow<List<Voicemail>> = _voicemails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    /** One-shot user-facing message, consumed by the screen's snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _audioUnavailable = MutableStateFlow(false)
    val audioUnavailable: StateFlow<Boolean> = _audioUnavailable.asStateFlow()

    /** Loudspeaker by default, as when playing back any recording. */
    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _status = MutableStateFlow(VoicemailStatus.Ready)
    val status: StateFlow<VoicemailStatus> = _status.asStateFlow()

    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()

    private val player = VoicemailPlayer(
        context = context.applicationContext,
        onUpdate = { playingId, isPlaying, positionMs, durationMs ->
            _playback.value = PlaybackState(playingId, isPlaying, positionMs, durationMs)
        },
        onError = { _message.value = it },
        onAudioUnavailable = { _audioUnavailable.value = true },
    )

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            fetchVoicemails()
        }
    }

    init {
        try {
            contentResolver.registerContentObserver(
                VoicemailContract.Voicemails.CONTENT_URI,
                true,
                contentObserver,
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
        try {
            contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isDefaultDialer(): Boolean = voicemailRepo.isDefaultDialer()

    fun fetchVoicemails() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Status is read alongside the rows so the screen never shows an
                // empty list without knowing whether that is normal.
                _status.value = voicemailRepo.getStatus()
                voicemailRepo.getVoicemails()
            }
            _voicemails.value = result
            _isLoading.value = false
        }
    }

    /** Registers the carrier SMS filter. Safe to call on every screen entry. */
    fun registerSmsFilter() {
        viewModelScope.launch(Dispatchers.IO) {
            voicemailRepo.registerSmsFilter()
        }
    }

    fun syncNow(onResult: (newCount: Int?) -> Unit = {}) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = withContext(Dispatchers.IO) { voicemailRepo.syncNow() }
            _isSyncing.value = false
            onResult(result.getOrNull())
            fetchVoicemails()
            // A list that already has messages hides a broken sync, so the
            // failure is announced rather than left to the empty state.
            if (result.isFailure) _syncFailed.value = true
        }
    }

    fun requestProvisioning(onResult: (anySent: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { voicemailRepo.requestProvisioning() }
            onResult(results.any { it.success })
        }
    }

    fun togglePlayback(voicemail: Voicemail) {
        player.toggle(voicemail.id)
        if (!voicemail.isRead) {
            markAsRead(voicemail.id, true)
        }
    }

    fun seekTo(positionMs: Int) = player.seekTo(positionMs)

    fun toggleSpeaker() {
        val enabled = !_isSpeakerOn.value
        _isSpeakerOn.value = enabled
        player.setSpeakerOn(enabled)
    }

    fun markAsRead(id: Long, isRead: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { voicemailRepo.markAsRead(id, isRead) }
            fetchVoicemails()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            if (_playback.value.playingId == id) {
                player.release()
            }
            withContext(Dispatchers.IO) { voicemailRepo.delete(id) }
            fetchVoicemails()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeAudioUnavailable() {
        _audioUnavailable.value = false
    }

    fun consumeSyncFailed() {
        _syncFailed.value = false
    }
}
