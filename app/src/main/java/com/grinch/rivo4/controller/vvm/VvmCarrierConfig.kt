package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * What the system knows about a SIM's visual voicemail service.
 *
 * CarrierConfig is the authoritative source here: it is what tells us whether
 * the carrier speaks a protocol we implement, where to reach it, and whether
 * its servers are only accessible over its own network.
 */
data class VvmCarrierConfig(
    val subscriptionId: Int,
    val carrierName: String,
    val vvmType: String,
    val destinationNumber: String,
    val portNumber: Int,
    val clientPrefix: String,
    val sslEnabled: Boolean,
    val cellularDataRequired: Boolean,
    /**
     * False when the values are Android's built-in defaults rather than anything
     * matched to this SIM, which happens while a SIM is still initialising.
     * Telling the two apart avoids reading "no voicemail service" into a config
     * that has simply not loaded yet.
     */
    val configApplied: Boolean = false,
    /** Where the settings came from, since a fallback is far less trustworthy. */
    val source: Source = Source.PLATFORM,
) {

    enum class Source {
        /** The device's own carrier database, which is always preferred. */
        PLATFORM,

        /** The table this app carries for carriers the device does not describe. */
        BUILT_IN,

        /** Values entered by hand for a carrier nothing else covers. */
        MANUAL,
    }

    /**
     * True only for the OMTP dialect this app implements. Other carriers use
     * CVVM or vendor protocols whose provisioning exchange differs, so sending
     * them an OMTP request would be noise at best.
     */
    val isSupported: Boolean
        get() = vvmType.equals(TelephonyManager.VVM_TYPE_OMTP, ignoreCase = true)

    val canBeProvisioned: Boolean
        get() = isSupported && destinationNumber.isNotBlank()

    companion object {
        /**
         * OMTP 1.3 client prefix, used only when the carrier declares none.
         * Matches the platform default; note there is no trailing colon, that
         * belongs to the message syntax rather than the prefix.
         */
        const val DEFAULT_CLIENT_PREFIX = "//VVM"

        fun readAll(context: Context): List<VvmCarrierConfig> {
            return try {
                val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                    ?: return emptyList()
                val subs = subscriptionManager.activeSubscriptionInfoList ?: return emptyList()
                subs.map { sub ->
                    read(context, sub.subscriptionId, sub.carrierName?.toString() ?: "")
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun read(context: Context, subscriptionId: Int, carrierName: String = ""): VvmCarrierConfig {
            val bundle = try {
                context.getSystemService(CarrierConfigManager::class.java)
                    ?.getConfigForSubId(subscriptionId)
                    ?: PersistableBundle.EMPTY
            } catch (_: Exception) {
                PersistableBundle.EMPTY
            }
            val declaredType = bundle.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING, "") ?: ""
            val declaredDestination =
                bundle.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING, "") ?: ""

            // Only stand in for a carrier the platform says nothing about. The
            // vendor config database is the authority wherever it has an answer.
            val fallback = if (declaredType.isBlank() && declaredDestination.isBlank()) {
                manualFallback(context, subscriptionId)
                    ?: builtInFallback(context, subscriptionId)
            } else {
                null
            }

            return VvmCarrierConfig(
                subscriptionId = subscriptionId,
                carrierName = carrierName,
                vvmType = if (fallback != null) TelephonyManager.VVM_TYPE_OMTP else declaredType,
                destinationNumber = fallback?.destinationNumber ?: declaredDestination,
                portNumber = fallback?.portNumber
                    ?: bundle.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0),
                clientPrefix = bundle.getString(CarrierConfigManager.KEY_VVM_CLIENT_PREFIX_STRING, "")
                    ?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT_CLIENT_PREFIX,
                sslEnabled = bundle.getBoolean(CarrierConfigManager.KEY_VVM_SSL_ENABLED_BOOL, false),
                cellularDataRequired = fallback?.cellularDataRequired ?: bundle.getBoolean(
                    CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL,
                    false,
                ),
                configApplied = CarrierConfigManager.isConfigForIdentifiedCarrier(bundle),
                source = fallback?.source ?: Source.PLATFORM,
            )
        }

        private data class Fallback(
            val destinationNumber: String,
            val portNumber: Int,
            val cellularDataRequired: Boolean,
            val source: Source,
        )

        /** Deliberately ahead of the built-in table: a person who typed a value knows better. */
        private fun manualFallback(context: Context, subscriptionId: Int): Fallback? {
            return VvmConfigOverride.load(context, subscriptionId)?.let {
                // Nothing here describes this carrier, and carrier mailboxes are
                // usually reachable from their own network only, so assume one is
                // needed. Getting that wrong only costs a wait before falling back.
                Fallback(it.destinationNumber, it.portNumber, true, Source.MANUAL)
            }
        }

        private fun builtInFallback(context: Context, subscriptionId: Int): Fallback? {
            return VvmKnownCarriers.lookup(context, subscriptionId)?.let {
                Fallback(it.destinationNumber, it.portNumber, it.cellularDataRequired, Source.BUILT_IN)
            }
        }
    }
}
