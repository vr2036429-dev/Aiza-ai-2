package com.example.aiza.voice.input

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    val isListening: Boolean get() = false

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
 *
 * All interactions with Android's SpeechRecognizer are safely dispatched on the main looper
 * to prevent threading issues.
 */
class AndroidSpeechRecognitionEngine(
    private val context: Context,
    private val logger: DiagnosticLogger
) : SpeechRecognitionEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var _isListening = false
    override val isListening: Boolean get() = _isListening

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

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
        runOnMainThread {
            if (!isRecognitionAvailable()) {
                logger.logDiagnostic(
                    category = "STT",
                    message = "Speech recognition service is not available on this device.",
                    level = DiagnosticLogger.Level.WARNING
                )
                onError(VoiceError.SpeechRecognitionUnavailable)
                return@runOnMainThread
            }

            try {
                // Cancel and release any previous session before starting a new one
                cleanupRecognizer()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening = true
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Microphone open and ready for speech input ($languageCode).",
                            level = DiagnosticLogger.Level.DEBUG
                        )
                    }

                    override fun onBeginningOfSpeech() {
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Voice activity detected by recognizer.",
                            level = DiagnosticLogger.Level.DEBUG
                        )
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Raw audio buffer received
                    }

                    override fun onEndOfSpeech() {
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Speech input stream ended. Processing audio hypotheses...",
                            level = DiagnosticLogger.Level.DEBUG
                        )
                    }

                    override fun onError(errorCode: Int) {
                        _isListening = false
                        val voiceError = mapSpeechErrorCode(errorCode)
                        logger.logDiagnostic(
                            category = "STT",
                            message = "Speech Recognition Error (code $errorCode): ${voiceError.userMessage}",
                            level = DiagnosticLogger.Level.WARNING
                        )
                        onError(voiceError)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestMatch = matches?.firstOrNull()?.trim() ?: ""

                        logger.logDiagnostic(
                            category = "STT",
                            message = "Final speech recognized: \"$bestMatch\"",
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

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Reserved for custom events from recognizer service
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.startListening(intent)

            } catch (e: Exception) {
                _isListening = false
                logger.logDiagnostic(
                    category = "STT",
                    message = "Failed to launch Android SpeechRecognizer: ${e.localizedMessage}",
                    level = DiagnosticLogger.Level.ERROR
                )
                onError(VoiceError.Unknown(e.localizedMessage ?: "Failed to start speech recognition"))
            }
        }
    }

    override fun stopListening() {
        runOnMainThread {
            try {
                if (_isListening) {
                    speechRecognizer?.stopListening()
                    _isListening = false
                    logger.logDiagnostic(
                        category = "STT",
                        message = "Speech recognition stopped listening.",
                        level = DiagnosticLogger.Level.DEBUG
                    )
                }
            } catch (e: Exception) {
                logger.logDiagnostic(
                    category = "STT",
                    message = "Error during stopListening: ${e.localizedMessage}",
                    level = DiagnosticLogger.Level.WARNING
                )
            }
        }
    }

    override fun cancel() {
        runOnMainThread {
            try {
                speechRecognizer?.cancel()
                _isListening = false
                logger.logDiagnostic(
                    category = "STT",
                    message = "Speech recognition session cancelled.",
                    level = DiagnosticLogger.Level.DEBUG
                )
            } catch (e: Exception) {
                logger.logDiagnostic(
                    category = "STT",
                    message = "Error during cancel: ${e.localizedMessage}",
                    level = DiagnosticLogger.Level.WARNING
                )
            }
        }
    }

    override fun destroy() {
        runOnMainThread {
            cleanupRecognizer()
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore during teardown
        } finally {
            speechRecognizer = null
            _isListening = false
        }
    }

    /**
     * Maps Android SpeechRecognizer error codes to Aiza VoiceError types.
     */
    internal fun mapSpeechErrorCode(errorCode: Int): VoiceError {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> VoiceError.MicrophoneUnavailable
            SpeechRecognizer.ERROR_CLIENT -> VoiceError.Unknown("Speech recognition client error ($errorCode)")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.MicrophonePermissionDenied
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NetworkError
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NoSpeechDetected
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.MicrophoneUnavailable
            SpeechRecognizer.ERROR_SERVER -> VoiceError.NetworkError
            // Android 12+ (API 31) error constants
            10 -> VoiceError.LanguageNotSupported // ERROR_CANNOT_CHECK_SUPPORT
            11 -> VoiceError.NetworkError // ERROR_SERVER_DISCONNECTED
            12 -> VoiceError.NetworkError // ERROR_TOO_MANY_REQUESTS
            // Android 14+ (API 34)
            13 -> VoiceError.LanguageNotSupported // ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
            14 -> VoiceError.LanguageNotSupported // ERROR_LANGUAGE_NOT_SUPPORTED
            15 -> VoiceError.LanguageNotSupported // ERROR_LANGUAGE_UNAVAILABLE
            else -> VoiceError.Unknown("Speech recognition error (code $errorCode)")
        }
    }
}

