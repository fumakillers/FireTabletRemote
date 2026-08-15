package com.fumakillers.fireremoteserver.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object DeviceIpAddressResolver {
    fun resolve(context: Context): String? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null && connectivityManager.getNetworkCapabilities(activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        ) {
            resolveFromLinkProperties(connectivityManager, activeNetwork)?.let { return it }
        }
        resolveFromInterfaces()?.let { return it }
        return activeNetwork?.let { resolveFromLinkProperties(connectivityManager, it) }
    }

    private fun resolveFromLinkProperties(
        connectivityManager: ConnectivityManager,
        network: android.net.Network,
    ): String? = connectivityManager.getLinkProperties(network)?.linkAddresses
        ?.asSequence()
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress

    private fun resolveFromInterfaces(): String? {
        val interfaces = mutableListOf<NetworkInterface>()
        val enumeration = NetworkInterface.getNetworkInterfaces() ?: return null
        while (enumeration.hasMoreElements()) interfaces += enumeration.nextElement()
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.contains("wlan", ignoreCase = true) }
            .flatMap { networkInterface ->
                val addresses = mutableListOf<java.net.InetAddress>()
                val addressEnumeration = networkInterface.inetAddresses
                while (addressEnumeration.hasMoreElements()) addresses += addressEnumeration.nextElement()
                addresses.asSequence()
            }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }
}
