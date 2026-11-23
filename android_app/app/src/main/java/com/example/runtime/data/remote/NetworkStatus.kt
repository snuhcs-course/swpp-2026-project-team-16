package com.example.runtime.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class NoConnectivityException(cause: Throwable? = null) : IOException("No internet connection", cause)

object NetworkStatus {
    @Volatile
    var isOnline: () -> Boolean = { true }

    fun initialize(context: Context) {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        isOnline = {
            manager.getNetworkCapabilities(manager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
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
