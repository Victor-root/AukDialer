package com.grinch.rivo4.modal.data

data class Voicemail(
    val id: Long,
    val number: String?,
    val date: Long,
    val durationSeconds: Int,
    val isRead: Boolean,
    val hasContent: Boolean,
    val sourcePackage: String?,
    val phoneAccountId: String?,
    val contactName: String? = null,
    val photoUri: String? = null,
    /** Display name of the SIM this message arrived on, when it can be resolved. */
    val simLabel: String? = null
) {
    /** True when this row was imported by Rivo rather than another voicemail app. */
    fun isOwnedBy(packageName: String): Boolean = sourcePackage == packageName
}

/**
 * Why voicemail is or is not working, so the screen can say something more
 * useful than showing nothing at all.
 */
enum class VoicemailStatus {
    /** Set up and reachable. An empty list here really means an empty mailbox. */
    Ready,

    /** Another app holds the dialer role, so the carrier never routes us anything. */
    NotDefaultDialer,

    /** The carrier runs a protocol this app does not implement. */
    CarrierUnsupported,

    /** The carrier has not been asked for the mailbox settings yet. */
    NotProvisioned,

    /** The carrier knows the line but is still switching the mailbox on. */
    ActivationPending,

    /** The carrier answered that this line has no voicemail service. */
    ServiceRefused,

    /** The mailbox rejected the credentials the carrier itself supplied. */
    AuthenticationRejected,

    /** The mailbox could not be reached at all. */
    ServerUnreachable,
}

/** Per-SIM outcome of a manual provisioning request, surfaced in settings. */
data class VoicemailProbeResult(
    val subscriptionId: Int,
    val carrierName: String,
    val success: Boolean,
    val message: String
)
