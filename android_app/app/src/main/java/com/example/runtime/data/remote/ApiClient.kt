package com.example.runtime.data.remote

import com.example.runtime.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object TokenStore {
    @Volatile
    var token: String? = null
}

object SessionEvents {
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val expired = _expired.asSharedFlow()

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}

class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {},
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Token $token")
                .build()
        }
        val response = chain.proceed(request)
        if (response.code == HTTP_UNAUTHORIZED && !token.isNullOrBlank() && tokenProvider() == token) {
            onUnauthorized()
        }
        return response
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}

class RedactingLoggingInterceptor : Interceptor {
    private val bodyLogger = HttpLoggingInterceptor()
        .setLevel(HttpLoggingInterceptor.Level.BODY)
        .apply { redactHeader("Authorization") }
    private val basicLogger = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)

    override fun intercept(chain: Interceptor.Chain): Response {
        val logger = if (chain.request().url.encodedPath.contains("/auth/")) basicLogger else bodyLogger
        return logger.intercept(chain)
    }
}

object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private const val READ_TIMEOUT_SECONDS = 45L

    val api: ApiService by lazy { create(BuildConfig.BASE_URL) }

    fun create(
        baseUrl: String,
        tokenProvider: () -> String? = { TokenStore.token },
        onUnauthorized: () -> Unit = SessionEvents::notifyExpired,
    ): ApiService {
        val client = OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider, onUnauthorized))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(RedactingLoggingInterceptor())
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

