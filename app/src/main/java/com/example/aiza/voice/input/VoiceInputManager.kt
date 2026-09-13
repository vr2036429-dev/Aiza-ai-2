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
 * Manages voice capture lifecycle, permission verifications, partial transcript updates,
 * and delivery of final speech text to Aiza Core.
 */
class VoiceInputManager(
    private val context: Context,
    private val recognitionEngine: SpeechRecognitionEngine,
    private val stateManager: VoiceStateManager,
    private val logger: DiagnosticLogger
) {
    var onSpeechRecognized: ((String) -> Unit)? = null
    var onSpeechError: ((VoiceError) -> Unit)? = null

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening(languageCode: String = "en-US") {
        if (!hasMicrophonePermission()) {
            val err = VoiceError.MicrophonePermissionDenied
            stateManager.transitionTo(VoiceState.ERROR, error = err)
            onSpeechError?.invoke(err)
            return
        }

        if (!recognitionEngine.isRecognitionAvailable()) {
            val err = VoiceError.SpeechRecognitionUnavailable
            stateManager.transitionTo(VoiceState.ERROR, error = err)
            onSpeechError?.invoke(err)
            return
        }

        stateManager.transitionTo(VoiceState.LISTENING)

        recognitionEngine.startListening(
            languageCode = languageCode,
            onPartialResult = { partial ->
                stateManager.transitionTo(VoiceState.LISTENING, partialText = partial)
            },
            onFinalResult = { finalSpeech ->
                stateManager.transitionTo(VoiceState.PROCESSING, partialText = finalSpeech)
                onSpeechRecognized?.invoke(finalSpeech)
            },
            onError = { error ->
                stateManager.transitionTo(VoiceState.ERROR, error = error)
                onSpeechError?.invoke(error)
            },
            onRmsChanged = { rms ->
                stateManager.updateRms(rms)
            }
        )
    }

    fun stopListening() {
        recognitionEngine.stopListening()
        if (stateManager.status.value.state == VoiceState.LISTENING) {
            stateManager.transitionTo(VoiceState.PROCESSING)
        }
    }

    fun cancel() {
        recognitionEngine.cancel()
        stateManager.resetToIdle()
    }

    fun destroy() {
        recognitionEngine.destroy()
    }
}
