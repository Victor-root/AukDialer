package com.grinch.rivo4.controller.vvm

/**
 * Parsed OMTP 1.3 STATUS message. Every field is nullable: a real reply does not
 * necessarily fill all of them (an "unknown user" state legitimately omits
 * credentials), so the parser turns absent or unparseable fields into null
 * rather than throwing.
 */
data class OmtpStatusMessage(
    val provisioningState: ProvisioningState,
    val returnCode: String?,
    val subscriberType: String?,
    val imapServer: String?,
    val imapPort: Int?,
    val smtpServer: String?,
    val smtpPort: Int?,
    val imapUsername: String?,
    val imapPassword: String?,
    val smtpUsername: String?,
    val smtpPassword: String?,
    val tuiAccessNumber: String?,
    val clientSmsDestinationNumber: String?,
    val language: String?,
    val maxGreetingLengthSeconds: Int?,
    val maxVoicemailLengthSeconds: Int?,
    /**
     * True when the carrier IMAP endpoint expects TLS from the first byte
     * ("imaps", usually port 993), false for plaintext IMAP with optional
     * STARTTLS. Filled from CarrierConfig, since STATUS replies don't carry it.
     */
    val imapUseSsl: Boolean = false,
) {
    /**
     * A STATUS reply is usable only when the carrier acknowledges the subscriber
     * AND ships IMAP credentials we can log in with.
     */
    fun hasUsableImapCredentials(): Boolean {
        return provisioningState.isProvisioned() &&
            !imapServer.isNullOrBlank() &&
            (imapPort ?: 0) > 0 &&
            !imapUsername.isNullOrBlank() &&
            !imapPassword.isNullOrBlank()
    }
}

/** OMTP 1.3 table 4: the STATUS "st" field. Codes are sent verbatim on the wire. */
enum class ProvisioningState(val omtpCode: String) {
    NEW_USER("N"),
    READY("R"),

    /** Some carriers send P instead of R. Treated identically. */
    PROVISIONED("P"),
    BLOCKED("B"),
    UNKNOWN_USER("U"),
    UNKNOWN("?");

    fun isProvisioned(): Boolean = this == READY || this == PROVISIONED

    companion object {
        fun fromCode(code: String?): ProvisioningState {
            if (code.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.omtpCode.equals(code.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }
}
