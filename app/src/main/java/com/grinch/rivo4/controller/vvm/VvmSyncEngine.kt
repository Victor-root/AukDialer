package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log

/**
 * Orchestrates a sync run: reads credentials, talks IMAP, persists each fetched
 * message into VoicemailContract.
 *
 * Blocking I/O: callers MUST run this on a background thread.
 */
class VvmSyncEngine(private val context: Context) {

    data class SubscriptionSyncOutcome(
        val subscriptionId: Int,
        val phoneAccountId: String?,
        val success: Boolean,
        val totalSeen: Int,
        val writtenNew: Int,
        val skippedExisting: Int,
        val errorType: String?,
        val errorMessage: String?,
    )

    fun syncAllProvisionedSubscriptions(): List<SubscriptionSyncOutcome> {
        val credentialsStore = VvmCredentialsStore(context)
        val provisioned = credentialsStore.listProvisionedSubscriptions()
        if (provisioned.isEmpty()) {
            Log.i(LOG_TAG, "Sync skipped: no provisioned subscription")
            return emptyList()
        }
        val writer = VvmVoicemailWriter(context)
        return provisioned.map { syncOneSubscription(it, credentialsStore, writer) }
    }

    private fun syncOneSubscription(
        subscriptionId: Int,
        credentialsStore: VvmCredentialsStore,
        writer: VvmVoicemailWriter,
    ): SubscriptionSyncOutcome {
        val phoneAccountId = lookupPhoneAccountIdForSub(subscriptionId)
        val credentials = credentialsStore.load(subscriptionId)
        if (credentials == null || !credentials.hasUsableImapCredentials()) {
            Log.i(LOG_TAG, "Sync skipped for subId=$subscriptionId: credentials incomplete")
            return SubscriptionSyncOutcome(
                subscriptionId = subscriptionId,
                phoneAccountId = phoneAccountId,
                success = false,
                totalSeen = 0,
                writtenNew = 0,
                skippedExisting = 0,
                errorType = "BadConfig",
                errorMessage = "credentials incomplete",
            )
        }

        val knownUids = writer.knownServerUids(phoneAccountId)
        val result = VvmImapClient(credentials).syncNewMessages(
            isAlreadyKnown = { uid -> uid in knownUids },
            onNewMessage = { fetched ->
                when (writer.writeFetchedMessage(phoneAccountId, fetched)) {
                    is VvmVoicemailWriter.WriteResult.Success -> true
                    is VvmVoicemailWriter.WriteResult.Failed -> false
                }
            },
        )

        return when (result) {
            is VvmImapClient.SyncResult.Success -> SubscriptionSyncOutcome(
                subscriptionId = subscriptionId,
                phoneAccountId = phoneAccountId,
                success = true,
                totalSeen = result.total,
                writtenNew = result.written,
                skippedExisting = result.skipped,
                errorType = null,
                errorMessage = null,
            )
            is VvmImapClient.SyncResult.Failed -> SubscriptionSyncOutcome(
                subscriptionId = subscriptionId,
                phoneAccountId = phoneAccountId,
                success = false,
                totalSeen = 0,
                writtenNew = result.partialWritten,
                skippedExisting = 0,
                errorType = result.errorType,
                errorMessage = result.errorMessage,
            )
        }
    }

    /**
     * Maps a subscription id to the PhoneAccount id telephony reports, so the
     * inserted row carries the right PHONE_ACCOUNT_ID.
     *
     * Uses SubscriptionInfo.iccId rather than TelephonyManager.getSubscriberId:
     * the latter needs READ_PRIVILEGED_PHONE_STATE, reserved for system apps.
     */
    private fun lookupPhoneAccountIdForSub(subscriptionId: Int): String? {
        return try {
            val sub = context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                ?.firstOrNull { it.subscriptionId == subscriptionId }
                ?: return subscriptionId.toString()
            sub.iccId?.takeIf { it.isNotBlank() } ?: subscriptionId.toString()
        } catch (_: Exception) {
            subscriptionId.toString()
        }
    }

    companion object {
        private const val LOG_TAG = "VvmSyncEngine"
    }
}
