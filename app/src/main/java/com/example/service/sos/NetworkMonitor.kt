package com.example.service.sos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NetworkMonitor"

/**
 * Giám sát trạng thái kết nối mạng thời gian thực (Wi-Fi, 4G/5G, Ethernet).
 * Giúp hệ thống tự động nhận biết khi nào có mạng để dùng Cloud Internet,
 * và khi nào mất mạng để kích hoạt chế độ ngoại tuyến P2P Mesh / SMS khẩn cấp.
 */
class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /**
     * Mang dang hoat dong co phai VPN khong. Noi ra de app noi that voi nguoi
     * dung ("may dang bat VPN") thay vi de ho doan; vi tri van lay tu GPS cua
     * may nen VPN khong lam sai toa do.
     */
    private val _isVpnActive = MutableStateFlow(checkVpnActive())
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        startMonitoring()
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun checkVpnActive(): Boolean {
        val cm = connectivityManager ?: return false
        val capabilities = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun startMonitoring() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return

        // Them TRANSPORT_VPN: may bat VPN thi activeNetwork la mang VPN, thieu
        // no thi callback khong bao gio chay va app bao "Ngoại tuyến" trong khi
        // Internet van dung — SOS bi day sang kenh SMS khong can thiet.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Mạng Internet đã kết nối (Online)")
                _isOnline.value = true
                _isVpnActive.value = checkVpnActive()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Mất kết nối Internet (Offline)")
                _isOnline.value = checkInitialConnectivity()
                _isVpnActive.value = checkVpnActive()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                _isOnline.value = hasInternet
                _isVpnActive.value = checkVpnActive()
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e(TAG, "Không thể đăng ký NetworkCallback: ${e.message}")
            _isOnline.value = checkInitialConnectivity()
            _isVpnActive.value = checkVpnActive()
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi khi hủy NetworkCallback: ${e.message}")
            }
        }
        networkCallback = null
    }
}
