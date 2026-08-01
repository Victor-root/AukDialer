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
) {
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
            return VvmCarrierConfig(
                subscriptionId = subscriptionId,
                carrierName = carrierName,
                vvmType = bundle.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING, "") ?: "",
                destinationNumber = bundle.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING, "") ?: "",
                portNumber = bundle.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT, 0),
                clientPrefix = bundle.getString(CarrierConfigManager.KEY_VVM_CLIENT_PREFIX_STRING, "")
                    ?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT_CLIENT_PREFIX,
                sslEnabled = bundle.getBoolean(CarrierConfigManager.KEY_VVM_SSL_ENABLED_BOOL, false),
                cellularDataRequired = bundle.getBoolean(
                    CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL,
                    false,
                ),
            )
        }
    }
}
