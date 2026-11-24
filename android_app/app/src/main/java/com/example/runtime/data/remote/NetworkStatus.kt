package com.example.runtime.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class NoConnectivityException(cause: Throwable? = null) : IOException("No internet connection", cause)

object NetworkStatus {
    @Volatile
    var isOnline: () -> Boolean = { true }

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    fun initialize(context: Context) {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        isOnline = {
            manager.getNetworkCapabilities(manager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
        _online.value = isOnline()
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                _online.value = false
            }
        })
    }
}

class ConnectivityInterceptor(
    private val isOnline: () -> Boolean = { NetworkStatus.isOnline() },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isOnline()) throw NoConnectivityException()
        return try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            if (e !is NoConnectivityException && !isOnline()) throw NoConnectivityException(e)
            throw e
        }
    }
}
