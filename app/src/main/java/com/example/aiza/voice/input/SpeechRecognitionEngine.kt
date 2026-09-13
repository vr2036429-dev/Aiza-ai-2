package com.example.aiza.voice.input

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.voice.VoiceError
import java.util.Locale

/**
 * Interface abstracting speech recognition engine implementations.
 */
interface SpeechRecognitionEngine {
    fun isRecognitionAvailable(): Boolean
    fun startListening(
        languageCode: String = "en-US",
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit = {},
        onError: (VoiceError) -> Unit = {},
        onRmsChanged: (Float) -> Unit = {}
    )
    fun stopListening()
    fun cancel()
    fun destroy()
}

/**
 * Native Android SpeechRecognizer implementation supporting streaming partial results,
 * RMS sound level changes, and comprehensive error mapping.
 */
class AndroidSpeechRecognitionEngine(
    private val context: Context,
    private val logger: DiagnosticLogger
) : SpeechRecognitionEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening(
        languageCode: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (VoiceError) -> Unit,
        onRmsChanged: (Float) -> Unit
    ) {
        if (!isRecognitionAvailable()) {
            onError(VoiceError.SpeechRecognitionUnavailable)
            return
        }

        try {
            cancel() // Clean up any existing session

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Microphone open and ready for Asik's speech ($languageCode).",
                            level = DiagnosticLogger.Level.DEBUG
                        )
                    }

                    override fun onBeginningOfSpeech() {
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Voice activity detected.",
                            level = DiagnosticLogger.Level.DEBUG
                        )
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Speech stream ended. Processing audio...",
                            level = DiagnosticLogger.Level.DEBUG
                        )
                    }

                    override fun onError(errorCode: Int) {
                        isListening = false
                        val voiceError = mapSpeechErrorCode(errorCode)
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Speech Recognition Error: ${voiceError.userMessage} (Code $errorCode)",
                            level = DiagnosticLogger.Level.WARNING
                        )
                        onError(voiceError)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestMatch = matches?.firstOrNull()?.trim() ?: ""

                        logger.logDiagnostic(
                            category = "STT",
                            message = "Final Speech Recognized: \"$bestMatch\"",
                            level = DiagnosticLogger.Level.INFO
                        )

                        if (bestMatch.isNotBlank()) {
                            onFinalResult(bestMatch)
                        } else {
                            onError(VoiceError.NoSpeechDetected)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialMatch = matches?.firstOrNull()?.trim() ?: ""
                        if (partialMatch.isNotBlank()) {
                            onPartialResult(partialMatch)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            isListening = false
            logger.logDiagnostic(
                category = "STT",
                message = "Failed to launch speech recognition: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.ERROR
            )
            onError(VoiceError.Unknown(e.localizedMessage ?: "Failed to start speech recognition"))
        }
    }

    override fun stopListening() {
        try {
            if (isListening) {
                speechRecognizer?.stopListening()
                isListening = false
            }
        } catch (e: Exception) {
            // Safe cleanup
        }
    }

    override fun cancel() {
        try {
            speechRecognizer?.cancel()
            isListening = false
        } catch (e: Exception) {
            // Safe cleanup
        }
    }

    override fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
        } catch (e: Exception) {
            // Safe cleanup
        }
    }

    private fun mapSpeechErrorCode(errorCode: Int): VoiceError {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> VoiceError.MicrophoneUnavailable
            SpeechRecognizer.ERROR_CLIENT -> VoiceError.Unknown("Client side error occurred")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.MicrophonePermissionDenied
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NetworkError
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NoSpeechDetected
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.MicrophoneUnavailable
            SpeechRecognizer.ERROR_SERVER -> VoiceError.NetworkError
            else -> VoiceError.Unknown("Recognition error (code $errorCode)")
        }
    }
}
