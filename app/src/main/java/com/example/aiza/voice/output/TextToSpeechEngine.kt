package com.example.aiza.voice.output

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.aiza.diagnostics.DiagnosticLogger
import java.util.Locale
import java.util.UUID

/**
 * Metadata for a selectable TTS voice.
 */
data class VoiceInfo(
    val name: String,
    val localeDisplayName: String,
    val isNetworkConnectionRequired: Boolean = false
)

/**
 * Modular Text-to-Speech interface allowing the voice engine to be replaced later.
 */
interface TextToSpeechEngine {
    val isReady: Boolean

    fun initialize(onReady: (Boolean) -> Unit)
    fun speak(
        text: String,
        languageCode: String,
        utteranceId: String = UUID.randomUUID().toString(),
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    fun stop()
    fun pause()
    fun resume()
    fun setPitch(pitch: Float)
    fun setSpeechRate(rate: Float)
    fun getAvailableVoices(): List<VoiceInfo>
    fun setVoice(voiceName: String): Boolean
    fun shutdown()
}

/**
 * Default Android native implementation of TextToSpeechEngine.
 */
class AndroidTextToSpeechEngine(
    private val context: Context,
    private val logger: DiagnosticLogger
) : TextToSpeechEngine {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    private var currentPitch: Float = 1.0f
    private var currentRate: Float = 1.0f

    private val callbackMap = mutableMapOf<String, Triple<() -> Unit, () -> Unit, (String) -> Unit>>()

    override val isReady: Boolean
        get() = isInitialized && tts != null

    override fun initialize(onReady: (Boolean) -> Unit) {
        if (isInitialized && tts != null) {
            onReady(true)
            return
        }

        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    setupUtteranceListener()
                    applyCurrentSettings()
                    logger.logDiagnostic(
                        category = "TTS",
                        message = "Android TTS Engine initialized successfully.",
                        level = DiagnosticLogger.Level.INFO
                    )
                    onReady(true)
                } else {
                    isInitialized = false
                    logger.logDiagnostic(
                        category = "TTS",
                        message = "Android TTS Engine initialization failed with status: $status",
                        level = DiagnosticLogger.Level.ERROR
                    )
                    onReady(false)
                }
            }
        } catch (e: Exception) {
            logger.logDiagnostic(
                category = "TTS",
                message = "Exception while initializing TTS: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.ERROR
            )
            onReady(false)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { id ->
                    callbackMap[id]?.first?.invoke()
                }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { id ->
                    callbackMap.remove(id)?.second?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { id ->
                    callbackMap.remove(id)?.third?.invoke("TTS playback error")
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { id ->
                    val errorDesc = when (errorCode) {
                        TextToSpeech.ERROR_NETWORK -> "Network connection error in TTS engine"
                        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout in TTS engine"
                        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice data not yet installed on device"
                        TextToSpeech.ERROR_SYNTHESIS -> "Speech synthesis engine failure"
                        else -> "Speech output error (Code $errorCode)"
                    }
                    callbackMap.remove(id)?.third?.invoke(errorDesc)
                }
            }
        })
    }

    private fun applyCurrentSettings() {
        tts?.setPitch(currentPitch)
        tts?.setSpeechRate(currentRate)
    }

    override fun speak(
        text: String,
        languageCode: String,
        utteranceId: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isInitialized || tts == null) {
            initialize { success ->
                if (success) {
                    speak(text, languageCode, utteranceId, onStart, onDone, onError)
                } else {
                    onError("Text-to-speech engine is unavailable.")
                }
            }
            return
        }

        val targetLocale = mapLanguageCodeToLocale(languageCode)
        val result = tts?.setLanguage(targetLocale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            logger.logDiagnostic(
                category = "TTS",
                message = "Language '$languageCode' (${targetLocale.displayName}) is not supported or requires language pack download. Falling back to default locale.",
                level = DiagnosticLogger.Level.WARNING
            )
            tts?.setLanguage(Locale.getDefault())
        }

        applyCurrentSettings()
        callbackMap[utteranceId] = Triple(onStart, onDone, onError)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (speakResult != TextToSpeech.SUCCESS) {
            callbackMap.remove(utteranceId)
            onError("Failed to dispatch utterance to TTS engine.")
        } else {
            logger.logDiagnostic(
                category = "TTS",
                message = "Speaking utterance [$utteranceId] in ${targetLocale.language}: \"${text.take(40)}...\"",
                level = DiagnosticLogger.Level.DEBUG
            )
        }
    }

    override fun stop() {
        try {
            tts?.stop()
            callbackMap.clear()
            logger.logDiagnostic(
                category = "TTS",
                message = "TTS speech halted immediately by user interruption.",
                level = DiagnosticLogger.Level.DEBUG
            )
        } catch (e: Exception) {
            logger.logDiagnostic(
                category = "TTS",
                message = "Exception stopping TTS: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.WARNING
            )
        }
    }

    override fun pause() {
        // Android TTS does not offer an OS-level pause; stopping serves as interrupt
        stop()
    }

    override fun resume() {
        // Resume not supported natively without storing segment offsets
    }

    override fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentPitch)
    }

    override fun setSpeechRate(rate: Float) {
        currentRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentRate)
    }

    override fun getAvailableVoices(): List<VoiceInfo> {
        return try {
            tts?.voices?.map { voice ->
                VoiceInfo(
                    name = voice.name,
                    localeDisplayName = "${voice.locale.displayName} (${voice.locale.language})",
                    isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun setVoice(voiceName: String): Boolean {
        return try {
            val matchingVoice = tts?.voices?.find { it.name == voiceName }
            if (matchingVoice != null) {
                tts?.voice = matchingVoice
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            // Safe cleanup
        }
    }

    private fun mapLanguageCodeToLocale(languageCode: String): Locale {
        return when (languageCode.lowercase().trim()) {
            "bn", "ben", "bengali" -> Locale("bn", "IN")
            "hi", "hin", "hindi" -> Locale("hi", "IN")
            "en", "eng", "english" -> Locale.US
            "hinglish" -> Locale("hi", "IN") // Hinglish uses Hindi/English phonetics
            else -> Locale.getDefault()
        }
    }
}
