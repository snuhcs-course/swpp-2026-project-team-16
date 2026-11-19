package com.example.runtime.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryBatchRequest(val events: List<TelemetryEventDto>)

@Serializable
data class TelemetryEventDto(
    val event_name: String,
    val outcome: String,
    val duration_ms: Long? = null,
    val failure_category: String? = null,
    val app_version: String,
)

@Serializable
data class TelemetryAccepted(val accepted: Int)
