package com.example.aiza.brain

/**
 * Replaceable abstraction for AI reasoning brains powering Aiza.
 * Implementations can include Gemini, local models, or alternative endpoints.
 */
interface AIProvider {
    val id: String
    val displayName: String
    val isConfigured: Boolean

    suspend fun generateResponse(request: AIRequest): Result<AIResponse>
    suspend fun ping(): Boolean
}
