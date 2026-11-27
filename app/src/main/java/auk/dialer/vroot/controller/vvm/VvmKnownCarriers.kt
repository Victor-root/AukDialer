package auk.dialer.vroot.controller.vvm

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
        val sslEnabled: Boolean,
    )

    private val BY_MCC_MNC: Map<String, Entry> = buildMap {
        // Orange France, from AOSP's own table in
        // packages/services/Telephony/res/xml/vvm_config.xml.
        val orangeFrance = Entry("21101", 20481, cellularDataRequired = true, sslEnabled = false)
        put("20801", orangeFrance)
        put("20802", orangeFrance)

        // Free Mobile France, read off a device whose vendor database does
        // carry the entry, since no published table lists it. The same SIM in a
        // handset whose database omits it reports no voicemail service at all,
        // which is what this covers.
        put("20815", Entry("2051", 5499, cellularDataRequired = true, sslEnabled = true))
    }

    // Free Mobile gives its mailbox only to the clients it knows: one that reached it, in 2013,
    // asked as Free's own app, "STATUS:pv=13;ct=free.VVM.10;pt=5499;//VVM"
    // (talk.maemo.org/showthread.php?t=89093).
    private val CLIENT_TYPE_BY_MCC_MNC: Map<String, String> = mapOf("20815" to "free.VVM.10")

    fun lookup(context: Context, subscriptionId: Int): Entry? =
        BY_MCC_MNC[simOperator(context, subscriptionId)]

    /** The client type a carrier expects its requests to name, null for one that does not say. */
    fun clientType(context: Context, subscriptionId: Int): String? =
        CLIENT_TYPE_BY_MCC_MNC[simOperator(context, subscriptionId)]

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
