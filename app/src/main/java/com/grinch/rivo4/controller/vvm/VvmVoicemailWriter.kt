package com.grinch.rivo4.controller.vvm

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.VoicemailContract
import java.io.IOException

/**
 * Persists one IMAP-fetched voicemail into VoicemailContract.Voicemails:
 *
 *  1. Insert the row with HAS_CONTENT=0 and SOURCE_DATA carrying the IMAP UID,
 *     which is what lets the next sync dedupe.
 *  2. Stream the audio into the row through the provider's output stream.
 *  3. Flip HAS_CONTENT to 1 so playback can pick the row up.
 *
 * Every write is scoped to our own source URI, so voicemails belonging to
 * another app stay untouched.
 */
class VvmVoicemailWriter(private val context: Context) {

    sealed class WriteResult {
        data class Success(val rowUri: Uri, val audioWritten: Boolean) : WriteResult()
        data class Failed(val errorType: String, val errorMessage: String) : WriteResult()
    }

    fun writeFetchedMessage(phoneAccountId: String?, message: VvmImapClient.FetchedMessage): WriteResult {
        return try {
            val sourceUri = VoicemailContract.Voicemails.buildSourceUri(context.packageName)
            // If a previous sync inserted this UID but failed to attach audio,
            // reuse that row instead of inserting a duplicate: otherwise the
            // dedupe pass would skip the very row that needs retrying.
            val existingRowId = findExistingRowId(sourceUri, phoneAccountId, message.serverUid)
            val values = baseContentValues(phoneAccountId, message)
            val rowUri = if (existingRowId != null) {
                val uri = ContentUris.withAppendedId(sourceUri, existingRowId)
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                context.contentResolver.insert(sourceUri, values)
                    ?: return WriteResult.Failed("InsertFailed", "ContentResolver.insert returned null")
            }

            val audioWritten = writeAudioBytesIfAny(rowUri, message.audioBytes)
            if (audioWritten) {
                context.contentResolver.update(
                    rowUri,
                    ContentValues().apply { put(VoicemailContract.Voicemails.HAS_CONTENT, 1) },
                    null,
                    null,
                )
            }
            WriteResult.Success(rowUri, audioWritten)
        } catch (e: Exception) {
            WriteResult.Failed(e.javaClass.simpleName, e.message ?: "<no message>")
        }
    }

    private fun baseContentValues(
        phoneAccountId: String?,
        message: VvmImapClient.FetchedMessage,
    ): ContentValues {
        return ContentValues().apply {
            put(VoicemailContract.Voicemails.SOURCE_PACKAGE, context.packageName)
            put(VoicemailContract.Voicemails.SOURCE_DATA, message.serverUid)
            phoneAccountId?.let { put(VoicemailContract.Voicemails.PHONE_ACCOUNT_ID, it) }
            put(VoicemailContract.Voicemails.NUMBER, message.fromAddress ?: "")
            put(VoicemailContract.Voicemails.DATE, message.sentDateMs ?: System.currentTimeMillis())
            message.durationSeconds?.let { put(VoicemailContract.Voicemails.DURATION, it.toLong()) }
            put(VoicemailContract.Voicemails.IS_READ, 0)
            put(VoicemailContract.Voicemails.HAS_CONTENT, 0)
            message.audioContentType?.let { put(VoicemailContract.Voicemails.MIME_TYPE, it) }
        }
    }

    private fun findExistingRowId(sourceUri: Uri, phoneAccountId: String?, serverUid: String): Long? {
        val projection = arrayOf(VoicemailContract.Voicemails._ID)
        val (selection, args) = if (phoneAccountId.isNullOrBlank()) {
            "${VoicemailContract.Voicemails.SOURCE_DATA}=?" to arrayOf(serverUid)
        } else {
            "${VoicemailContract.Voicemails.PHONE_ACCOUNT_ID}=? AND ${VoicemailContract.Voicemails.SOURCE_DATA}=?" to
                arrayOf(phoneAccountId, serverUid)
        }
        return try {
            context.contentResolver.query(sourceUri, projection, selection, args, null)?.use { c ->
                if (c.moveToNext()) c.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeAudioBytesIfAny(rowUri: Uri, bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        return try {
            val stream = context.contentResolver.openOutputStream(rowUri, "w")
                ?: throw IOException("openOutputStream returned null")
            stream.use { it.write(bytes) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Server UIDs we already hold **with audio**. Rows whose audio fetch failed
     * (HAS_CONTENT=0) are deliberately excluded so the next sync retries them.
     */
    fun knownServerUids(phoneAccountId: String?): Set<String> {
        val sourceUri = VoicemailContract.Voicemails.buildSourceUri(context.packageName)
        val projection = arrayOf(VoicemailContract.Voicemails.SOURCE_DATA)
        val contentFilter = "${VoicemailContract.Voicemails.HAS_CONTENT}=1"
        val (selection, args) = if (phoneAccountId.isNullOrBlank()) {
            contentFilter to null
        } else {
            "${VoicemailContract.Voicemails.PHONE_ACCOUNT_ID}=? AND $contentFilter" to arrayOf(phoneAccountId)
        }
        return try {
            context.contentResolver.query(sourceUri, projection, selection, args, null)?.use { c ->
                val out = mutableSetOf<String>()
                while (c.moveToNext()) {
                    c.getString(0)?.takeIf { it.isNotBlank() }?.let(out::add)
                }
                out
            } ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
