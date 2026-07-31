package com.grinch.rivo4.controller.vvm

import android.content.ContentValues
import android.content.Context
import android.provider.VoicemailContract

/**
 * Writes per-subscription provisioning state into VoicemailContract.Status so
 * the system knows this app holds the voicemail mailbox for that account.
 *
 * Writes are scoped to our own source package, so rows owned by another
 * voicemail app are never touched.
 */
class VvmStatusWriter(private val context: Context) {

    fun writeStatus(phoneAccountId: String, componentName: String?, status: OmtpStatusMessage): Result<Unit> {
        return runCatching {
            val sourceUri = VoicemailContract.Status.buildSourceUri(context.packageName)
            val values = ContentValues().apply {
                put(VoicemailContract.Status.SOURCE_PACKAGE, context.packageName)
                put(VoicemailContract.Status.PHONE_ACCOUNT_ID, phoneAccountId)
                if (!componentName.isNullOrBlank()) {
                    put(VoicemailContract.Status.PHONE_ACCOUNT_COMPONENT_NAME, componentName)
                }
                put(VoicemailContract.Status.CONFIGURATION_STATE, configurationStateFor(status))
                put(
                    VoicemailContract.Status.DATA_CHANNEL_STATE,
                    if (status.provisioningState.isProvisioned()) {
                        VoicemailContract.Status.DATA_CHANNEL_STATE_OK
                    } else {
                        VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION
                    },
                )
                put(
                    VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE,
                    VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK,
                )
                status.tuiAccessNumber?.takeIf { it.isNotBlank() }?.let {
                    put(VoicemailContract.Status.VOICEMAIL_ACCESS_URI, "tel:$it")
                }
            }
            val updated = context.contentResolver.update(
                sourceUri,
                values,
                "${VoicemailContract.Status.PHONE_ACCOUNT_ID}=?",
                arrayOf(phoneAccountId),
            )
            if (updated == 0) {
                context.contentResolver.insert(sourceUri, values)
                    ?: throw IllegalStateException("Status row insert returned null")
            }
        }
    }

    fun clearStatus(phoneAccountId: String): Result<Unit> {
        return runCatching {
            val sourceUri = VoicemailContract.Status.buildSourceUri(context.packageName)
            context.contentResolver.delete(
                sourceUri,
                "${VoicemailContract.Status.PHONE_ACCOUNT_ID}=?",
                arrayOf(phoneAccountId),
            )
        }
    }

    private fun configurationStateFor(status: OmtpStatusMessage): Int {
        return when (status.provisioningState) {
            ProvisioningState.READY, ProvisioningState.PROVISIONED ->
                VoicemailContract.Status.CONFIGURATION_STATE_OK
            ProvisioningState.NEW_USER ->
                VoicemailContract.Status.CONFIGURATION_STATE_CONFIGURING
            ProvisioningState.BLOCKED, ProvisioningState.UNKNOWN_USER ->
                VoicemailContract.Status.CONFIGURATION_STATE_FAILED
            ProvisioningState.UNKNOWN ->
                VoicemailContract.Status.CONFIGURATION_STATE_NOT_CONFIGURED
        }
    }
}
