package com.example.aiza.voice

/**
 * Fundamental voice assistant states required by Part 2, section 4.
 * Designed to be consumed by UI and future modules, especially the 3D Floating Avatar.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Detailed errors for voice input and output operations.
 */
sealed class VoiceError(val userMessage: String, val canRetry: Boolean = true) {
    object MicrophonePermissionDenied : VoiceError(
        "Microphone permission is required to speak with Aiza. Please grant permission.",
        canRetry = false
    )
    object MicrophoneUnavailable : VoiceError(
        "Microphone hardware is currently unavailable or in use by another app."
    )
    object SpeechRecognitionUnavailable : VoiceError(
        "Speech recognition service is not available on this device.",
        canRetry = false
    )
    object NetworkError : VoiceError(
        "Network connection lost during speech recognition. Please check your connection."
    )
    object NoSpeechDetected : VoiceError(
        "No speech was detected. Tap the microphone and speak clearly."
    )
    object LanguageNotSupported : VoiceError(
        "The selected language is not currently supported by the device speech engine."
    )
    data class TextToSpeechError(val reason: String) : VoiceError(
        "Voice output error: $reason"
    )
    data class Unknown(val message: String) : VoiceError(
        "Voice error: $message"
    )
}

/**
 * Snapshot of current voice assistant status.
 */
data class VoiceStatusSnapshot(
    val state: VoiceState = VoiceState.IDLE,
    val partialText: String = "",
    val activeUtteranceId: String? = null,
    val error: VoiceError? = null,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val soundLevelDb: Float = 0f
)
