package com.example.aiza.voice

import com.example.aiza.core.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuration preferences for Aiza's voice assistant system.
 */
data class VoiceSettings(
    val isVoiceAssistantEnabled: Boolean = true,
    val isWakeWordEnabled: Boolean = false,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val preferredLanguage: Language = Language.ENGLISH,
    val selectedVoiceName: String? = null,
    val autoSpeakResponses: Boolean = true
)

class VoiceSettingsRepository {
    private val _settings = MutableStateFlow(VoiceSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    fun updateSettings(transform: (VoiceSettings) -> VoiceSettings) {
        _settings.value = transform(_settings.value)
    }

    fun setVoiceAssistantEnabled(enabled: Boolean) {
        updateSettings { it.copy(isVoiceAssistantEnabled = enabled) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        updateSettings { it.copy(isWakeWordEnabled = enabled) }
    }

    fun setSpeechRate(rate: Float) {
        updateSettings { it.copy(speechRate = rate.coerceIn(0.5f, 2.0f)) }
    }

    fun setPitch(pitch: Float) {
        updateSettings { it.copy(pitch = pitch.coerceIn(0.5f, 2.0f)) }
    }

    fun setPreferredLanguage(language: Language) {
        updateSettings { it.copy(preferredLanguage = language) }
    }

    fun setSelectedVoiceName(voiceName: String?) {
        updateSettings { it.copy(selectedVoiceName = voiceName) }
    }

    fun setAutoSpeakResponses(autoSpeak: Boolean) {
        updateSettings { it.copy(autoSpeakResponses = autoSpeak) }
    }
}
