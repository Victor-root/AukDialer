package com.grinch.rivo4.controller.vvm

import android.telecom.PhoneAccountHandle
import android.telephony.CarrierConfigManager
import android.telephony.VisualVoicemailService
import android.telephony.VisualVoicemailSms
import android.util.Log

/**
 * Bound by the telephony framework when this app is the default dialer and a
 * carrier OMTP SMS matching the registered filter arrives.
 *
 * STATUS replies carry the mailbox credentials, which are persisted encrypted;
 * SYNC messages are wake-up signals that hand off to [VvmSyncWorker].
 */
class RivoVisualVoicemailService : VisualVoicemailService() {

    override fun onCellServiceConnected(task: VisualVoicemailTask, phoneAccountHandle: PhoneAccountHandle) {
        try {
            Log.i(LOG_TAG, "onCellServiceConnected account=${phoneAccountHandle.id}")
        } finally {
            safeFinish(task)
        }
    }

    override fun onSmsReceived(task: VisualVoicemailTask, sms: VisualVoicemailSms) {
        try {
            val prefix = sms.prefix ?: "<no-prefix>"
            val fields = sms.fields
            val accountId = sms.phoneAccountHandle?.id ?: "<no-account>"

            OmtpStatusParser.parseStatus(prefix, fields)?.let {
                handleParsedStatus(sms.phoneAccountHandle, accountId, it)
                return
            }
            OmtpSyncParser.parseSync(prefix, fields)?.let {
                handleParsedSync(accountId, it)
                return
            }

            // Vendor-specific or malformed frame: record its shape only. Field
            // values are never logged, they can hold the mailbox password.
            Log.i(
                LOG_TAG,
                "onSmsReceived unhandled prefix=$prefix account=$accountId " +
                    OmtpStatusParser.describeFieldsRedacted(fields),
            )
        } finally {
            safeFinish(task)
        }
    }

    private fun handleParsedSync(accountId: String, sync: OmtpSyncMessage) {
        Log.i(LOG_TAG, "SYNC account=$accountId ev=${sync.eventType.name} trigger=${sync.shouldTriggerSync()}")
        if (!sync.shouldTriggerSync()) return
        // Hand off to WorkManager rather than a bare thread: this process can be
        // killed right after the task finishes, and the request survives that.
        VvmSyncWorker.enqueueAutoSync(applicationContext, trigger = "sms:${sync.eventType.name}")
    }

    private fun handleParsedStatus(
        phoneAccountHandle: PhoneAccountHandle?,
        accountId: String,
        status: OmtpStatusMessage,
    ) {
        val subscriptionId = accountId.toIntOrNull()

        // STATUS bodies don't say whether IMAP wants TLS from byte zero or
        // STARTTLS on a plaintext port; CarrierConfig is authoritative, so the
        // answer is resolved once here and committed with the credentials.
        val sslEnabled = subscriptionId?.let { resolveSslEnabledForSub(it) } ?: false
        val enrichedStatus = status.copy(imapUseSsl = sslEnabled)

        if (subscriptionId != null) {
            try {
                VvmCredentialsStore(this).store(subscriptionId, enrichedStatus)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Credentials store failed subId=$subscriptionId", e)
            }
        }

        if (phoneAccountHandle != null) {
            VvmStatusWriter(this)
                .writeStatus(phoneAccountHandle.id, phoneAccountHandle.componentName?.flattenToString(), status)
                .onFailure { Log.w(LOG_TAG, "Status row write failed account=$accountId", it) }
        }

        Log.i(
            LOG_TAG,
            "STATUS account=$accountId state=${status.provisioningState.name} " +
                "imapReady=${status.hasUsableImapCredentials()}",
        )
    }

    override fun onSimRemoved(task: VisualVoicemailTask, phoneAccountHandle: PhoneAccountHandle) {
        try {
            Log.i(LOG_TAG, "onSimRemoved account=${phoneAccountHandle.id}")
        } finally {
            safeFinish(task)
        }
    }

    override fun onStopped(task: VisualVoicemailTask) {
        try {
            Log.i(LOG_TAG, "onStopped")
        } finally {
            safeFinish(task)
        }
    }

    private fun safeFinish(task: VisualVoicemailTask) {
        try {
            task.finish()
        } catch (_: Exception) {
        }
    }

    private fun resolveSslEnabledForSub(subscriptionId: Int): Boolean {
        return try {
            val manager = getSystemService(CarrierConfigManager::class.java) ?: return false
            manager.getConfigForSubId(subscriptionId)
                ?.getBoolean(CarrierConfigManager.KEY_VVM_SSL_ENABLED_BOOL, false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val LOG_TAG = "VvmService"
    }
}
