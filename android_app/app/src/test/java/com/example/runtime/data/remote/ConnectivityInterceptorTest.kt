package com.example.runtime.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ConnectivityInterceptorTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun failsFastWhenOffline() {
        val error = runCatching { call { false } }.exceptionOrNull()

        assertTrue(error is NoConnectivityException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun passesThroughWhenOnline() {
        server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(200, call { true })
    }

    @Test
    fun keepsServerErrorsWhenOnline() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val error = runCatching { call { true } }.exceptionOrNull()

        assertTrue(error is IOException)
        assertFalse(error is NoConnectivityException)
    }

    @Test
    fun reportsOfflineWhenConnectionDropsMidRequest() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        var checks = 0

        val error = runCatching { call { checks++ == 0 } }.exceptionOrNull()

        assertTrue(error is NoConnectivityException)
    }

    private fun call(isOnline: () -> Boolean): Int =
        OkHttpClient.Builder()
            .addInterceptor(ConnectivityInterceptor(isOnline))
            .retryOnConnectionFailure(false)
            .build()
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()
            .use { it.code }
}
