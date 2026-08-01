package com.grinch.rivo4.modal.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.provider.VoicemailContract
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.VisualVoicemailSmsFilterSettings
import android.util.Log
import com.grinch.rivo4.controller.util.isAlreadyDefaultDialer
import com.grinch.rivo4.controller.vvm.OmtpStatusMessage
import com.grinch.rivo4.controller.vvm.VvmCredentialsStore
import com.grinch.rivo4.controller.vvm.VvmImapClient
import com.grinch.rivo4.controller.vvm.VvmSyncEngine
import com.grinch.rivo4.controller.vvm.VvmSyncWorker
import com.grinch.rivo4.modal.`interface`.IContactsRepository
import com.grinch.rivo4.modal.`interface`.IVoicemailRepository
import com.grinch.rivo4.modal.data.Contact
import com.grinch.rivo4.modal.data.Voicemail
import com.grinch.rivo4.modal.data.VoicemailProbeResult

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
        return resolveContacts(withSimLabels(items))
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

            val activeSubs = activeSubscriptions()
            var registered = 0
            if (activeSubs.isNullOrEmpty()) {
                telephonyManager.setVisualVoicemailSmsFilterSettings(
                    buildSmsFilterSettings(OMTP_CLIENT_PREFIX, port = 0)
                )
                registered = 1
            } else {
                for (sub in activeSubs) {
                    try {
                        // The prefix and port must come from CarrierConfig: many
                        // carriers only send binary SMS on a vendor port, which a
                        // prefix-only filter never catches.
                        val (prefix, port) = readFilterParams(sub.subscriptionId)
                        telephonyManager.createForSubscriptionId(sub.subscriptionId)
                            .setVisualVoicemailSmsFilterSettings(buildSmsFilterSettings(prefix, port))
                        registered++
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Filter registration failed subId=${sub.subscriptionId}", e)
                    }
                }
                if (registered == 0) throw IllegalStateException("No subscription accepted the filter")
            }
            VvmSyncWorker.ensurePeriodicSync(context)
            autoProvisionMissingSubs()
            registered
        }
    }

    override fun requestProvisioning(): List<VoicemailProbeResult> {
        val configs = carrierConfigs()
        if (configs.isEmpty()) return emptyList()
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            ?: return configs.map {
                VoicemailProbeResult(it.subscriptionId, it.carrierName, false, "TelephonyManager unavailable")
            }
        return configs.map { sendStatusProbe(telephonyManager, it) }
    }

    override fun syncNow(): Result<Int> {
        return runCatching {
            val outcomes = VvmSyncEngine(context).syncAllProvisionedSubscriptions()
            val failure = outcomes.firstOrNull { !it.success }
            if (outcomes.isNotEmpty() && outcomes.none { it.success }) {
                throw IllegalStateException(failure?.errorMessage ?: "sync failed")
            }
            outcomes.sumOf { it.writtenNew }
        }
    }

    /**
     * Sends a provisioning request for any SIM whose credentials are missing.
     * Throttled in memory so repeated filter registrations don't spam the
     * carrier, and silently skipped until SEND_SMS has been granted.
     */
    private fun autoProvisionMissingSubs() {
        if (context.checkSelfPermission(SEND_SMS_PERMISSION) != PackageManager.PERMISSION_GRANTED) return
        val now = System.currentTimeMillis()
        if (now - lastAutoProvisionAttemptMs < AUTO_PROVISION_COOLDOWN_MS) return

        val configs = carrierConfigs()
        if (configs.isEmpty()) return
        val store = VvmCredentialsStore(context)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return
        var sentAny = false
        for (cfg in configs) {
            if (store.load(cfg.subscriptionId)?.hasUsableImapCredentials() == true) continue
            sendStatusProbe(telephonyManager, cfg)
            sentAny = true
        }
        if (sentAny) lastAutoProvisionAttemptMs = now
    }

    private fun sendStatusProbe(
        telephonyManager: TelephonyManager,
        cfg: CarrierVvmConfig,
    ): VoicemailProbeResult {
        return try {
            if (cfg.destinationNumber.isBlank()) {
                throw IllegalStateException("No VVM destination number in CarrierConfig")
            }
            telephonyManager.createForSubscriptionId(cfg.subscriptionId)
                .sendVisualVoicemailSms(cfg.destinationNumber, cfg.portNumber, OMTP_STATUS_REQUEST_BODY, null)
            VoicemailProbeResult(cfg.subscriptionId, cfg.carrierName, true, "sent to ${cfg.destinationNumber}")
        } catch (e: Exception) {
            val message = "${e.javaClass.simpleName}: ${e.message ?: ""}"
            Log.w(LOG_TAG, "Status probe failed subId=${cfg.subscriptionId}", e)
            VoicemailProbeResult(cfg.subscriptionId, cfg.carrierName, false, message)
        }
    }

    private data class CarrierVvmConfig(
        val subscriptionId: Int,
        val carrierName: String,
        val destinationNumber: String,
        val portNumber: Int,
    )

    private fun carrierConfigs(): List<CarrierVvmConfig> {
        return try {
            val manager = context.getSystemService(CarrierConfigManager::class.java) ?: return emptyList()
            val subs = activeSubscriptions() ?: return emptyList()
            subs.map { sub ->
                val cfg = try {
                    manager.getConfigForSubId(sub.subscriptionId) ?: PersistableBundle.EMPTY
                } catch (_: Exception) {
                    PersistableBundle.EMPTY
                }
                CarrierVvmConfig(
                    subscriptionId = sub.subscriptionId,
                    carrierName = sub.carrierName?.toString() ?: "",
                    destinationNumber = cfg.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING, "") ?: "",
                    portNumber = cfg.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readFilterParams(subId: Int): Pair<String, Int> {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
            ?: return OMTP_CLIENT_PREFIX to 0
        val cfg = try {
            manager.getConfigForSubId(subId) ?: PersistableBundle.EMPTY
        } catch (_: Exception) {
            PersistableBundle.EMPTY
        }
        val prefix = cfg.getString(CarrierConfigManager.KEY_VVM_CLIENT_PREFIX_STRING, "")
            ?.takeIf { it.isNotEmpty() }
            ?: OMTP_CLIENT_PREFIX
        return prefix to cfg.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0)
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
        val credentials = loadCredentialsForPhoneAccount(meta.phoneAccountId) ?: return
        VvmImapClient(credentials).setSeenFlag(meta.serverUid, isRead)
    }

    private fun pushDeleteToServer(rowId: Long) {
        val meta = loadOwnedRowMeta(rowId) ?: return
        val credentials = loadCredentialsForPhoneAccount(meta.phoneAccountId) ?: return
        VvmImapClient(credentials).deleteMessage(meta.serverUid)
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

    private fun loadCredentialsForPhoneAccount(phoneAccountId: String?): OmtpStatusMessage? {
        val store = VvmCredentialsStore(context)
        val subId = phoneAccountId?.toIntOrNull()
            ?: lookupSubIdByIccId(phoneAccountId)
            ?: store.listProvisionedSubscriptions().singleOrNull()
            ?: return null
        return store.load(subId)?.takeIf { it.hasUsableImapCredentials() }
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
        private const val SEND_SMS_PERMISSION = "android.permission.SEND_SMS"
        private const val MAX_ITEMS = 200

        /** OMTP 1.3 standard client prefix, used when CarrierConfig declares none. */
        private const val OMTP_CLIENT_PREFIX = "//VVM:"
        private const val OMTP_STATUS_REQUEST_BODY = "STATUS"

        private const val AUTO_PROVISION_COOLDOWN_MS = 60_000L

        @Volatile
        private var lastAutoProvisionAttemptMs: Long = 0L
    }
}
