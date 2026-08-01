package com.grinch.rivo4.debug

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.VoicemailContract
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.VisualVoicemailService
import com.grinch.rivo4.controller.util.isAlreadyDefaultDialer
import com.grinch.rivo4.controller.vvm.VvmCarrierConfig
import com.grinch.rivo4.controller.vvm.VvmCredentialsStore
import com.grinch.rivo4.controller.vvm.VvmImapClient
import com.grinch.rivo4.controller.vvm.VvmNetwork

/**
 * Debug-only snapshot of everything the voicemail feature depends on, meant to
 * be copied out of the app and pasted into a bug report.
 *
 * Secrets are never included: the report states whether a credential is present,
 * never its value. Phone numbers are masked.
 */
object VoicemailDiagnostics {

    fun build(context: Context): String = buildString {
        appendLine("== Rivo voicemail diagnostics ==")
        appendLine("android=${android.os.Build.VERSION.SDK_INT} device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("app=${appVersion(context)}")
        appendLine("defaultDialer=${runCatching { isAlreadyDefaultDialer(context) }.getOrDefault(false)}")
        appendLine("sendSms=${granted(context, "android.permission.SEND_SMS")}")
        // Carrier config is unreadable without it, which looks exactly like a
        // carrier that declares nothing.
        appendLine("readPhoneState=${granted(context, "android.permission.READ_PHONE_STATE")}")
        appendLine("readVoicemail=${granted(context, "com.android.voicemail.permission.READ_VOICEMAIL")}")
        appendLine("addVoicemail=${granted(context, "com.android.voicemail.permission.ADD_VOICEMAIL")}")
        appendLine("postNotifications=${granted(context, "android.permission.POST_NOTIFICATIONS")}")
        appendLine()
        appendNetwork(context)
        appendLine()
        appendSubscriptions(context)
        appendLine()
        appendCredentials(context)
        appendLine()
        appendVoicemails(context)
        appendLine()
        appendStatusRows(context)
        appendLine()
        appendVoicemailApps(context)
        appendLine()
        appendImapCheck(context)
    }

    /**
     * Other apps implementing a visual voicemail service. A vendor dialer owning
     * the service is the usual reason a device carries no carrier config we can
     * read: its own voicemail stack never needed one published.
     */
    private fun StringBuilder.appendVoicemailApps(context: Context) {
        appendLine("-- Other visual voicemail apps on the device --")
        try {
            val intent = android.content.Intent(VisualVoicemailService.SERVICE_INTERFACE)
            val services = context.packageManager.queryIntentServices(intent, 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .filter { it != context.packageName }
                .distinct()
            appendLine(services.joinToString(", ").ifBlank { "<none>" })
        } catch (e: Exception) {
            appendLine("lookup failed: ${e.javaClass.simpleName}")
        }
    }

    /** Which route is live matters: many carrier mailboxes refuse Wi-Fi. */
    private fun StringBuilder.appendNetwork(context: Context) {
        appendLine("-- Network --")
        try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            if (manager == null) {
                appendLine("connectivity service unavailable")
                return
            }
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            if (capabilities == null) {
                appendLine("no active network")
                return
            }
            val transports = buildList {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            }
            appendLine("active=${transports.joinToString("+").ifBlank { "unknown" }}")
            appendLine("validated=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
            appendLine("notMetered=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}")
        } catch (e: Exception) {
            appendLine("network read failed: ${e.javaClass.simpleName}")
        }
    }

    private fun StringBuilder.appendSubscriptions(context: Context) {
        appendLine("-- SIMs and carrier config --")
        val configs = VvmCarrierConfig.readAll(context)
        if (configs.isEmpty()) {
            appendLine("no active subscription")
            return
        }
        val subs = try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        } catch (_: Exception) {
            null
        }
        for (config in configs) {
            val sub = subs?.firstOrNull { it.subscriptionId == config.subscriptionId }
            appendLine("subId=${config.subscriptionId} carrier=${config.carrierName}")
            if (sub != null) {
                appendLine("  mccMnc=${sub.mccString ?: "?"}-${sub.mncString ?: "?"} country=${sub.countryIso}")
                appendLine("  embedded=${sub.isEmbedded} slot=${sub.simSlotIndex}")
                // The two identifiers a carrier config entry is keyed on.
                appendLine("  carrierId=${sub.carrierId} gid1=${groupIdLevel1(context, config.subscriptionId)}")
            }
            appendLine("  vvmType=${config.vvmType.ifBlank { "<none>" }} supported=${config.isSupported}")
            appendLine("  source=${if (config.isOverridden) "manual override" else "platform"}")
            // Says which of two very different problems an empty config is: one
            // that never loaded, or one that loaded carrying no voicemail entry.
            appendLine("  configApplied=${config.configApplied}")
            appendLine("  destination=${mask(config.destinationNumber)}")
            appendLine("  port=${config.portNumber}")
            appendLine("  clientPrefix=${config.clientPrefix}")
            appendLine("  ssl=${config.sslEnabled}")
            appendLine("  cellularRequired=${config.cellularDataRequired}")
            appendExtraCarrierKeys(context, config.subscriptionId)
        }
    }

    /**
     * Keys the engine does not act on, but which explain a carrier's behaviour:
     * a legacy-mode carrier or one shipping its own voicemail app behaves
     * differently from a plain OMTP one.
     */
    private fun StringBuilder.appendExtraCarrierKeys(context: Context, subscriptionId: Int) {
        val bundle = try {
            context.getSystemService(CarrierConfigManager::class.java)
                ?.getConfigForSubId(subscriptionId) ?: return
        } catch (_: Exception) {
            appendLine("  carrier config unreadable")
            return
        }
        appendLine("  configKeys=${bundle.size()}")
        appendLine("  legacyMode=${bundle.getBoolean(CarrierConfigManager.KEY_VVM_LEGACY_MODE_ENABLED_BOOL, false)}")
        appendLine("  prefetch=${bundle.getBoolean(CarrierConfigManager.KEY_VVM_PREFETCH_BOOL, false)}")
        val disabled = bundle.getStringArray(CarrierConfigManager.KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY)
        appendLine("  disabledCapabilities=${disabled?.joinToString(",")?.ifBlank { "<none>" } ?: "<none>"}")
        val packages = bundle.getStringArray(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY)
        appendLine("  carrierVvmApps=${packages?.joinToString(",")?.ifBlank { "<none>" } ?: "<none>"}")
    }

    private fun StringBuilder.appendCredentials(context: Context) {
        appendLine("-- Stored credentials (values redacted) --")
        val store = VvmCredentialsStore(context)
        val subs = store.listProvisionedSubscriptions()
        if (subs.isEmpty()) {
            appendLine("none stored: the carrier never answered the STATUS request")
            return
        }
        for (subId in subs) {
            val credentials = store.load(subId)
            if (credentials == null) {
                appendLine("subId=$subId unreadable")
                continue
            }
            appendLine("subId=$subId state=${credentials.provisioningState.name}")
            appendLine("  usable=${credentials.hasUsableImapCredentials()}")
            // The return code is what a carrier uses to explain a refusal.
            appendLine("  returnCode=${credentials.returnCode ?: "<none>"} subscriberType=${credentials.subscriberType ?: "<none>"}")
            appendLine("  imapServer=${credentials.imapServer ?: "<none>"} port=${credentials.imapPort ?: 0} ssl=${credentials.imapUseSsl}")
            appendLine("  smtpPort=${credentials.smtpPort ?: 0}")
            appendLine("  hasUser=${!credentials.imapUsername.isNullOrBlank()} hasPassword=${!credentials.imapPassword.isNullOrBlank()}")
            appendLine("  hasTui=${!credentials.tuiAccessNumber.isNullOrBlank()} language=${credentials.language ?: "<none>"}")
            appendLine("  maxGreetingS=${credentials.maxGreetingLengthSeconds ?: 0} maxMessageS=${credentials.maxVoicemailLengthSeconds ?: 0}")
            appendLine("  storedAt=${store.storedAtMs(subId) ?: 0}")
        }
    }

    private fun StringBuilder.appendVoicemails(context: Context) {
        appendLine("-- Voicemail rows (raw, deleted=1 rows are hidden in the app) --")
        val projection = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
            VoicemailContract.Voicemails.IS_READ,
            VoicemailContract.Voicemails.HAS_CONTENT,
            VoicemailContract.Voicemails.SOURCE_PACKAGE,
            VoicemailContract.Voicemails.DELETED,
        )
        try {
            context.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                projection,
                null,
                null,
                "${VoicemailContract.Voicemails.DATE} DESC",
            )?.use { cursor ->
                if (cursor.count == 0) {
                    appendLine("provider readable, 0 rows")
                    return
                }
                appendLine("${cursor.count} row(s)")
                var shown = 0
                while (cursor.moveToNext() && shown < MAX_ROWS) {
                    appendLine(
                        "  id=${cursor.getLong(0)} number=${mask(cursor.getString(1))}" +
                            " date=${cursor.getLong(2)} durationS=${cursor.getLong(3)}" +
                            " read=${cursor.getInt(4)} hasContent=${cursor.getInt(5)}" +
                            " source=${cursor.getString(6)} deleted=${cursor.getInt(7)}"
                    )
                    shown++
                }
            } ?: appendLine("query returned no cursor")
        } catch (e: Exception) {
            appendLine("query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun StringBuilder.appendStatusRows(context: Context) {
        appendLine("-- Status rows --")
        try {
            context.contentResolver.query(
                VoicemailContract.Status.CONTENT_URI,
                null,
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.count == 0) {
                    appendLine("no status row")
                    return
                }
                while (cursor.moveToNext()) {
                    val source = cursor.columnValue(VoicemailContract.Status.SOURCE_PACKAGE)
                    val config = configurationStateLabel(cursor.columnInt(VoicemailContract.Status.CONFIGURATION_STATE))
                    val data = dataChannelLabel(cursor.columnInt(VoicemailContract.Status.DATA_CHANNEL_STATE))
                    val notif = notificationChannelLabel(cursor.columnInt(VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE))
                    appendLine("  source=$source config=$config data=$data notif=$notif")
                }
            } ?: appendLine("query returned no cursor")
        } catch (e: Exception) {
            appendLine("query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Connects to the carrier mailbox with the stored credentials, so a report
     * proves the whole chain works without waiting for someone to leave an
     * actual message.
     */
    private fun StringBuilder.appendImapCheck(context: Context) {
        appendLine("-- Carrier mailbox connection --")
        val store = VvmCredentialsStore(context)
        val subs = store.listProvisionedSubscriptions()
        if (subs.isEmpty()) {
            appendLine("skipped: no credentials to connect with")
            return
        }
        for (subId in subs) {
            val credentials = store.load(subId)
            if (credentials == null || !credentials.hasUsableImapCredentials()) {
                appendLine("subId=$subId skipped: credentials incomplete")
                continue
            }
            // Same connection setup as the real sync, and the same route: a
            // carrier demanding its own network must be reached over it here
            // too, or the report would describe a path the app never takes.
            val config = VvmCarrierConfig.read(context, subId)
            appendLine("subId=$subId route=${if (config.cellularDataRequired) "carrier cellular" else "default"}")
            val inspection = if (config.cellularDataRequired) {
                VvmNetwork.onCellular(context, subId) { VvmImapClient(credentials).inspect() }
            } else {
                VvmImapClient(credentials).inspect()
            }
            if (!inspection.connected) {
                appendLine("  FAILED over ${inspection.protocol}: ${inspection.error}")
                continue
            }
            appendLine("  OK over ${inspection.protocol}")
            appendLine("  messages=${inspection.messageCount} unread=${inspection.unseenCount}")
            appendLine("  capabilities=${inspection.capabilities.joinToString(",").ifBlank { "<none advertised>" }}")
            appendLine("  newest=${inspection.newestMessageShape ?: "<no message to inspect>"}")
        }
    }

    private fun configurationStateLabel(value: Int?): String = when (value) {
        null -> "<absent>"
        VoicemailContract.Status.CONFIGURATION_STATE_OK -> "OK"
        VoicemailContract.Status.CONFIGURATION_STATE_NOT_CONFIGURED -> "NOT_CONFIGURED"
        VoicemailContract.Status.CONFIGURATION_STATE_CAN_BE_CONFIGURED -> "CAN_BE_CONFIGURED"
        VoicemailContract.Status.CONFIGURATION_STATE_DISABLED -> "DISABLED"
        else -> "code $value"
    }

    private fun dataChannelLabel(value: Int?): String = when (value) {
        null -> "<absent>"
        VoicemailContract.Status.DATA_CHANNEL_STATE_OK -> "OK"
        VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION -> "NO_CONNECTION"
        else -> "code $value"
    }

    private fun notificationChannelLabel(value: Int?): String = when (value) {
        null -> "<absent>"
        VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK -> "OK"
        VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION -> "NO_CONNECTION"
        VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING -> "MESSAGE_WAITING"
        else -> "code $value"
    }

    private fun android.database.Cursor.columnValue(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0) "<absent>" else runCatching { getString(index) }.getOrNull() ?: "<null>"
    }

    private fun android.database.Cursor.columnInt(column: String): Int? {
        val index = getColumnIndex(column)
        if (index < 0) return null
        return runCatching { if (isNull(index)) null else getInt(index) }.getOrNull()
    }

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun groupIdLevel1(context: Context, subscriptionId: Int): String {
        return try {
            context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(subscriptionId)
                ?.groupIdLevel1
                ?.takeIf { it.isNotBlank() }
                ?: "<none>"
        } catch (_: Exception) {
            "<unreadable>"
        }
    }

    private fun appVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (_: Exception) {
            "<unknown>"
        }
    }

    /** Keeps the shape of a number visible without exposing who it belongs to. */
    private fun mask(raw: String?): String {
        if (raw.isNullOrBlank()) return "<none>"
        val digits = raw.filter { it.isDigit() }
        if (digits.length <= 4) return "*".repeat(digits.length)
        return "${digits.take(2)}${"*".repeat(digits.length - 4)}${digits.takeLast(2)}"
    }

    /**
     * Writes the report to logcat under a single tag. Split by line because
     * logcat truncates long messages, which would cut the report short.
     */
    fun log(report: String) {
        report.lineSequence().forEach { android.util.Log.i(LOG_TAG, it) }
    }

    private const val LOG_TAG = "VvmDiagnostics"
    private const val MAX_ROWS = 25
}
