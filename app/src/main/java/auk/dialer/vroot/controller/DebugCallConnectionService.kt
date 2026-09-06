package auk.dialer.vroot.controller

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import auk.dialer.vroot.BuildConfig

/**
 * Backs the debug-only "Simulate an incoming call" button (see [DebugCallSimulator]): a
 * self-managed Telecom line that never reaches a SIM or the real network. CallService still
 * receives it through onCallAdded exactly like a genuine call, so CallActivity/CallScreen need no
 * changes to show it. Declared in the manifest for every build, but Telecom never routes anything
 * to it unless [DebugCallSimulator] has registered its account, which it only does in debug builds.
 */
class DebugCallConnectionService : ConnectionService() {

    private fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        d("onCreateIncomingConnection: address=${request.address}")
        return createTestConnection(request).apply { setRinging() }
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        d("onCreateIncomingConnectionFailed: account=$connectionManagerPhoneAccount")
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        d("onCreateOutgoingConnection: address=${request.address}")
        return createTestConnection(request).apply { setDialing() }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        d("onCreateOutgoingConnectionFailed: account=$connectionManagerPhoneAccount")
    }

    private fun createTestConnection(request: ConnectionRequest): Connection {
        return object : Connection() {
            override fun onAnswer(videoState: Int) {
                d("connection.onAnswer")
                setActive()
            }

            override fun onReject(rejectReason: Int) {
                d("connection.onReject")
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                d("connection.onDisconnect")
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }

            override fun onAbort() {
                d("connection.onAbort")
                setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
                destroy()
            }

            override fun onHold() {
                setOnHold()
            }

            override fun onUnhold() {
                setActive()
            }
        }.apply {
            setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
            connectionCapabilities = Connection.CAPABILITY_HOLD or Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_MUTE
            audioModeIsVoip = true
        }
    }

    private companion object {
        private const val TAG = "AukCallDebug"
    }
}
