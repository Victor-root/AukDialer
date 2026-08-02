package com.grinch.rivo4.modal.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.VoicemailContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.VisualVoicemailSmsFilterSettings
import android.util.Log
import com.grinch.rivo4.controller.util.isAlreadyDefaultDialer
import com.grinch.rivo4.controller.vvm.ProvisioningState
import com.grinch.rivo4.controller.vvm.VvmCarrierConfig
import com.grinch.rivo4.controller.vvm.VvmCredentialsStore
import com.grinch.rivo4.controller.vvm.VvmImapClient
import com.grinch.rivo4.controller.vvm.VvmNetwork
import com.grinch.rivo4.controller.vvm.VvmRequestSender
import com.grinch.rivo4.controller.vvm.VvmSyncEngine
import com.grinch.rivo4.controller.vvm.VvmSyncWorker
import com.grinch.rivo4.modal.`interface`.IContactsRepository
import com.grinch.rivo4.modal.`interface`.IVoicemailRepository
import com.grinch.rivo4.modal.data.Contact
import com.grinch.rivo4.modal.data.Voicemail
import com.grinch.rivo4.modal.data.VoicemailProbeResult
import com.grinch.rivo4.modal.data.VoicemailStatus

/**
 * Reads and writes Android's voicemail provider, and drives the carrier-side
 * provisioning and sync.
 *
 * Every provider call is wrapped defensively: not being the default dialer, a
 * missing permission, or a vendor provider lacking a column must degrade to an
 * empty result rather than crash the caller.
 */
class VoicemailRepository(
    private val context: Context,
    private val contactsRepo: IContactsRepository
) : IVoicemailRepository {

    override fun isDefaultDialer(): Boolean {
        return try {
            isAlreadyDefaultDialer(context)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Works down from the conditions that make everything else moot: without the
     * dialer role nothing is routed here, without a protocol we understand
     * nothing can be asked, and without credentials nothing can be fetched. Only
     * once all of that holds does a failed sync become the interesting answer.
     */
    override fun getStatus(): VoicemailStatus {
        if (!isDefaultDialer()) return VoicemailStatus.NotDefaultDialer

        val supported = VvmCarrierConfig.readAll(context).filter { it.isSupported }
        if (supported.isEmpty()) return VoicemailStatus.CarrierUnsupported

        val store = VvmCredentialsStore(context)
        val credentials = supported.mapNotNull { store.load(it.subscriptionId) }
        if (credentials.none { it.hasUsableImapCredentials() }) {
            val states = credentials.map { it.provisioningState }
            return when {
                states.any { it == ProvisioningState.NEW_USER } -> VoicemailStatus.ActivationPending
                states.any {
                    it == ProvisioningState.BLOCKED || it == ProvisioningState.UNKNOWN_USER
                } -> VoicemailStatus.ServiceRefused
                else -> VoicemailStatus.NotProvisioned
            }
        }
        return lastSyncFailure ?: VoicemailStatus.Ready
    }

    /**
     * Reads every voicemail the provider lets us see, whichever app imported it.
     *
     * Deliberately not gated on a READ_VOICEMAIL check: that permission is
     * privileged and a third-party app never holds it, yet the provider grants
     * the default dialer full read access anyway. Checking it first would
     * report an empty mailbox on every device.
     */
    override fun getVoicemails(): List<Voicemail> {
        // Exclude soft-deleted rows: the provider keeps them until the owning
        // source syncs the deletion, but their audio file is already gone.
        val deletedFilter = "${VoicemailContract.Voicemails.DELETED} = 0"
        val items = queryVoicemails(deletedFilter)
            // Vendor providers occasionally lack the DELETED column and reject
            // the selection outright; an unfiltered read beats no read at all.
            ?: queryVoicemails(null)
            ?: return emptyList()
        return resolveContacts(withSimLabels(preferOwnCopies(items)))
    }

    /**
     * Collapses the copies several apps keep of the same message.
     *
     * The provider is shared, and each voicemail app stores its own row for
     * every message it imports, scoped to its own source package. Two apps
     * syncing one mailbox therefore produce two rows for one message, and this
     * list shows every source. Ours is preferred where it exists, since it is
     * the only one whose audio, read flag and deletion we can act on.
     */
    private fun preferOwnCopies(items: List<Voicemail>): List<Voicemail> {
        val ours = context.packageName
        if (items.none { it.sourcePackage != ours }) return items
        return items
            .groupBy { it.number to it.date }
            .values
            .map { copies -> copies.firstOrNull { it.sourcePackage == ours } ?: copies.first() }
            .sortedByDescending { it.date }
    }

    /**
     * Attaches the SIM display name to each row. The provider stores the phone
     * account id, which is either the SIM's iccId or its subscription id
     * depending on which app imported the message, so both are looked up.
     */
    private fun withSimLabels(items: List<Voicemail>): List<Voicemail> {
        if (items.none { !it.phoneAccountId.isNullOrBlank() }) return items
        val labels = simLabelsByAccountId()
        if (labels.isEmpty()) return items
        return items.map { item ->
            val label = item.phoneAccountId?.let { labels[it] }
            if (label == null) item else item.copy(simLabel = label)
        }
    }

    private fun simLabelsByAccountId(): Map<String, String> {
        return try {
            val subs = activeSubscriptions() ?: return emptyMap()
            buildMap {
                for (sub in subs) {
                    val label = sub.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: continue
                    sub.iccId?.takeIf { it.isNotBlank() }?.let { put(it, label) }
                    put(sub.subscriptionId.toString(), label)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Returns null when the query itself failed, as opposed to an empty mailbox. */
    private fun queryVoicemails(selection: String?): List<Voicemail>? {
        val projection = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
            VoicemailContract.Voicemails.IS_READ,
            VoicemailContract.Voicemails.HAS_CONTENT,
            VoicemailContract.Voicemails.SOURCE_PACKAGE,
            VoicemailContract.Voicemails.PHONE_ACCOUNT_ID,
        )
        val items = mutableListOf<Voicemail>()
        return try {
            context.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                projection,
                selection,
                null,
                "${VoicemailContract.Voicemails.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext() && items.size < MAX_ITEMS) {
                    items.add(
                        Voicemail(
                            id = cursor.getLong(0),
                            number = cursor.getString(1),
                            date = cursor.getLong(2),
                            durationSeconds = cursor.getLong(3).toInt().coerceAtLeast(0),
                            isRead = cursor.getLong(4) != 0L,
                            hasContent = cursor.getLong(5) != 0L,
                            sourcePackage = cursor.getString(6),
                            phoneAccountId = cursor.getString(7),
                        )
                    )
                }
                items
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Voicemail query failed (selection=$selection)", e)
            null
        }
    }

    /** Resolves each distinct number once, so a mailbox full of the same caller costs one lookup. */
    private fun resolveContacts(items: List<Voicemail>): List<Voicemail> {
        val cache = mutableMapOf<String, Contact?>()
        return items.map { item ->
            val number = item.number?.takeIf { it.isNotBlank() } ?: return@map item
            val contact = cache.getOrPut(number) {
                try {
                    contactsRepo.getContactByNumber(number)
                } catch (_: Exception) {
                    null
                }
            }
            if (contact == null) item else item.copy(contactName = contact.name, photoUri = contact.photoUri)
        }
    }

    override fun markAsRead(id: Long, isRead: Boolean): Result<Unit> {
        return runCatching {
            val values = ContentValues().apply {
                put(VoicemailContract.Voicemails.IS_READ, if (isRead) 1 else 0)
            }
            val uri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
            if (context.contentResolver.update(uri, values, null, null) <= 0) {
                throw IllegalStateException("No row updated for id=$id")
            }
            pushSeenFlagToServer(id, isRead)
        }
    }

    override fun delete(id: Long): Result<Unit> {
        return runCatching {
            // Server first: if the network call fails we leave both sides intact
            // rather than "gone here, still on the carrier".
            pushDeleteToServer(id)
            val uri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
            if (context.contentResolver.delete(uri, null, null) <= 0) {
                throw IllegalStateException("No row deleted for id=$id")
            }
        }
    }

    /**
     * Registers a carrier SMS filter per active SIM so the system routes OMTP
     * messages to our service instead of the user's SMS app.
     */
    override fun registerSmsFilter(): Result<Int> {
        return runCatching {
            if (!isDefaultDialer()) throw IllegalStateException("Not the default dialer")
            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
                ?: throw IllegalStateException("TelephonyManager unavailable")

            // Only SIMs whose carrier speaks our protocol. Registering a filter
            // on the others would divert their voicemail SMS to a service that
            // cannot read them, leaving nobody to handle the message.
            val supported = VvmCarrierConfig.readAll(context).filter { it.isSupported }
            if (supported.isEmpty()) {
                throw IllegalStateException("No SIM with a supported voicemail protocol")
            }

            var registered = 0
            for (config in supported) {
                try {
                    // The prefix and port must come from the carrier: many only
                    // send binary SMS on a vendor port, which a prefix-only
                    // filter never catches.
                    telephonyManager.createForSubscriptionId(config.subscriptionId)
                        .setVisualVoicemailSmsFilterSettings(
                            buildSmsFilterSettings(config.clientPrefix, config.portNumber)
                        )
                    registered++
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Filter registration failed subId=${config.subscriptionId}", e)
                }
            }
            if (registered == 0) throw IllegalStateException("No subscription accepted the filter")

            VvmSyncWorker.ensurePeriodicSync(context)
            autoProvisionMissingSubs(supported)
            registered
        }
    }

    override fun requestProvisioning(): List<VoicemailProbeResult> {
        return VvmCarrierConfig.readAll(context).map { config ->
            when (val result = VvmRequestSender.sendStatus(context, config)) {
                is VvmRequestSender.Result.Sent -> VoicemailProbeResult(
                    config.subscriptionId,
                    config.carrierName,
                    true,
                    "sent to ${config.destinationNumber}",
                )
                is VvmRequestSender.Result.Skipped -> VoicemailProbeResult(
                    config.subscriptionId,
                    config.carrierName,
                    false,
                    result.reason,
                )
                is VvmRequestSender.Result.Failed -> VoicemailProbeResult(
                    config.subscriptionId,
                    config.carrierName,
                    false,
                    "${result.errorType}: ${result.errorMessage}",
                )
            }
        }
    }

    override fun syncNow(): Result<Int> {
        return runCatching {
            val outcomes = VvmSyncEngine(context).syncAllProvisionedSubscriptions()
            val failure = outcomes.firstOrNull { !it.success }
            if (outcomes.isNotEmpty() && outcomes.none { it.success }) {
                lastSyncFailure = classifyFailure(failure?.errorType)
                throw IllegalStateException(failure?.errorMessage ?: "sync failed")
            }
            lastSyncFailure = null
            outcomes.sumOf { it.writtenNew }
        }
    }

    /**
     * Credentials the carrier itself issued being refused is a different problem
     * from not reaching the server, and points somewhere else entirely, so the
     * two are worth telling apart.
     */
    private fun classifyFailure(errorType: String?): VoicemailStatus {
        return if (errorType?.contains("Authentication", ignoreCase = true) == true) {
            VoicemailStatus.AuthenticationRejected
        } else {
            VoicemailStatus.ServerUnreachable
        }
    }

    /**
     * Asks for credentials on any supported SIM that has none yet. Throttled in
     * memory so repeated filter registrations do not pester the carrier.
     */
    private fun autoProvisionMissingSubs(configs: List<VvmCarrierConfig>) {
        val now = System.currentTimeMillis()
        if (now - lastAutoProvisionAttemptMs < AUTO_PROVISION_COOLDOWN_MS) return

        val store = VvmCredentialsStore(context)
        var sentAny = false
        for (config in configs) {
            if (store.load(config.subscriptionId)?.hasUsableImapCredentials() == true) continue
            if (VvmRequestSender.sendStatus(context, config) is VvmRequestSender.Result.Sent) {
                sentAny = true
            }
        }
        if (sentAny) lastAutoProvisionAttemptMs = now
    }

    private fun buildSmsFilterSettings(prefix: String, port: Int): VisualVoicemailSmsFilterSettings {
        val builder = VisualVoicemailSmsFilterSettings.Builder().setClientPrefix(prefix)
        // Only constrain the port when the carrier declares one, otherwise the
        // filter would widen to unrelated data SMS.
        if (port > 0) builder.setDestinationPort(port)
        return builder.build()
    }

    private fun activeSubscriptions(): List<SubscriptionInfo>? {
        return try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        } catch (_: Exception) {
            null
        }
    }

    private fun pushSeenFlagToServer(rowId: Long, isRead: Boolean) {
        val meta = loadOwnedRowMeta(rowId) ?: return
        val subId = subscriptionIdFor(meta.phoneAccountId) ?: return
        val credentials = VvmCredentialsStore(context).load(subId)
            ?.takeIf { it.hasUsableImapCredentials() } ?: return
        onCarrierNetwork(subId) { VvmImapClient(credentials).setSeenFlag(meta.serverUid, isRead) }
    }

    private fun pushDeleteToServer(rowId: Long) {
        val meta = loadOwnedRowMeta(rowId) ?: return
        val subId = subscriptionIdFor(meta.phoneAccountId) ?: return
        val credentials = VvmCredentialsStore(context).load(subId)
            ?.takeIf { it.hasUsableImapCredentials() } ?: return
        onCarrierNetwork(subId) { VvmImapClient(credentials).deleteMessage(meta.serverUid) }
    }

    /** Write-backs reach the same servers as the sync, so they need the same route. */
    private fun <T> onCarrierNetwork(subscriptionId: Int, block: () -> T): T {
        return if (VvmCarrierConfig.read(context, subscriptionId).cellularDataRequired) {
            VvmNetwork.onCellular(context, subscriptionId, block)
        } else {
            block()
        }
    }

    private data class OwnedRowMeta(val serverUid: String, val phoneAccountId: String?)

    /** Returns null for rows owned by another voicemail app: we hold no credentials for those. */
    private fun loadOwnedRowMeta(rowId: Long): OwnedRowMeta? {
        val rowUri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, rowId)
        val projection = arrayOf(
            VoicemailContract.Voicemails.SOURCE_PACKAGE,
            VoicemailContract.Voicemails.SOURCE_DATA,
            VoicemailContract.Voicemails.PHONE_ACCOUNT_ID,
        )
        return try {
            context.contentResolver.query(rowUri, projection, null, null, null)?.use { c ->
                if (!c.moveToNext()) return@use null
                if (c.getString(0) != context.packageName) return@use null
                val serverUid = c.getString(1)
                if (serverUid.isNullOrBlank()) return@use null
                OwnedRowMeta(serverUid, c.getString(2))
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves the SIM a stored row belongs to. The single-subscription
     * fallback deliberately gives up when several are provisioned rather than
     * guess, since guessing would write into the wrong carrier's mailbox.
     */
    private fun subscriptionIdFor(phoneAccountId: String?): Int? {
        return phoneAccountId?.toIntOrNull()
            ?: lookupSubIdByIccId(phoneAccountId)
            ?: VvmCredentialsStore(context).listProvisionedSubscriptions().singleOrNull()
    }

    private fun lookupSubIdByIccId(iccId: String?): Int? {
        if (iccId.isNullOrBlank()) return null
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                ?.firstOrNull { it.iccId == iccId }
                ?.subscriptionId
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val LOG_TAG = "VoicemailRepository"
        private const val MAX_ITEMS = 200
        private const val AUTO_PROVISION_COOLDOWN_MS = 60_000L

        @Volatile
        private var lastAutoProvisionAttemptMs: Long = 0L

        /** Cleared by the next successful sync, so a fixed problem stops being reported. */
        @Volatile
        private var lastSyncFailure: VoicemailStatus? = null
    }
}
