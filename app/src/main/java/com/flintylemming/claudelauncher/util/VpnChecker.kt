package com.flintylemming.claudelauncher.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface
import java.util.Collections

object VpnChecker {

    /**
     * Checks whether an active VPN connection exists on the device.
     *
     * 1. Checks the default active network for [NetworkCapabilities.TRANSPORT_VPN].
     * 2. Checks all available networks for [NetworkCapabilities.TRANSPORT_VPN].
     * 3. Falls back to inspecting active network interfaces for virtual VPN tunnels (tun, ppp, tap, vpn, wg).
     */
    @Suppress("DEPRECATION")
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }

            val allNetworks = cm.allNetworks
            for (network in allNetworks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
        }

        return hasVpnNetworkInterface()
    }

    /**
     * Inspects network interfaces for common VPN virtual interfaces.
     */
    fun hasVpnNetworkInterface(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (iface in Collections.list(interfaces)) {
                if (iface.isUp && !iface.isLoopback) {
                    val name = iface.name.lowercase()
                    if (name.startsWith("tun") ||
                        name.startsWith("ppp") ||
                        name.startsWith("tap") ||
                        name.contains("vpn") ||
                        name.startsWith("wg")
                    ) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
