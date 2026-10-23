package com.example.runtime.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private val server = MockWebServer()
    private var token: String? = null
    private var unauthorizedCalls = 0

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun addsTokenHeaderWhenLoggedIn() {
        token = "abc"
        call(200)

        assertEquals("Token abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun sendsNoTokenHeaderWhenLoggedOut() {
        call(200)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun reportsExpiredSessionOn401() {
        token = "abc"
        call(401)

        assertEquals(1, unauthorizedCalls)
    }

    @Test
    fun ignores401WithoutToken() {
        call(401)

        assertEquals(0, unauthorizedCalls)
    }

    @Test
    fun ignores401WhenTokenChangedDuringRequest() {
        token = "old"
        val client = client(onResponse = { token = "new" })
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(0, unauthorizedCalls)
    }

    @Test
    fun ignoresOtherErrors() {
        token = "abc"
        call(403)
        call(500)

        assertEquals(0, unauthorizedCalls)
    }

    private fun call(code: Int) {
        server.enqueue(MockResponse().setResponseCode(code))
        client().newCall(Request.Builder().url(server.url("/")).build()).execute().close()
    }

    private fun client(onResponse: () -> Unit = {}) = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor({ token }, { unauthorizedCalls++ }))
        .addNetworkInterceptor { chain -> chain.proceed(chain.request()).also { onResponse() } }
        .build()
}
