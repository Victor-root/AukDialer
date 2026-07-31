package com.grinch.rivo4.controller.vvm

import android.os.Bundle

/**
 * Parsed OMTP 1.3 SYNC message. The carrier emits it whenever the mailbox
 * changes. We only act on [eventType]: the SYNC SMS is a wake-up signal, the
 * authoritative state always comes from the following IMAP refresh.
 */
data class OmtpSyncMessage(
    val eventType: SyncEventType,
    val messageUid: String?,
    val messageCount: Int?,
    val messageType: String?,
    val durationSeconds: Int?,
) {
    fun shouldTriggerSync(): Boolean = eventType in SYNC_TRIGGER_EVENTS

    companion object {
        val SYNC_TRIGGER_EVENTS = setOf(
            SyncEventType.NEW_MESSAGE,
            SyncEventType.MAILBOX_UPDATE,
        )
    }
}

/** OMTP 1.3 table 6: the SYNC "ev" field. */
enum class SyncEventType(val omtpCode: String) {
    NEW_MESSAGE("NM"),

    /** Another mailbox change, typically a message read or deleted from the carrier menu. */
    MAILBOX_UPDATE("MBU"),

    /** Greeting changed. Not acted on: we don't manage greetings. */
    GREETING_UPDATE("GU"),
    UNKNOWN("?");

    companion object {
        fun fromCode(code: String?): SyncEventType {
            if (code.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.omtpCode.equals(code.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }
}

object OmtpSyncParser {
    const val PREFIX_SYNC = "SYNC"

    private const val KEY_EVENT = "ev"
    private const val KEY_MESSAGE_UID = "id"
    private const val KEY_COUNT = "c"
    private const val KEY_TYPE = "t"
    private const val KEY_LENGTH = "l"

    fun parseSync(prefix: String?, fields: Bundle?): OmtpSyncMessage? {
        if (prefix == null || !prefix.equals(PREFIX_SYNC, ignoreCase = true)) return null
        if (fields == null || fields.isEmpty) return null

        return OmtpSyncMessage(
            eventType = SyncEventType.fromCode(fields.getString(KEY_EVENT)),
            messageUid = fields.getString(KEY_MESSAGE_UID),
            messageCount = fields.getString(KEY_COUNT)?.toIntOrNull(),
            messageType = fields.getString(KEY_TYPE),
            durationSeconds = fields.getString(KEY_LENGTH)?.toIntOrNull(),
        )
    }
}
