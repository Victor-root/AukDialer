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

/** Per-SIM outcome of a manual provisioning request, surfaced in settings. */
data class VoicemailProbeResult(
    val subscriptionId: Int,
    val carrierName: String,
    val success: Boolean,
    val message: String
)
