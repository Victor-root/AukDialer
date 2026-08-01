package com.grinch.rivo4.controller.vvm

import android.content.Context

/**
 * Manually supplied voicemail settings for a SIM, used only when the platform
 * describes none.
 *
 * The carrier database that normally answers this is not part of Android: AOSP
 * ships thirty-odd carriers and a single visual voicemail entry, the real one
 * comes with the vendor's own config app. On a device whose vendor never filled
 * in a given carrier, nothing readable describes the service, and the values
 * have to come from somewhere else.
 *
 * A real carrier config always wins, so setting this can never contradict a
 * device that already knows the answer.
 */
object VvmConfigOverride {

    data class Settings(val destinationNumber: String, val portNumber: Int)

    fun load(context: Context, subscriptionId: Int): Settings? {
        val prefs = prefs(context)
        val number = prefs.getString(key(subscriptionId, KEY_NUMBER), null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return Settings(number, prefs.getInt(key(subscriptionId, KEY_PORT), 0))
    }

    fun save(context: Context, subscriptionId: Int, destinationNumber: String, portNumber: Int) {
        prefs(context).edit()
            .putString(key(subscriptionId, KEY_NUMBER), destinationNumber.trim())
            .putInt(key(subscriptionId, KEY_PORT), portNumber)
            .apply()
    }

    fun clear(context: Context, subscriptionId: Int) {
        prefs(context).edit()
            .remove(key(subscriptionId, KEY_NUMBER))
            .remove(key(subscriptionId, KEY_PORT))
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(subscriptionId: Int, suffix: String) = "$subscriptionId.$suffix"

    private const val PREFS_NAME = "vvm_config_override"
    private const val KEY_NUMBER = "destination"
    private const val KEY_PORT = "port"
}
