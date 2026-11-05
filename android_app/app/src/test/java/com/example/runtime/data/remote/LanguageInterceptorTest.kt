package com.example.runtime.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LanguageInterceptorTest {

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
    fun sendsTheAppLanguage() {
        call(Request.Builder().url(server.url("/")).build())

        assertEquals("ko-KR", server.takeRequest().getHeader("Accept-Language"))
    }

    @Test
    fun keepsAnExplicitLanguage() {
        call(Request.Builder().url(server.url("/")).header("Accept-Language", "en").build())

        assertEquals("en", server.takeRequest().getHeader("Accept-Language"))
    }

    private fun call(request: Request) {
        server.enqueue(MockResponse().setResponseCode(200))
        OkHttpClient.Builder()
            .addInterceptor(LanguageInterceptor { "ko-KR" })
            .build()
            .newCall(request)
            .execute()
            .close()
    }
}
