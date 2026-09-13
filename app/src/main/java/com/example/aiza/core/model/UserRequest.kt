package com.example.aiza.core.model

import java.util.UUID

/**
 * Encapsulates an incoming request from the user (Asik).
 */
data class UserRequest(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userName: String = "Asik",
    val explicitLanguage: Language? = null,
    val source: RequestSource = RequestSource.TEXT_INPUT,
    val metadata: Map<String, Any> = emptyMap()
)

enum class RequestSource {
    TEXT_INPUT,
    VOICE_INPUT,
    BACKGROUND_TRIGGER,
    AUTOMATION_EVENT
}
