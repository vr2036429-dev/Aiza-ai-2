package com.example.aiza.voice

import com.example.aiza.diagnostics.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages reactive voice states across the application.
 * Exposes state transitions to UI, diagnostics, and future 3D avatar module.
 */
class VoiceStateManager(
    private val logger: DiagnosticLogger
) {
    private val _status = MutableStateFlow(VoiceStatusSnapshot())
    val status: StateFlow<VoiceStatusSnapshot> = _status.asStateFlow()

    fun transitionTo(newState: VoiceState, partialText: String = "", error: VoiceError? = null) {
        val current = _status.value
        _status.value = current.copy(
            state = newState,
            partialText = partialText,
            error = error,
            isListening = newState == VoiceState.LISTENING,
            isSpeaking = newState == VoiceState.SPEAKING
        )

        logger.logDiagnostic(
            category = "VOICE_STATE",
            message = "Voice State -> $newState" +
                    if (partialText.isNotBlank()) " | Partial: '$partialText'" else "" +
                    if (error != null) " | Error: ${error.userMessage}" else "",
            level = if (newState == VoiceState.ERROR) DiagnosticLogger.Level.WARNING else DiagnosticLogger.Level.DEBUG
        )
    }

    fun updateRms(soundLevelDb: Float) {
        _status.value = _status.value.copy(soundLevelDb = soundLevelDb)
    }

    fun resetToIdle() {
        _status.value = VoiceStatusSnapshot(state = VoiceState.IDLE)
        logger.logDiagnostic(
            category = "VOICE_STATE",
            message = "Voice State -> IDLE",
            level = DiagnosticLogger.Level.DEBUG
        )
    }
}
