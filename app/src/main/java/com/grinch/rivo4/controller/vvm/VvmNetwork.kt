package com.grinch.rivo4.controller.vvm

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TelephonyNetworkSpecifier
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs carrier mailbox traffic over a specific SIM's mobile data.
 *
 * Many voicemail servers sit inside the carrier network and are unreachable
 * from Wi-Fi, and on a dual-SIM phone the default data connection may belong to
 * the other line entirely. Where the carrier says so, the work has to be pinned
 * to a cellular network tied to that subscription.
 */
object VvmNetwork {

    private const val LOG_TAG = "VvmNetwork"
    private const val REQUEST_TIMEOUT_MS = 20_000
    private const val AWAIT_TIMEOUT_SECONDS = 25L

    /**
     * Runs [block] with process traffic pinned to [subscriptionId]'s mobile
     * data, restoring the previous routing afterwards. Falls back to running on
     * the default network if no such connection can be obtained, since an
     * attempt that might work beats refusing outright.
     *
     * Binding is process-wide, which is blunt: it is kept to the shortest
     * possible window and always undone in a finally.
     */
    fun <T> onCellular(context: Context, subscriptionId: Int, block: () -> T): T {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return block()

        val holder = NetworkHolder()
        val callback = holder.callback
        return try {
            connectivityManager.requestNetwork(
                cellularRequest(subscriptionId),
                callback,
                REQUEST_TIMEOUT_MS,
            )
            val network = holder.await()
            if (network == null) {
                Log.w(LOG_TAG, "No cellular network for subId=$subscriptionId, using default route")
                return block()
            }
            val previous = connectivityManager.boundNetworkForProcess
            connectivityManager.bindProcessToNetwork(network)
            try {
                block()
            } finally {
                connectivityManager.bindProcessToNetwork(previous)
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Cellular binding failed for subId=$subscriptionId", e)
            block()
        } finally {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    private fun cellularRequest(subscriptionId: Int): NetworkRequest {
        return NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(
                TelephonyNetworkSpecifier.Builder()
                    .setSubscriptionId(subscriptionId)
                    .build()
            )
            .build()
    }

    private class NetworkHolder {
        private val latch = CountDownLatch(1)

        @Volatile
        private var network: Network? = null

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) {
                network = available
                latch.countDown()
            }

            override fun onUnavailable() {
                latch.countDown()
            }
        }

        fun await(): Network? {
            latch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return network
        }
    }
}
