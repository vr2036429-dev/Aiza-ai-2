package com.example.aiza.voice.input

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStateManager

/**
 * Manages voice capture lifecycle, microphone permission verification,
 * partial transcript streaming, real-time RMS normalization,
 * and delivery of final speech text to Aiza Core.
 */
class VoiceInputManager(
    private val context: Context,
    private val recognitionEngine: SpeechRecognitionEngine,
    private val stateManager: VoiceStateManager,
    private val logger: DiagnosticLogger
) {
    /**
     * Fired when final recognized speech is available and converted to text.
     */
    var onSpeechRecognized: ((String) -> Unit)? = null

    /**
     * Fired when streaming partial hypotheses are updated in real-time.
     */
    var onPartialResult: ((String) -> Unit)? = null

    /**
     * Fired when an unrecoverable or transient speech recognition error occurs.
     */
    var onSpeechError: ((VoiceError) -> Unit)? = null

    /**
     * Fired specifically when microphone permission is denied or missing.
     */
    var onPermissionDenied: (() -> Unit)? = null

    /**
     * Fired when speech recognition successfully starts listening.
     */
    var onListeningStarted: (() -> Unit)? = null

    /**
     * Fired when speech recognition stops listening.
     */
    var onListeningStopped: (() -> Unit)? = null

    /**
     * Fired with normalized RMS sound amplitude (0.0f .. 1.0f) for visualizers.
     */
    var onRmsChanged: ((Float) -> Unit)? = null

    /**
     * Whether microphone permission is currently granted by the user.
     */
    val isPermissionGranted: Boolean
        get() = hasMicrophonePermission()

    /**
     * Whether voice input is actively listening for audio.
     */
    val isListening: Boolean
        get() = stateManager.status.value.isListening

    /**
     * Verifies if RECORD_AUDIO runtime permission is granted.
     */
    fun hasMicrophonePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        logger.logDiagnostic(
            category = "VoiceInput",
            message = "Microphone permission check: ${if (granted) "GRANTED" else "DENIED"}",
            level = DiagnosticLogger.Level.DEBUG
        )
        return granted
    }

    /**
     * Checks if both microphone permission is granted and speech recognition is supported.
     */
    fun isMicrophoneAvailable(): Boolean {
        return hasMicrophonePermission() && recognitionEngine.isRecognitionAvailable()
    }

    /**
     * Initiates voice listening session.
     *
     * @param languageCode BCP-47 language tag (e.g. "en-US", "hi-IN", "bn-BD").
     * @param onPermissionRequired Optional hook invoked when microphone permission is missing.
     */
    fun startListening(
        languageCode: String = "en-US",
        onPermissionRequired: (() -> Unit)? = null
    ) {
        logger.logDiagnostic(
            category = "VoiceInput",
            message = "startListening requested (language: $languageCode)",
            level = DiagnosticLogger.Level.INFO
        )

        // 1. Verify Microphone Permission
        if (!hasMicrophonePermission()) {
            val err = VoiceError.MicrophonePermissionDenied
            logger.logDiagnostic(
                category = "VoiceInput",
                message = "Microphone permission missing. Cannot capture audio.",
                level = DiagnosticLogger.Level.WARNING
            )
            stateManager.transitionTo(VoiceState.ERROR, error = err)
            onPermissionDenied?.invoke()
            onPermissionRequired?.invoke()
            onSpeechError?.invoke(err)
            return
        }

        // 2. Verify Speech Recognition Service Availability
        if (!recognitionEngine.isRecognitionAvailable()) {
            val err = VoiceError.SpeechRecognitionUnavailable
            logger.logDiagnostic(
                category = "VoiceInput",
                message = "Android SpeechRecognizer service is not available on this device.",
                level = DiagnosticLogger.Level.WARNING
            )
            stateManager.transitionTo(VoiceState.ERROR, error = err)
            onSpeechError?.invoke(err)
            return
        }

        // 3. Transition to LISTENING state
        stateManager.transitionTo(VoiceState.LISTENING)
        onListeningStarted?.invoke()

        // 4. Start speech recognition with callbacks
        recognitionEngine.startListening(
            languageCode = languageCode,
            onPartialResult = { partial ->
                stateManager.transitionTo(VoiceState.LISTENING, partialText = partial)
                onPartialResult?.invoke(partial)
            },
            onFinalResult = { finalSpeech ->
                logger.logDiagnostic(
                    category = "VoiceInput",
                    message = "Audio successfully converted to text: \"$finalSpeech\"",
                    level = DiagnosticLogger.Level.INFO
                )
                stateManager.transitionTo(VoiceState.PROCESSING, partialText = finalSpeech)
                onSpeechRecognized?.invoke(finalSpeech)
            },
            onError = { error ->
                logger.logDiagnostic(
                    category = "VoiceInput",
                    message = "Speech input error occurred: ${error.userMessage}",
                    level = DiagnosticLogger.Level.WARNING
                )
                stateManager.transitionTo(VoiceState.ERROR, error = error)
                onSpeechError?.invoke(error)
            },
            onRmsChanged = { rmsDb ->
                // Normalize typical Android RMS dB range (-2.0dB .. 10.0dB) to 0.0f .. 1.0f
                val normalizedRms = ((rmsDb + 2.0f) / 12.0f).coerceIn(0.0f, 1.0f)
                stateManager.updateRms(normalizedRms)
                onRmsChanged?.invoke(normalizedRms)
            }
        )
    }

    /**
     * Gracefully stops listening and begins processing pending speech audio.
     */
    fun stopListening() {
        logger.logDiagnostic(
            category = "VoiceInput",
            message = "stopListening invoked.",
            level = DiagnosticLogger.Level.DEBUG
        )
        recognitionEngine.stopListening()
        onListeningStopped?.invoke()
        if (stateManager.status.value.state == VoiceState.LISTENING) {
            stateManager.transitionTo(VoiceState.PROCESSING)
        }
    }

    /**
     * Immediately cancels the active audio recording session and resets state to IDLE.
     */
    fun cancel() {
        logger.logDiagnostic(
            category = "VoiceInput",
            message = "cancel invoked. Resetting voice input session.",
            level = DiagnosticLogger.Level.DEBUG
        )
        recognitionEngine.cancel()
        stateManager.resetToIdle()
        onListeningStopped?.invoke()
    }

    /**
     * Releases underlying speech recognition and audio resources.
     */
    fun destroy() {
        logger.logDiagnostic(
            category = "VoiceInput",
            message = "destroy invoked. Releasing recognizer resources.",
            level = DiagnosticLogger.Level.DEBUG
        )
        recognitionEngine.destroy()
        onSpeechRecognized = null
        onPartialResult = null
        onSpeechError = null
        onPermissionDenied = null
        onListeningStarted = null
        onListeningStopped = null
        onRmsChanged = null
    }
}

