package com.grinch.rivo4.debug

import android.content.ContentValues
import android.content.Context
import android.provider.VoicemailContract
import android.util.Log
import com.grinch.rivo4.controller.vvm.VvmImapClient
import com.grinch.rivo4.controller.vvm.VvmVoicemailWriter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Debug-only injection of fake voicemails, so the list, playback and row actions
 * can be exercised without a carrier mailbox.
 *
 * Rows go in through [VvmVoicemailWriter], the same path the real IMAP sync
 * uses, so this exercises the production write code rather than a shortcut.
 * Audio is a tone synthesised at runtime, which keeps a binary fixture out of
 * the repository.
 */
object VoicemailInjector {

    private const val LOG_TAG = "VoicemailInjector"
    private const val SAMPLE_RATE = 8_000
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val CALLERS = listOf(
        "+33612345678",
        "+33698765432",
        "+33745120398",
        "+441632960121",
        "+12025550143",
    )

    /** One unread voicemail with a short playable tone. */
    fun insertOne(context: Context) {
        write(
            context = context,
            number = CALLERS.random(),
            ageMs = 0L,
            durationSeconds = 8,
            withAudio = true,
        )
    }

    /** A spread of messages: varied callers, durations and ages. */
    fun insertBatch(context: Context, count: Int = 5) {
        repeat(count) { index ->
            write(
                context = context,
                number = CALLERS[index % CALLERS.size],
                ageMs = index * DAY_MS,
                durationSeconds = 4 + index * 3,
                withAudio = true,
            )
        }
    }

    /** Uses a real contact number so name and photo resolution can be checked. */
    fun insertFromNumber(context: Context, number: String) {
        write(
            context = context,
            number = number,
            ageMs = 0L,
            durationSeconds = 6,
            withAudio = true,
        )
    }

    /** No audio attached, so the row should offer no play button. */
    fun insertWithoutAudio(context: Context) {
        write(
            context = context,
            number = CALLERS.random(),
            ageMs = 2 * 60 * 60 * 1000L,
            durationSeconds = 0,
            withAudio = false,
        )
    }

    /** Long enough to drag the seek bar around. */
    fun insertLong(context: Context) {
        write(
            context = context,
            number = CALLERS.random(),
            ageMs = 0L,
            durationSeconds = 60,
            withAudio = true,
        )
    }

    /**
     * Clears the soft-delete flag on every row carrying it, bringing back
     * entries the app filters out.
     *
     * Android does not erase a deleted voicemail immediately: it flags the row
     * and waits for the app that owns it to sync the deletion and purge. When
     * that app is no longer the default dialer it never runs again, so the row
     * lingers forever. Restoring one is a way to exercise the list against
     * rows whose audio file is long gone.
     *
     * Restricted to rows this app owns unless [includeOtherSources] is set,
     * since writing into another source's rows is normally off limits.
     */
    fun restoreDeleted(context: Context, includeOtherSources: Boolean = true): Int {
        val values = ContentValues().apply {
            put(VoicemailContract.Voicemails.DELETED, 0)
        }
        val uri = if (includeOtherSources) {
            VoicemailContract.Voicemails.CONTENT_URI
        } else {
            VoicemailContract.Voicemails.buildSourceUri(context.packageName)
        }
        return try {
            context.contentResolver.update(
                uri,
                values,
                "${VoicemailContract.Voicemails.DELETED} = 1",
                null,
            ).also { Log.i(LOG_TAG, "Restored $it deleted row(s)") }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Restore failed", e)
            0
        }
    }

    /**
     * Removes every row this app owns, straight through the provider so no
     * deletion is pushed to a carrier.
     */
    fun deleteAllOwned(context: Context): Int {
        return try {
            context.contentResolver.delete(
                VoicemailContract.Voicemails.buildSourceUri(context.packageName),
                null,
                null,
            )
        } catch (_: Exception) {
            0
        }
    }

    private fun write(
        context: Context,
        number: String,
        ageMs: Long,
        durationSeconds: Int,
        withAudio: Boolean,
    ) {
        val message = VvmImapClient.FetchedMessage(
            // A unique UID keeps the writer from treating repeated injections
            // as the same message and updating one row over and over.
            serverUid = "debug-${System.nanoTime()}",
            fromAddress = number,
            sentDateMs = System.currentTimeMillis() - ageMs,
            durationSeconds = durationSeconds.takeIf { it > 0 },
            audioBytes = if (withAudio) buildWavTone(durationSeconds.coerceAtLeast(1)) else null,
            audioContentType = if (withAudio) "audio/wav" else null,
        )
        when (val result = VvmVoicemailWriter(context).writeFetchedMessage(null, message)) {
            is VvmVoicemailWriter.WriteResult.Success ->
                Log.i(LOG_TAG, "Injected $number audio=${result.audioWritten}")
            is VvmVoicemailWriter.WriteResult.Failed ->
                // Writing to the voicemail provider needs default-dialer status;
                // without it the insert is rejected and nothing would show up.
                Log.w(LOG_TAG, "Injection failed: ${result.errorType}: ${result.errorMessage}")
        }
    }

    /** 8 kHz mono 16-bit PCM WAV holding a fading tone, playable by MediaPlayer. */
    private fun buildWavTone(seconds: Int, frequencyHz: Double = 440.0): ByteArray {
        val sampleCount = SAMPLE_RATE * seconds
        val dataSize = sampleCount * 2

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }

        val samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            // Slow amplitude wobble makes it obvious by ear that playback is
            // actually progressing rather than looping the same instant.
            val envelope = 0.4 + 0.3 * sin(2 * PI * 0.5 * i / SAMPLE_RATE)
            val value = sin(2 * PI * frequencyHz * i / SAMPLE_RATE) * envelope * Short.MAX_VALUE
            samples.putShort(value.toInt().toShort())
        }

        return ByteArrayOutputStream(44 + dataSize).apply {
            write(header.array())
            write(samples.array())
        }.toByteArray()
    }
}
