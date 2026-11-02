package auk.dialer.vroot.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Takes missed-call notifications off Telecom's hands. Without a receiver for this action in the
 * default dialer, Telecom posts its own on top of the one [CallService] already shows for each call.
 */
class MissedCallsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
