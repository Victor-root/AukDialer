package com.grinch.rivo4.debug

import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.provider.VoicemailContract
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import com.grinch.rivo4.controller.util.isAlreadyDefaultDialer
import com.grinch.rivo4.controller.vvm.VvmCredentialsStore

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
        appendLine("-- Voicemail rows --")
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
                    val config = cursor.columnValue(VoicemailContract.Status.CONFIGURATION_STATE)
                    val dataChannel = cursor.columnValue(VoicemailContract.Status.DATA_CHANNEL_STATE)
                    val notifChannel = cursor.columnValue(VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE)
                    appendLine("  source=$source config=$config data=$dataChannel notif=$notifChannel")
                }
            } ?: appendLine("query returned no cursor")
        } catch (e: Exception) {
            appendLine("query failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun android.database.Cursor.columnValue(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0) "<absent>" else runCatching { getString(index) }.getOrNull() ?: "<null>"
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
