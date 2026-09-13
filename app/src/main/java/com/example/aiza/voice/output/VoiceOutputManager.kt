package com.example.aiza.voice.output

import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStateManager

/**
 * High-level manager coordinating text-to-speech output, interruption, and voice states.
 */
class VoiceOutputManager(
    private val ttsEngine: TextToSpeechEngine,
    private val stateManager: VoiceStateManager,
    private val logger: DiagnosticLogger
) {
    // Interruption / completion listeners (used by UI, background engine, and future 3D avatar)
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null
    var onSpeechInterrupted: (() -> Unit)? = null

    fun initialize(onReady: (Boolean) -> Unit = {}) {
        ttsEngine.initialize(onReady)
    }

    fun speak(
        text: String,
        languageCode: String = "en",
        onDone: () -> Unit = {}
    ) {
        if (text.isBlank()) {
            stateManager.resetToIdle()
            onDone()
            return
        }

        // Clean any markdown formatting (asterisks, hashtags) for natural spoken audio
        val cleanSpokenText = sanitizeForSpeech(text)

        ttsEngine.speak(
            text = cleanSpokenText,
            languageCode = languageCode,
            onStart = {
                stateManager.transitionTo(VoiceState.SPEAKING)
                onSpeechStarted?.invoke()
            },
            onDone = {
                stateManager.resetToIdle()
                onSpeechCompleted?.invoke()
                onDone()
            },
            onError = { errorMessage ->
                stateManager.transitionTo(
                    VoiceState.ERROR,
                    error = VoiceError.TextToSpeechError(errorMessage)
                )
                logger.logDiagnostic(
                    category = "VOICE_OUTPUT",
                    message = "TTS Speech Error: $errorMessage",
                    level = DiagnosticLogger.Level.ERROR
                )
                onDone()
            }
        )
    }

    /**
     * Interrupts speech immediately on user tap, hotword, or UI control.
     */
    fun stopSpeaking() {
        ttsEngine.stop()
        stateManager.resetToIdle()
        onSpeechInterrupted?.invoke()
        logger.logDiagnostic(
            category = "VOICE_OUTPUT",
            message = "Voice output was interrupted by Asik.",
            level = DiagnosticLogger.Level.INFO
        )
    }

    fun setSpeechRate(rate: Float) {
        ttsEngine.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        ttsEngine.setPitch(pitch)
    }

    fun setVoice(voiceName: String): Boolean {
        return ttsEngine.setVoice(voiceName)
    }

    fun getAvailableVoices(): List<VoiceInfo> {
        return ttsEngine.getAvailableVoices()
    }

    fun shutdown() {
        ttsEngine.shutdown()
    }

    private fun sanitizeForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("[*#_`~]"), "") // Strip markdown symbols
            .replace(Regex("\\[.*?\\]"), "") // Strip tags like [TOOL]
            .replace(Regex("http\\S+"), "web link")
            .trim()
    }
}
