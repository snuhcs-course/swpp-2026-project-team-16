package com.example.runtime.data.remote

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val body = response()?.errorBody()?.string()
        val message = body?.let {
            runCatching { ApiClient.json.parseToJsonElement(it).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
        }
        message ?: "Request failed (${code()})"
    }
    is IOException -> "Cannot reach the server."
    else -> message ?: "Something went wrong."
}
