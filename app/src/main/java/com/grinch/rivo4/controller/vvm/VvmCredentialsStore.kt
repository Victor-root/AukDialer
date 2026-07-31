package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Per-subscription store for OMTP STATUS / IMAP credentials, backed by
 * EncryptedSharedPreferences (AES-GCM values, AES-SIV keys, master key in the
 * Android Keystore). Never logs or exposes credential values.
 */
class VvmCredentialsStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { openOrReset() }

    @Synchronized
    fun store(subscriptionId: Int, status: OmtpStatusMessage) {
        prefs.edit().apply {
            val p = subKey(subscriptionId)
            putString("${p}state", status.provisioningState.name)
            putStringOrRemove("${p}return_code", status.returnCode)
            putStringOrRemove("${p}subscriber_type", status.subscriberType)
            putStringOrRemove("${p}imap_server", status.imapServer)
            putIntOrRemove("${p}imap_port", status.imapPort)
            putBoolean("${p}imap_use_ssl", status.imapUseSsl)
            putStringOrRemove("${p}smtp_server", status.smtpServer)
            putIntOrRemove("${p}smtp_port", status.smtpPort)
            putStringOrRemove("${p}imap_user", status.imapUsername)
            putStringOrRemove("${p}imap_pass", status.imapPassword)
            putStringOrRemove("${p}smtp_user", status.smtpUsername)
            putStringOrRemove("${p}smtp_pass", status.smtpPassword)
            putStringOrRemove("${p}tui", status.tuiAccessNumber)
            putStringOrRemove("${p}dn", status.clientSmsDestinationNumber)
            putStringOrRemove("${p}lang", status.language)
            putIntOrRemove("${p}greeting_max_s", status.maxGreetingLengthSeconds)
            putIntOrRemove("${p}voicemail_max_s", status.maxVoicemailLengthSeconds)
            putLong("${p}stored_at_ms", System.currentTimeMillis())
            apply()
        }
    }

    @Synchronized
    fun load(subscriptionId: Int): OmtpStatusMessage? {
        val p = subKey(subscriptionId)
        val rawState = prefs.getString("${p}state", null) ?: return null
        val state = runCatching { ProvisioningState.valueOf(rawState) }.getOrElse { ProvisioningState.UNKNOWN }
        return OmtpStatusMessage(
            provisioningState = state,
            returnCode = prefs.getString("${p}return_code", null),
            subscriberType = prefs.getString("${p}subscriber_type", null),
            imapServer = prefs.getString("${p}imap_server", null),
            imapPort = prefs.getInt("${p}imap_port", 0).takeIf { it > 0 },
            smtpServer = prefs.getString("${p}smtp_server", null),
            smtpPort = prefs.getInt("${p}smtp_port", 0).takeIf { it > 0 },
            imapUsername = prefs.getString("${p}imap_user", null),
            imapPassword = prefs.getString("${p}imap_pass", null),
            smtpUsername = prefs.getString("${p}smtp_user", null),
            smtpPassword = prefs.getString("${p}smtp_pass", null),
            tuiAccessNumber = prefs.getString("${p}tui", null),
            clientSmsDestinationNumber = prefs.getString("${p}dn", null),
            language = prefs.getString("${p}lang", null),
            maxGreetingLengthSeconds = prefs.getInt("${p}greeting_max_s", 0).takeIf { it > 0 },
            maxVoicemailLengthSeconds = prefs.getInt("${p}voicemail_max_s", 0).takeIf { it > 0 },
            imapUseSsl = prefs.getBoolean("${p}imap_use_ssl", false),
        )
    }

    @Synchronized
    fun storedAtMs(subscriptionId: Int): Long? {
        return prefs.getLong("${subKey(subscriptionId)}stored_at_ms", -1L).takeIf { it > 0 }
    }

    @Synchronized
    fun listProvisionedSubscriptions(): List<Int> {
        // Keys look like "subN.state"; strip the prefix before parsing the id.
        return prefs.all.keys
            .filter { it.endsWith(".state") }
            .mapNotNull { it.substringBefore('.').removePrefix(KEY_PREFIX).toIntOrNull() }
            .sorted()
    }

    @Synchronized
    fun clear(subscriptionId: Int) {
        val p = subKey(subscriptionId)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(p) }.forEach { editor.remove(it) }
        editor.apply()
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun subKey(subscriptionId: Int): String = "${KEY_PREFIX}${subscriptionId}."

    private fun SharedPreferences.Editor.putStringOrRemove(key: String, value: String?) {
        if (value.isNullOrEmpty()) remove(key) else putString(key, value)
    }

    private fun SharedPreferences.Editor.putIntOrRemove(key: String, value: Int?) {
        if (value == null || value <= 0) remove(key) else putInt(key, value)
    }

    private fun openOrReset(): SharedPreferences {
        return try {
            create()
        } catch (e: Exception) {
            // A rotated master key (OS restore) or a corrupted file makes the
            // encrypted store permanently unreadable. Wipe and start fresh: the
            // next STATUS reply repopulates it.
            Log.w(LOG_TAG, "EncryptedSharedPreferences open failed; resetting", e)
            context.applicationContext.deleteSharedPreferences(PREFS_FILE)
            try {
                create()
            } catch (retry: Exception) {
                Log.e(LOG_TAG, "Encrypted prefs unavailable; falling back to plain", retry)
                context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val LOG_TAG = "VvmCredentialsStore"
        private const val PREFS_FILE = "vvm_credentials_v1"
        private const val KEY_PREFIX = "sub"
    }
}
