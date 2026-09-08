package auk.dialer.vroot.controller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import auk.dialer.vroot.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallRecorder {

    private const val TAG = "AukCallRecorder"

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Bumped by every start() and stop(), so a source still being checked when the
     * user stops (or starts again) can tell it was superseded and back off instead
     * of resurrecting a recording the user already cancelled. */
    @Volatile
    private var attemptId = 0

    private val audioSources = listOf(
        MediaRecorder.AudioSource.VOICE_CALL,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC
    )

    // VOICE_CALL and the other privileged sources are often accepted without throwing on
    // modern Android, but the OS mutes the captured stream instead of rejecting it
    // outright, so a started recorder is not proof of a real signal. Sampling the level
    // for a moment catches that and lets a silent source fall through to the next one,
    // the same way a thrown exception already did.
    private const val SILENCE_CHECK_WINDOW_MS = 800L
    private const val SILENCE_CHECK_POLL_MS = 100L
    private const val SILENCE_AMPLITUDE_THRESHOLD = 300

    private fun sourceName(source: Int) = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "unknown($source)"
    }

    private suspend fun peakAmplitude(instance: MediaRecorder): Int {
        val deadline = System.currentTimeMillis() + SILENCE_CHECK_WINDOW_MS
        var peak = 0
        while (System.currentTimeMillis() < deadline) {
            delay(SILENCE_CHECK_POLL_MS)
            val level = runCatching { instance.maxAmplitude }.getOrDefault(0)
            if (level > peak) peak = level
            if (BuildConfig.DEBUG) Log.d(TAG, "amplitude sample=$level")
        }
        return peak
    }

    fun getRecordingsDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val dir = File(base, "CallRecordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(context: Context, label: String) {
        if (_isRecording.value) return
        if (!hasPermission(context)) return

        val safeLabel = label
            .replace(Regex("[^\\p{L}\\p{N}+_-]"), "_")
            .take(40)
            .ifBlank { "call" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(getRecordingsDirectory(context), "${safeLabel}_$stamp.m4a")
        val thisAttempt = ++attemptId

        scope.launch {
            for (source in audioSources) {
                if (attemptId != thisAttempt) return@launch

                val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                try {
                    instance.setAudioSource(source)
                    instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    instance.setAudioEncodingBitRate(128000)
                    instance.setAudioSamplingRate(44100)
                    instance.setOutputFile(file.absolutePath)
                    instance.prepare()
                    instance.start()

                    val peak = if (source == MediaRecorder.AudioSource.MIC) {
                        Int.MAX_VALUE
                    } else {
                        peakAmplitude(instance)
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "source=${sourceName(source)} peak=$peak threshold=$SILENCE_AMPLITUDE_THRESHOLD")
                    }
                    if (peak <= SILENCE_AMPLITUDE_THRESHOLD) error("no signal from ${sourceName(source)}")
                    if (attemptId != thisAttempt) error("superseded")

                    recorder = instance
                    currentFile = file
                    _isRecording.value = true
                    return@launch
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "source=${sourceName(source)} rejected: ${e.message}")
                    try { instance.reset() } catch (ignored: Exception) {}
                    try { instance.release() } catch (ignored: Exception) {}
                    if (file.exists()) file.delete()
                }
            }
            if (BuildConfig.DEBUG && attemptId == thisAttempt) Log.d(TAG, "no source produced a signal")
        }
    }

    fun stop(): File? {
        attemptId++
        val instance = recorder ?: run {
            _isRecording.value = false
            return null
        }
        var saved = currentFile
        try {
            instance.stop()
        } catch (e: Exception) {
            saved?.delete()
            saved = null
        } finally {
            try { instance.reset() } catch (ignored: Exception) {}
            try { instance.release() } catch (ignored: Exception) {}
            recorder = null
            currentFile = null
            _isRecording.value = false
        }
        if (saved != null && (!saved.exists() || saved.length() == 0L)) {
            saved.delete()
            return null
        }
        return saved
    }

    fun listRecordings(context: Context): List<File> {
        val dir = getRecordingsDirectory(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun delete(file: File): Boolean = file.delete()

    fun uriFor(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun play(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    fun share(context: Context, file: File, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }
}
