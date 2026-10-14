package com.example.runtime.data.remote

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

class EmptyResponseException(val serverMessage: String?) : IllegalStateException(serverMessage)

fun HttpException.serverMessage(): String? {
    val body = response()?.errorBody()?.string() ?: return null
    return runCatching {
        ApiClient.json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
    }.getOrNull()
}
