package com.example.aiza.brain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Registry managing available AI reasoning providers for Aiza.
 * Allows seamless hot-swapping between providers (Gemini, Local SLMs, etc.).
 */
class AIProviderRegistry(initialProvider: AIProvider) {

    private val providers = mutableMapOf<String, AIProvider>()

    private val _activeProvider = MutableStateFlow(initialProvider)
    val activeProvider: StateFlow<AIProvider> = _activeProvider.asStateFlow()

    init {
        registerProvider(initialProvider)
    }

    fun registerProvider(provider: AIProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): AIProvider? = providers[id]

    fun getAllProviders(): List<AIProvider> = providers.values.toList()

    fun setActiveProvider(id: String): Boolean {
        val provider = providers[id] ?: return false
        _activeProvider.value = provider
        return true
    }
}
