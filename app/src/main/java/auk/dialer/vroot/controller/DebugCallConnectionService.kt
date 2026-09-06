package auk.dialer.vroot.controller

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/**
 * Backs the debug-only "Simulate an incoming call" button (see [DebugCallSimulator]): a
 * self-managed Telecom line that never reaches a SIM or the real network. CallService still
 * receives it through onCallAdded exactly like a genuine call, so CallActivity/CallScreen need no
 * changes to show it. Declared in the manifest for every build, but Telecom never routes anything
 * to it unless [DebugCallSimulator] has registered its account, which it only does in debug builds.
 */
class DebugCallConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection = createTestConnection(request).apply { setRinging() }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection = createTestConnection(request).apply { setDialing() }

    private fun createTestConnection(request: ConnectionRequest): Connection {
        return object : Connection() {
            override fun onAnswer(videoState: Int) {
                setActive()
            }

            override fun onReject(rejectReason: Int) {
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }

            override fun onAbort() {
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
}
