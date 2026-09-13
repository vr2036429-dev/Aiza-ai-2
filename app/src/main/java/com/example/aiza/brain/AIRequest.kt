package com.example.aiza.brain

import com.example.aiza.core.model.Language

/**
 * Chat message role for multi-turn AI reasoning.
 */
enum class AIRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class AIMessage(
    val role: AIRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AIRequest(
    val prompt: String,
    val conversationHistory: List<AIMessage> = emptyList(),
    val targetLanguage: Language = Language.ENGLISH,
    val userName: String = "Asik",
    val systemPromptOverride: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024
)

data class AIResponse(
    val content: String,
    val detectedLanguage: Language,
    val modelName: String,
    val totalTokens: Int = 0,
    val latencyMs: Long = 0L,
    val finishReason: String = "STOP",
    val isFallback: Boolean = false
)
