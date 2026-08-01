package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Sends the mobile-originated OMTP requests as hidden SMS.
 *
 * Two are needed in practice: STATUS asks the carrier for the mailbox state and
 * credentials, and Activate turns the mailbox on for a subscriber the carrier
 * knows but has not provisioned yet. Both go to the number and port the carrier
 * publishes in its config, never to a hardcoded destination.
 */
object VvmRequestSender {

    sealed class Result {
        object Sent : Result()
        data class Skipped(val reason: String) : Result()
        data class Failed(val errorType: String, val errorMessage: String) : Result()
    }

    /** OMTP 1.3 STATUS request: a bare keyword, no fields. */
    private const val STATUS_REQUEST = "STATUS"

    /**
     * OMTP activation request. The spec carries the protocol version the client
     * speaks and a client type; carriers that do not care simply ignore them.
     */
    private const val ACTIVATE_REQUEST = "Activate"
    private const val PROTOCOL_VERSION = "13"
    private const val CLIENT_TYPE = "rivo"

    private const val SEND_SMS_PERMISSION = "android.permission.SEND_SMS"
    private const val LOG_TAG = "VvmRequestSender"

    fun sendStatus(context: Context, config: VvmCarrierConfig): Result =
        send(context, config, STATUS_REQUEST)

    fun sendActivate(context: Context, config: VvmCarrierConfig): Result =
        send(context, config, "$ACTIVATE_REQUEST:pv=$PROTOCOL_VERSION;ct=$CLIENT_TYPE")

    private fun send(context: Context, config: VvmCarrierConfig, body: String): Result {
        if (!config.isSupported) {
            return Result.Skipped("protocol ${config.vvmType.ifBlank { "<none>" }} not supported")
        }
        if (config.destinationNumber.isBlank()) {
            return Result.Skipped("carrier declares no destination number")
        }
        if (context.checkSelfPermission(SEND_SMS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return Result.Skipped("SEND_SMS not granted")
        }
        return try {
            context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(config.subscriptionId)
                ?.sendVisualVoicemailSms(config.destinationNumber, config.portNumber, body, null)
                ?: return Result.Failed("NoTelephony", "TelephonyManager unavailable")
            Log.i(LOG_TAG, "Sent ${body.substringBefore(':')} on subId=${config.subscriptionId}")
            Result.Sent
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Request failed on subId=${config.subscriptionId}", e)
            Result.Failed(e.javaClass.simpleName, e.message ?: "<no message>")
        }
    }
}
