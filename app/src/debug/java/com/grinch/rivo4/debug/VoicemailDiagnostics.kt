package com.grinch.rivo4.debug

import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.provider.VoicemailContract
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import com.grinch.rivo4.controller.util.isAlreadyDefaultDialer
import com.grinch.rivo4.controller.vvm.VvmCredentialsStore
import com.grinch.rivo4.controller.vvm.VvmImapClient

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
        appendLine("defaultDialer=${runCatching { isAlreadyDefaultDialer(context) }.getOrDefault(false)}")
        appendLine("sendSms=${granted(context, "android.permission.SEND_SMS")}")
        appendLine("readVoicemail=${granted(context, "com.android.voicemail.permission.READ_VOICEMAIL")}")
        appendLine("addVoicemail=${granted(context, "com.android.voicemail.permission.ADD_VOICEMAIL")}")
        appendLine()
        appendSubscriptions(context)
        appendLine()
        appendCredentials(context)
        appendLine()
        appendVoicemails(context)
        appendLine()
        appendStatusRows(context)
        appendLine()
        appendImapCheck(context)
    }

    private fun StringBuilder.appendSubscriptions(context: Context) {
        appendLine("-- SIMs and carrier config --")
        val subs = try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        } catch (e: Exception) {
            appendLine("subscriptions unavailable: ${e.javaClass.simpleName}")
            null
        }
        if (subs.isNullOrEmpty()) {
            appendLine("no active subscription")
            return
        }
        val carrierConfig = context.getSystemService(CarrierConfigManager::class.java)
        for (sub in subs) {
            val cfg = try {
                carrierConfig?.getConfigForSubId(sub.subscriptionId) ?: PersistableBundle.EMPTY
            } catch (_: Exception) {
                PersistableBundle.EMPTY
            }
            appendLine("subId=${sub.subscriptionId} carrier=${sub.carrierName}")
            appendLine("  vvmType=${cfg.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING, "")}")
            appendLine("  destination=${mask(cfg.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING, ""))}")
            appendLine("  port=${cfg.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0)}")
            appendLine("  clientPrefix=${cfg.getString(CarrierConfigManager.KEY_VVM_CLIENT_PREFIX_STRING, "")}")
            appendLine("  ssl=${cfg.getBoolean(CarrierConfigManager.KEY_VVM_SSL_ENABLED_BOOL, false)}")
            appendLine("  legacy=${cfg.getBoolean(CarrierConfigManager.KEY_VVM_LEGACY_MODE_ENABLED_BOOL, false)}")
        }
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
            appendLine("  imapServer=${credentials.imapServer ?: "<none>"} port=${credentials.imapPort ?: 0} ssl=${credentials.imapUseSsl}")
            appendLine("  hasUser=${!credentials.imapUsername.isNullOrBlank()} hasPassword=${!credentials.imapPassword.isNullOrBlank()}")
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
            // Runs the real sync path, but reports every message as already
            // known so nothing is decoded or written. Exercising the production
            // code rather than a parallel connect routine is the point: a
            // diagnostic that connected its own way could pass while the real
            // one fails, which is exactly how the SASL breakage hid itself.
            val result = VvmImapClient(credentials).syncNewMessages(
                isAlreadyKnown = { true },
                onNewMessage = { false },
            )
            when (result) {
                is VvmImapClient.SyncResult.Success ->
                    appendLine("subId=$subId OK: ${result.total} message(s) on the server")
                is VvmImapClient.SyncResult.Failed ->
                    appendLine("subId=$subId FAILED: ${result.errorType}: ${result.errorMessage}")
            }
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
