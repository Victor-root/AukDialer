package auk.dialer.vroot.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import auk.dialer.vroot.BuildConfig

/**
 * Backs the debug-only "Simulate an incoming call" Settings button. Guarded by BuildConfig.DEBUG so
 * it never registers a test call line or reaches DebugCallConnectionService outside a debug build.
 */
object DebugCallSimulator {
    private const val ACCOUNT_ID = "auk_debug_test_call"
    private const val TEST_NUMBER = "0600000000"

    fun simulateIncomingCall(context: Context) {
        if (!BuildConfig.DEBUG) return

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = PhoneAccountHandle(
            ComponentName(context, DebugCallConnectionService::class.java),
            ACCOUNT_ID
        )

        val account = PhoneAccount.builder(handle, "Auk test line")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecomManager.registerPhoneAccount(account)

        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("tel", TEST_NUMBER, null))
        }
        telecomManager.addNewIncomingCall(handle, extras)
    }
}
