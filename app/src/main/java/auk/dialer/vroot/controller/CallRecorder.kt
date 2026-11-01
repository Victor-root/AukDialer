package auk.dialer.vroot.controller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
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

    private var capture: Capture? = null
    private var currentFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Bumped by every start() and stop(), so a source still being checked when the
     * user stops (or starts again) can tell it was superseded and back off instead
     * of resurrecting a recording the user already cancelled. */
    @Volatile
    private var attemptId = 0

    /** True from the moment start() is called until a source is confirmed, rejected
     * for good, or the attempt is superseded. isRecording alone cannot guard against
     * a second start() call: it is not set until a source is confirmed, which can
     * take a couple of seconds now that each source gets checked for real signal, so
     * a second tap in that window used to launch a second, competing attempt at the
     * same file. */
    @Volatile
    private var attemptInProgress = false

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

    /** A recording under way, whichever process captures it. */
    private interface Capture {
        /** The highest level since the previous call. */
        fun maxAmplitude(): Int

        /** Ends the capture. False when nothing usable was written. */
        fun stop(): Boolean
    }

    /**
     * One way to record: a source, captured by the app itself or through Shizuku. A capture whose
     * level stays at or below [silenceThreshold] is taken as muted, and null skips that check.
     */
    private class Attempt(val name: String, val silenceThreshold: Int?, val open: (File) -> Capture)

    private fun sourceName(source: Int) = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "unknown($source)"
    }

    private fun localCapture(context: Context, source: Int, file: File): Capture {
        val instance = MediaRecorder(context)
        try {
            instance.setAudioSource(source)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioEncodingBitRate(128000)
            instance.setAudioSamplingRate(44100)
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
        } catch (e: Exception) {
            try { instance.reset() } catch (ignored: Exception) {}
            try { instance.release() } catch (ignored: Exception) {}
            throw e
        }
        return object : Capture {
            override fun maxAmplitude() = runCatching { instance.maxAmplitude }.getOrDefault(0)

            override fun stop(): Boolean = try {
                instance.stop()
                true
            } catch (e: Exception) {
                false
            } finally {
                try { instance.reset() } catch (ignored: Exception) {}
                try { instance.release() } catch (ignored: Exception) {}
            }
        }
    }

    private fun privilegedCapture(remote: IPrivilegedRecorder, source: Int, file: File): Capture {
        val output = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        )
        // The service holds its own copy of the descriptor once the call returns.
        val started = output.use { remote.start(it, source) }
        if (!started) error("Shizuku service refused ${sourceName(source)}")
        return object : Capture {
            override fun maxAmplitude() = runCatching { remote.maxAmplitude() }.getOrDefault(0)

            override fun stop() = runCatching { remote.stop() }.getOrDefault(false)
        }
    }

    private suspend fun peakAmplitude(capture: Capture): Int {
        val deadline = System.currentTimeMillis() + SILENCE_CHECK_WINDOW_MS
        var peak = 0
        while (System.currentTimeMillis() < deadline) {
            delay(SILENCE_CHECK_POLL_MS)
            val level = capture.maxAmplitude()
            if (level > peak) peak = level
            if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, "amplitude sample=$level")
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
        if (_isRecording.value || attemptInProgress) return
        // Through Shizuku, the capture runs under the shell's own grant rather than the app's.
        ShizukuRecorder.refresh()
        if (!hasPermission(context) && ShizukuRecorder.status.value != ShizukuRecorder.Status.Ready) return
        attemptInProgress = true

        val safeLabel = label
            .replace(Regex("[^\\p{L}\\p{N}+_-]"), "_")
            .take(40)
            .ifBlank { "call" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(getRecordingsDirectory(context), "${safeLabel}_$stamp.m4a")
        val thisAttempt = ++attemptId

        scope.launch {
            // The call itself, captured through Shizuku when it is set up, ahead of what the app
            // can capture on its own.
            val privileged = ShizukuRecorder.recorder()
            if (BuildConfig.DEBUG_TOOLS) {
                Log.d(TAG, "Shizuku status=${ShizukuRecorder.status.value} service=${privileged != null}")
            }
            val attempts = buildList {
                // A muted capture reads exact zeros, while a live call line always carries some
                // noise, even when nobody is talking during the check.
                if (privileged != null) {
                    add(Attempt("Shizuku VOICE_CALL", silenceThreshold = 0) {
                        privilegedCapture(privileged, MediaRecorder.AudioSource.VOICE_CALL, it)
                    })
                }
                audioSources.forEach { source ->
                    val threshold = if (source == MediaRecorder.AudioSource.MIC) null else SILENCE_AMPLITUDE_THRESHOLD
                    add(Attempt(sourceName(source), threshold) { localCapture(context, source, it) })
                }
            }

            for (attempt in attempts) {
                if (attemptId != thisAttempt) return@launch

                var opened: Capture? = null
                try {
                    opened = attempt.open(file)
                    val threshold = attempt.silenceThreshold
                    if (threshold != null) {
                        val peak = peakAmplitude(opened)
                        if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, "source=${attempt.name} peak=$peak threshold=$threshold")
                        if (peak <= threshold) error("no signal from ${attempt.name}")
                    }
                    if (attemptId != thisAttempt) error("superseded")

                    capture = opened
                    currentFile = file
                    attemptInProgress = false
                    _isRecording.value = true
                    return@launch
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, "source=${attempt.name} rejected: ${e.message}")
                    opened?.stop()
                    if (file.exists()) file.delete()
                }
            }
            if (attemptId == thisAttempt) {
                attemptInProgress = false
                if (BuildConfig.DEBUG_TOOLS) Log.d(TAG, "no source produced a signal")
            }
        }
    }

    fun stop(): File? {
        attemptId++
        attemptInProgress = false
        val active = capture ?: run {
            _isRecording.value = false
            return null
        }
        var saved = currentFile
        if (!active.stop()) {
            saved?.delete()
            saved = null
        }
        capture = null
        currentFile = null
        _isRecording.value = false
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
