package com.octo4a.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.octo4a.repository.LoggerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

enum class IPAddressType {
    Wifi,
    Ethernet,
    VPN,
    Cellular,
}

data class IPAddress(val type: IPAddressType, val address: String, val port: String = "", val isIpv6: Boolean = false)

class NetworkStatusViewModel(context: Application, val logger: LoggerRepository): AndroidViewModel(context) {
    private var _ipAddresses = MutableStateFlow(listOf<IPAddress>())
    val ipAddresses = _ipAddresses.asLiveData()
    lateinit var callback: ConnectivityManager.NetworkCallback
    init {
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
         callback = object : ConnectivityManager.NetworkCallback() {
                // network is available for use
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    scanIPAddresses()
                }

                // Network capabilities have changed for the network
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    scanIPAddresses()
                }

                // lost network connection
                override fun onLost(network: Network) {
                    super.onLost(network)
                    scanIPAddresses()
                }
            }
            val connectivityManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(ConnectivityManager::class.java)
            } else {
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            }
             val networkRequest = NetworkRequest.Builder()
                     .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                     .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                     .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                     .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                     .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                     .build()
                connectivityManager.requestNetwork(networkRequest, callback)


        } else {
            // do something for phones running an SDK before lollipop
        }
        scanIPAddresses()
    }

    override fun onCleared() {
        super.onCleared()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val connectivityManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getApplication<Application>().getSystemService(ConnectivityManager::class.java)
            } else {
                getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            }
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun scanIPAddresses() {
        val ipAddresses = mutableListOf<IPAddress>()
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.getInetAddresses()
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress ) {
                        val ipAddress = inetAddress.getHostAddress().toString()
                        Log.d("INFNAME", intf.name)
                        val type = when {
                            intf.name.startsWith("wlan") -> IPAddressType.Wifi
                            intf.name.startsWith("eth") -> IPAddressType.Ethernet
                            intf.name.startsWith("tun") -> IPAddressType.VPN
                            intf.name.startsWith("ppp") -> IPAddressType.Cellular
                            intf.name.startsWith("rmnet")  || intf.name.startsWith("v4-rmnet")-> IPAddressType.Cellular
                            else -> IPAddressType.Wifi
                        }

                        ipAddresses.add(IPAddress(type, ipAddress, isIpv6 = ipAddress.contains(":")))
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
            logger.log { "Error while getting IP addresses: ${ex}" }
            ex.printStackTrace()
        }
        _ipAddresses.value = ipAddresses.sortedBy { it.address }.sortedBy { it.type.ordinal }.sortedBy { it.isIpv6 }
    }
}