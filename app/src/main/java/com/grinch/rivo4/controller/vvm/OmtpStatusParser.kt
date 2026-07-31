package com.grinch.rivo4.controller.vvm

import android.os.Bundle

/**
 * Parses the pre-decoded VisualVoicemailSms.fields Bundle handed to
 * [RivoVisualVoicemailService]. Android already tokenizes the OMTP body, so we
 * only map the OMTP field keys (spec 1.3 section 2.2) to our model.
 */
object OmtpStatusParser {

    const val PREFIX_STATUS = "STATUS"

    private val KNOWN_FIELD_KEYS = setOf(
        "st", "rc", "v", "g_len", "vs_len", "srv", "ipt", "spt",
        "u", "pw", "smtp_u", "smtp_pw", "pw_len", "tui", "dn", "lang",
    )

    fun parseStatus(prefix: String?, fields: Bundle?): OmtpStatusMessage? {
        if (prefix == null || !prefix.equals(PREFIX_STATUS, ignoreCase = true)) return null
        if (fields == null || fields.isEmpty) return null

        return OmtpStatusMessage(
            provisioningState = ProvisioningState.fromCode(fields.getString(KEY_STATE)),
            returnCode = fields.getString(KEY_RETURN_CODE),
            subscriberType = fields.getString(KEY_SUBSCRIBER_TYPE),
            imapServer = fields.getString(KEY_SERVER),
            imapPort = fields.getString(KEY_IMAP_PORT)?.toIntOrNull(),
            // OMTP allows a distinct SMTP host, but carriers reuse the same one.
            smtpServer = fields.getString(KEY_SMTP_SERVER) ?: fields.getString(KEY_SERVER),
            smtpPort = fields.getString(KEY_SMTP_PORT)?.toIntOrNull(),
            imapUsername = fields.getString(KEY_IMAP_USERNAME),
            imapPassword = fields.getString(KEY_IMAP_PASSWORD),
            smtpUsername = fields.getString(KEY_SMTP_USERNAME),
            smtpPassword = fields.getString(KEY_SMTP_PASSWORD),
            tuiAccessNumber = fields.getString(KEY_TUI_NUMBER),
            clientSmsDestinationNumber = fields.getString(KEY_DESTINATION_NUMBER),
            language = fields.getString(KEY_LANGUAGE),
            maxGreetingLengthSeconds = fields.getString(KEY_MAX_GREETING_LENGTH)?.toIntOrNull(),
            maxVoicemailLengthSeconds = fields.getString(KEY_MAX_VOICEMAIL_LENGTH)?.toIntOrNull(),
        )
    }

    /**
     * Log-safe summary listing which keys were present. Never echoes a value:
     * the fields Bundle carries the IMAP username and password.
     */
    fun describeFieldsRedacted(fields: Bundle?): String {
        if (fields == null || fields.isEmpty) return "<empty>"
        val known = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        for (key in fields.keySet()) {
            if (key in KNOWN_FIELD_KEYS) known.add(key) else unknown.add(key)
        }
        return buildString {
            append("known=[").append(known.sorted().joinToString(",")).append(']')
            if (unknown.isNotEmpty()) {
                append(" unknown=[").append(unknown.sorted().joinToString(",")).append(']')
            }
        }
    }

    const val KEY_STATE = "st"
    const val KEY_RETURN_CODE = "rc"
    const val KEY_SUBSCRIBER_TYPE = "sub"
    const val KEY_SERVER = "srv"
    const val KEY_IMAP_PORT = "ipt"
    const val KEY_SMTP_SERVER = "smtp_srv"
    const val KEY_SMTP_PORT = "spt"
    const val KEY_IMAP_USERNAME = "u"
    const val KEY_IMAP_PASSWORD = "pw"
    const val KEY_SMTP_USERNAME = "smtp_u"
    const val KEY_SMTP_PASSWORD = "smtp_pw"
    const val KEY_TUI_NUMBER = "tui"
    const val KEY_DESTINATION_NUMBER = "dn"
    const val KEY_LANGUAGE = "lang"
    const val KEY_MAX_GREETING_LENGTH = "g_len"
    const val KEY_MAX_VOICEMAIL_LENGTH = "vs_len"
}
