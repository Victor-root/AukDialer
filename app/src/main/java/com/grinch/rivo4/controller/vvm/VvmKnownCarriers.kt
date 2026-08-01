package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Voicemail settings for carriers whose values are published, keyed by the
 * SIM's MCC and MNC.
 *
 * The database a phone normally answers this from is not part of Android and
 * ships with the vendor, so a device whose vendor left a carrier out reports no
 * voicemail service at all. A small table of the carriers that are documented
 * covers those devices, exactly as the AOSP dialer carries one of its own.
 *
 * Every entry must come from a source that can be cited. A destination number
 * costs a real SMS to a real recipient, so a guessed one is worse than none.
 */
object VvmKnownCarriers {

    data class Entry(
        val destinationNumber: String,
        val portNumber: Int,
        val cellularDataRequired: Boolean,
    )

    private val BY_MCC_MNC: Map<String, Entry> = buildMap {
        // Orange France, from AOSP's own table in
        // packages/services/Telephony/res/xml/vvm_config.xml.
        val orangeFrance = Entry("21101", 20481, cellularDataRequired = true)
        put("20801", orangeFrance)
        put("20802", orangeFrance)
    }

    fun lookup(context: Context, subscriptionId: Int): Entry? =
        BY_MCC_MNC[simOperator(context, subscriptionId)]

    private fun simOperator(context: Context, subscriptionId: Int): String? {
        return try {
            context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(subscriptionId)
                ?.simOperator
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
