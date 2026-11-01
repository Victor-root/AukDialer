package auk.dialer.vroot.controller

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import auk.dialer.vroot.BuildConfig
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Records a call from the Shizuku user service process, which runs as the shell (or root) user.
 * Unlike the app, that user holds CAPTURE_AUDIO_OUTPUT, which the VOICE_CALL sources require.
 *
 * Shizuku creates it by reflection through the Context constructor, and nothing of the app process
 * (Koin, preferences) exists here.
 */
class PrivilegedRecorderService(context: Context) : IPrivilegedRecorder.Stub() {

    private val callerContext = CallerContext(context)
    private val lock = Any()

    @Volatile
    private var session: Session? = null

    override fun destroy() {
        stop()
        System.exit(0)
    }

    // Nothing may be thrown from these: past the binder it would crash this process.
    override fun start(output: ParcelFileDescriptor, audioSource: Int): Boolean {
        synchronized(lock) {
            val started = if (session == null) Session.open(callerContext, audioSource, output) else null
            if (started == null) {
                runCatching { output.close() }
                return false
            }
            session = started
            return true
        }
    }

    override fun maxAmplitude(): Int = session?.takePeak() ?: 0

    override fun stop(): Boolean {
        synchronized(lock) {
            val current = session ?: return false
            session = null
            return current.finish()
        }
    }

    /**
     * The audio stack checks the recording permissions against the caller's attribution, which has
     * to name this process's own user and a package of it rather than the app it was loaded from.
     */
    private class CallerContext(base: Context) : ContextWrapper(base) {
        private val uid = Process.myUid()
        private val callerPackage = runCatching {
            base.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()

        override fun getOpPackageName(): String = callerPackage ?: super.getOpPackageName()

        override fun getAttributionSource(): AttributionSource =
            AttributionSource.Builder(uid).setPackageName(callerPackage).build()
    }

    /** One capture: PCM from AudioRecord, encoded to AAC and written to an MPEG-4 file. */
    private class Session(
        private val record: AudioRecord,
        private val codec: MediaCodec,
        private val muxer: MediaMuxer,
        private val output: ParcelFileDescriptor
    ) {
        private val peak = AtomicInteger(0)
        private var track = -1
        private var hasSamples = false

        @Volatile
        private var running = true
        private val thread = Thread({ encode() }, "AukPrivilegedRecorder").apply { start() }

        fun takePeak(): Int = peak.getAndSet(0)

        fun finish(): Boolean {
            running = false
            thread.join()
            val written = runCatching {
                if (track >= 0) muxer.stop()
                hasSamples
            }.getOrDefault(false)
            release(record, codec, muxer, output)
            return written
        }

        private fun encode() {
            val info = MediaCodec.BufferInfo()
            var totalSamples = 0L
            runCatching {
                while (running) {
                    val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index) ?: break
                        input.clear()
                        val read = record.read(input, minOf(input.remaining(), READ_SIZE_BYTES))
                        if (read < 0) break
                        val samples = input.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
                        var chunkPeak = 0
                        for (i in 0 until read / BYTES_PER_SAMPLE) {
                            chunkPeak = maxOf(chunkPeak, abs(samples.get(i).toInt()))
                        }
                        peak.accumulateAndGet(chunkPeak) { a, b -> maxOf(a, b) }
                        codec.queueInputBuffer(index, 0, read, totalSamples * 1_000_000L / SAMPLE_RATE, 0)
                        totalSamples += read / BYTES_PER_SAMPLE
                    }
                    drain(info, endOfStream = false)
                }
                record.stop()
                val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (index >= 0) {
                    codec.queueInputBuffer(
                        index, 0, 0, totalSamples * 1_000_000L / SAMPLE_RATE,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    drain(info, endOfStream = true)
                }
            }
        }

        private fun drain(info: MediaCodec.BufferInfo, endOfStream: Boolean) {
            var idleRounds = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (endOfStream) CODEC_TIMEOUT_US else 0)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || ++idleRounds >= MAX_END_OF_STREAM_ROUNDS) return
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val data = codec.getOutputBuffer(index)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (data != null && !isConfig && info.size > 0 && track >= 0) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                            hasSamples = true
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        companion object {
            private const val TAG = "AukPrivilegedRecorder"
            private const val SAMPLE_RATE = 44100
            private const val BIT_RATE = 128000
            private const val BYTES_PER_SAMPLE = 2
            private const val READ_SIZE_BYTES = 2048
            private const val CODEC_TIMEOUT_US = 10_000L
            private const val MAX_END_OF_STREAM_ROUNDS = 100

            /** Starts a capture, or returns null with everything it opened released. */
            fun open(context: Context, audioSource: Int, output: ParcelFileDescriptor): Session? {
                var record: AudioRecord? = null
                var codec: MediaCodec? = null
                var muxer: MediaMuxer? = null
                return try {
                    record = createAudioRecord(context, audioSource)
                    codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                        configure(encoderFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        start()
                    }
                    muxer = MediaMuxer(output.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) error("capture refused")
                    Session(record, codec, muxer, output)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG_TOOLS) {
                        Log.d(TAG, "source $audioSource refused for uid ${Process.myUid()}", e)
                    }
                    release(record, codec, muxer, output)
                    null
                }
            }

            private fun release(record: AudioRecord?, codec: MediaCodec?, muxer: MediaMuxer?, output: ParcelFileDescriptor) {
                runCatching { record?.release() }
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { muxer?.release() }
                runCatching { output.close() }
            }

            // Shell and root hold RECORD_AUDIO themselves, the app's own grant plays no part.
            @SuppressLint("MissingPermission")
            private fun createAudioRecord(context: Context, audioSource: Int): AudioRecord {
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                return AudioRecord.Builder()
                    .setContext(context)
                    .setAudioSource(audioSource)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer, READ_SIZE_BYTES) * 4)
                    .build()
            }

            private fun encoderFormat(): MediaFormat =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, READ_SIZE_BYTES * 4)
                }
        }
    }
}
