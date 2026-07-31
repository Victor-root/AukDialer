package com.grinch.rivo4.controller.vvm

import android.util.Log
import com.sun.mail.imap.IMAPFolder
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.MimeBodyPart

/**
 * IMAP client for the carrier voicemail mailbox, configured from the
 * per-subscription credentials shipped in the OMTP STATUS reply.
 *
 * Every call here is blocking I/O: callers MUST run on a background thread.
 * This class never spawns threads of its own.
 */
class VvmImapClient(private val credentials: OmtpStatusMessage) {

    sealed class HealthCheckResult {
        data class Success(val inboxMessageCount: Int, val inboxUnseenCount: Int) : HealthCheckResult()
        data class Failed(val errorType: String, val errorMessage: String) : HealthCheckResult()
    }

    /**
     * One decoded IMAP message. [audioBytes] holds the first MIME part whose
     * Content-Type starts with "audio", or null when the message carries none.
     */
    data class FetchedMessage(
        val serverUid: String,
        val fromAddress: String?,
        val sentDateMs: Long?,
        val durationSeconds: Int?,
        val audioBytes: ByteArray?,
        val audioContentType: String?,
    )

    sealed class SyncResult {
        data class Success(val total: Int, val written: Int, val skipped: Int) : SyncResult()
        data class Failed(val errorType: String, val errorMessage: String, val partialWritten: Int) : SyncResult()
    }

    sealed class FlagUpdateResult {
        object Success : FlagUpdateResult()
        data class Failed(val errorType: String, val errorMessage: String) : FlagUpdateResult()
    }

    fun runHealthCheck(): HealthCheckResult {
        val (server, port, username, password) = imapEndpoint()
            ?: return HealthCheckResult.Failed("BadConfig", "credentials incomplete")

        var store: Store? = null
        var inbox: Folder? = null
        return try {
            val session = Session.getInstance(imapProperties(server, port))
            store = session.getStore(protocolStoreName)
            store.connect(server, port, username, password)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val total = inbox.messageCount
            val unseen = try {
                inbox.unreadMessageCount
            } catch (_: Exception) {
                0
            }
            HealthCheckResult.Success(total, unseen)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "IMAP health check failed", e)
            HealthCheckResult.Failed(e.javaClass.simpleName, e.message ?: "<no message>")
        } finally {
            closeSilently(inbox, store)
        }
    }

    /**
     * Walks INBOX, skips every UID [isAlreadyKnown] reports as persisted, and
     * hands each new message to [onNewMessage], which returns true when the
     * caller successfully stored it.
     */
    fun syncNewMessages(
        isAlreadyKnown: (serverUid: String) -> Boolean,
        onNewMessage: (FetchedMessage) -> Boolean,
    ): SyncResult {
        val (server, port, username, password) = imapEndpoint()
            ?: return SyncResult.Failed("BadConfig", "credentials incomplete", 0)

        var store: Store? = null
        var inbox: IMAPFolder? = null
        var totalSeen = 0
        var written = 0
        var skipped = 0
        return try {
            val session = Session.getInstance(imapProperties(server, port))
            store = session.getStore(protocolStoreName)
            store.connect(server, port, username, password)
            inbox = (store.getFolder("INBOX") ?: throw IllegalStateException("INBOX folder unavailable")) as IMAPFolder
            inbox.open(Folder.READ_ONLY)
            val messages = inbox.messages ?: emptyArray()
            totalSeen = messages.size
            if (messages.isNotEmpty()) {
                val profile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add(FetchProfile.Item.FLAGS)
                    add(UIDFolder.FetchProfileItem.UID)
                    // Pre-fetch the full body so reading parts needs no second
                    // roundtrip: some carrier servers hang up between commands.
                    add(IMAPFolder.FetchProfileItem.MESSAGE)
                }
                inbox.fetch(messages, profile)
                for (message in messages) {
                    val uid = try {
                        inbox.getUID(message).toString()
                    } catch (_: Exception) {
                        // Without a UID we cannot dedupe safely, so skip it.
                        continue
                    }
                    if (isAlreadyKnown(uid)) {
                        skipped++
                        continue
                    }
                    val fetched = try {
                        decodeMessage(message, uid)
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Failed to decode message uid=$uid", e)
                        continue
                    }
                    val persisted = try {
                        onNewMessage(fetched)
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Caller persist failed for uid=$uid", e)
                        false
                    }
                    if (persisted) written++
                }
            }
            SyncResult.Success(totalSeen, written, skipped)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "IMAP sync failed", e)
            SyncResult.Failed(e.javaClass.simpleName, e.message ?: "<no message>", written)
        } finally {
            closeSilently(inbox, store)
        }
    }

    private fun decodeMessage(message: javax.mail.Message, uid: String): FetchedMessage {
        val from = message.from?.firstOrNull()?.toString()
        // Carriers populate either sent-date or received-date; fall back to now
        // so the row never lands on the epoch.
        val dateMs = message.sentDate?.time
            ?: message.receivedDate?.time
            ?: System.currentTimeMillis()
        var durationSeconds = parseDurationSeconds(message)

        val audio = try {
            extractAudioFromPart(message, depth = 0)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "extractAudio failed for uid=$uid", e)
            null
        }
        if (durationSeconds == null && audio != null) {
            durationSeconds = audio.duration
        }

        return FetchedMessage(
            serverUid = uid,
            fromAddress = from,
            sentDateMs = dateMs,
            durationSeconds = durationSeconds,
            audioBytes = audio?.bytes,
            audioContentType = audio?.mime,
        )
    }

    private data class ExtractedAudio(val bytes: ByteArray, val mime: String, val duration: Int?)

    /** Depth-first walk of the MIME tree; the first non-empty audio part wins. */
    private fun extractAudioFromPart(part: Part, depth: Int): ExtractedAudio? {
        if (depth > MAX_MIME_DEPTH) return null
        val ctNoParams = (part.contentType ?: "").trim().substringBefore(';').trim()

        if (ctNoParams.startsWith("audio/", ignoreCase = true)) {
            val bytes = readPartBytesWithFallback(part)
            if (bytes.isNotEmpty()) {
                return ExtractedAudio(bytes, ctNoParams, parseDurationSeconds(part))
            }
            return null
        }

        if (ctNoParams.startsWith("multipart/", ignoreCase = true)) {
            val content = try {
                part.content
            } catch (_: Exception) {
                null
            }
            if (content is Multipart) {
                for (i in 0 until content.count) {
                    extractAudioFromPart(content.getBodyPart(i), depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun readPartBytesWithFallback(part: Part): ByteArray {
        // Preferred path: Jakarta Mail hands back the body already decoded from
        // base64 / quoted-printable.
        runCatching {
            ByteArrayOutputStream().use { out ->
                part.inputStream.use { it.copyTo(out) }
                val bytes = out.toByteArray()
                if (bytes.isNotEmpty()) return bytes
            }
        }

        // Fallback for servers whose declared encoding trips the decoder: read
        // the raw stream and decode base64 ourselves.
        if (part is MimeBodyPart) {
            runCatching {
                ByteArrayOutputStream().use { out ->
                    part.rawInputStream.use { it.copyTo(out) }
                    val raw = out.toByteArray()
                    if (raw.isEmpty()) return@runCatching
                    val decoded = when (part.encoding?.lowercase()?.trim() ?: "") {
                        "base64" -> runCatching {
                            android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
                        }.getOrNull() ?: ByteArray(0)
                        else -> raw
                    }
                    if (decoded.isNotEmpty()) return decoded
                }
            }
        }
        return ByteArray(0)
    }

    private fun parseDurationSeconds(part: Part): Int? {
        for (header in DURATION_HEADERS) {
            val values = try {
                part.getHeader(header)
            } catch (_: Exception) {
                null
            }
            val raw = values?.firstOrNull()?.trim() ?: continue
            val parsed = raw.toIntOrNull() ?: raw.filter { it.isDigit() }.toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        return null
    }

    private data class ImapEndpoint(val server: String, val port: Int, val username: String, val password: String)

    private fun imapEndpoint(): ImapEndpoint? {
        val server = credentials.imapServer?.takeIf { it.isNotBlank() } ?: return null
        val port = credentials.imapPort?.takeIf { it > 0 } ?: return null
        val username = credentials.imapUsername?.takeIf { it.isNotBlank() } ?: return null
        val password = credentials.imapPassword?.takeIf { it.isNotBlank() } ?: return null
        return ImapEndpoint(server, port, username, password)
    }

    private val useImaps: Boolean
        get() = credentials.imapUseSsl

    private val protocolStoreName: String
        get() = if (useImaps) "imaps" else "imap"

    private fun imapProperties(server: String, port: Int): Properties {
        val prefix = if (useImaps) "mail.imaps" else "mail.imap"
        return Properties().apply {
            put("$prefix.host", server)
            put("$prefix.port", port.toString())
            put("$prefix.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
            put("$prefix.timeout", READ_TIMEOUT_MS.toString())
            put("$prefix.writetimeout", READ_TIMEOUT_MS.toString())
            if (!useImaps) {
                // Plaintext IMAP: try STARTTLS opportunistically. If the server
                // doesn't advertise it, the library proceeds in cleartext,
                // matching the transport the carrier declared.
                put("$prefix.starttls.enable", "true")
            }
            // Let the library negotiate whichever scheme the carrier accepts.
            put("$prefix.auth.plain.disable", "false")
            put("$prefix.auth.login.disable", "false")
            put("$prefix.sasl.enable", "true")
            put("$prefix.sasl.mechanisms", "PLAIN LOGIN CRAM-MD5 DIGEST-MD5")
            // Carriers rarely support COMPRESS=DEFLATE and probing costs a slow
            // round-trip.
            put("$prefix.compress.enable", "false")
        }
    }

    /**
     * Sets or clears \Seen on the message with [serverUid]. Without this the
     * carrier's phone menu keeps announcing messages the user already played
     * here as new.
     */
    fun setSeenFlag(serverUid: String, seen: Boolean): FlagUpdateResult {
        return runImapWriteAction(expungeOnClose = false) { inbox ->
            val uid = serverUid.toLongOrNull()
                ?: throw IllegalArgumentException("Server UID is not numeric: $serverUid")
            val message = inbox.getMessageByUID(uid)
                ?: throw IllegalStateException("No message on server with UID $uid")
            inbox.setFlags(arrayOf(message), Flags(Flags.Flag.SEEN), seen)
        }
    }

    /** Flags [serverUid] \Deleted and expunges on close, so it is really gone. */
    fun deleteMessage(serverUid: String): FlagUpdateResult {
        return runImapWriteAction(expungeOnClose = true) { inbox ->
            val uid = serverUid.toLongOrNull()
                ?: throw IllegalArgumentException("Server UID is not numeric: $serverUid")
            val message = inbox.getMessageByUID(uid)
                ?: throw IllegalStateException("No message on server with UID $uid")
            inbox.setFlags(arrayOf(message), Flags(Flags.Flag.DELETED), true)
        }
    }

    private fun runImapWriteAction(
        expungeOnClose: Boolean,
        action: (IMAPFolder) -> Unit,
    ): FlagUpdateResult {
        val (server, port, username, password) = imapEndpoint()
            ?: return FlagUpdateResult.Failed("BadConfig", "credentials incomplete")

        var store: Store? = null
        var inbox: IMAPFolder? = null
        return try {
            val session = Session.getInstance(imapProperties(server, port))
            store = session.getStore(protocolStoreName)
            store.connect(server, port, username, password)
            inbox = (store.getFolder("INBOX") ?: throw IllegalStateException("INBOX folder unavailable")) as IMAPFolder
            inbox.open(Folder.READ_WRITE)
            action(inbox)
            FlagUpdateResult.Success
        } catch (e: Exception) {
            Log.w(LOG_TAG, "IMAP write action failed", e)
            FlagUpdateResult.Failed(e.javaClass.simpleName, e.message ?: "<no message>")
        } finally {
            try {
                inbox?.takeIf { it.isOpen }?.close(expungeOnClose)
            } catch (_: Exception) {
            }
            try {
                store?.takeIf { it.isConnected }?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeSilently(inbox: Folder?, store: Store?) {
        try {
            inbox?.takeIf { it.isOpen }?.close(false)
        } catch (_: Exception) {
        }
        try {
            store?.takeIf { it.isConnected }?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val LOG_TAG = "VvmImapClient"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_MIME_DEPTH = 10

        private val DURATION_HEADERS = listOf(
            "Content-Duration",
            "X-Content-Duration",
            "X-Voice-Message-Length",
            "X-CNS-Voice-Message-Length",
        )
    }
}
