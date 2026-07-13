package com.kmp.hook.elite.localnav

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun getInternetState(context: Context): InternetState {
    return if (!internetCheck(context)) InternetState.NoConnection else InternetState.Connected
}

fun internetCheck(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
