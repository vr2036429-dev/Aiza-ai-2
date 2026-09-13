package com.example.aiza.voice.output

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    val isNetworkConnectionRequired: Boolean = false,
    val quality: String = "Normal"
)

/**
 * Modular Text-to-Speech interface allowing the voice engine to be replaced later
 * (e.g. Android native TTS, cloud neural TTS, or local ONNX engines).
 */
interface TextToSpeechEngine {
    val engineId: String get() = "native_android_tts"
    val engineName: String get() = "Android Native Text-to-Speech"
    val isReady: Boolean
    val isSpeaking: Boolean get() = false

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
    fun setOnWordRangeListener(listener: ((utteranceId: String, start: Int, end: Int, word: String) -> Unit)?) {}
    fun shutdown()
}

/**
 * Default Android native implementation of TextToSpeechEngine using the Android TextToSpeech API.
 */
class AndroidTextToSpeechEngine(
    private val context: Context,
    private val logger: DiagnosticLogger
) : TextToSpeechEngine {

    override val engineId: String = "android_tts"
    override val engineName: String = "Android Text-to-Speech"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var _isSpeaking = false
    override val isSpeaking: Boolean get() = _isSpeaking

    private var currentPitch: Float = 1.0f
    private var currentRate: Float = 1.0f

    private val callbackMap = mutableMapOf<String, Triple<() -> Unit, () -> Unit, (String) -> Unit>>()
    private val textMap = mutableMapOf<String, String>()
    private var wordRangeListener: ((utteranceId: String, start: Int, end: Int, word: String) -> Unit)? = null

    override val isReady: Boolean
        get() = isInitialized && tts != null

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    override fun initialize(onReady: (Boolean) -> Unit) {
        if (isInitialized && tts != null) {
            onReady(true)
            return
        }

        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                runOnMain {
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        setupUtteranceListener()
                        applyCurrentSettings()
                        logger.logDiagnostic(
                            category = "TTS",
                            message = "Android TextToSpeech engine initialized successfully.",
                            level = DiagnosticLogger.Level.INFO
                        )
                        onReady(true)
                    } else {
                        isInitialized = false
                        logger.logDiagnostic(
                            category = "TTS",
                            message = "Android TextToSpeech initialization failed with status: $status",
                            level = DiagnosticLogger.Level.ERROR
                        )
                        onReady(false)
                    }
                }
            }
        } catch (e: Exception) {
            logger.logDiagnostic(
                category = "TTS",
                message = "Exception while initializing TextToSpeech: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.ERROR
            )
            onReady(false)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking = true
                utteranceId?.let { id ->
                    runOnMain {
                        callbackMap[id]?.first?.invoke()
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking = false
                utteranceId?.let { id ->
                    runOnMain {
                        textMap.remove(id)
                        callbackMap.remove(id)?.second?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking = false
                utteranceId?.let { id ->
                    runOnMain {
                        textMap.remove(id)
                        callbackMap.remove(id)?.third?.invoke("TTS playback error")
                    }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking = false
                utteranceId?.let { id ->
                    val errorDesc = when (errorCode) {
                        TextToSpeech.ERROR_NETWORK -> "Network connection error in TTS engine"
                        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout in TTS engine"
                        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice data not yet installed on device"
                        TextToSpeech.ERROR_SYNTHESIS -> "Speech synthesis engine failure"
                        else -> "Speech output error (Code $errorCode)"
                    }
                    runOnMain {
                        textMap.remove(id)
                        callbackMap.remove(id)?.third?.invoke(errorDesc)
                    }
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != null && wordRangeListener != null) {
                    val fullText = textMap[utteranceId]
                    val word = if (fullText != null && start >= 0 && end <= fullText.length && start < end) {
                        fullText.substring(start, end)
                    } else {
                        ""
                    }
                    runOnMain {
                        wordRangeListener?.invoke(utteranceId, start, end, word)
                    }
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
        textMap[utteranceId] = text

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (speakResult != TextToSpeech.SUCCESS) {
            _isSpeaking = false
            callbackMap.remove(utteranceId)
            textMap.remove(utteranceId)
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
            _isSpeaking = false
            callbackMap.clear()
            textMap.clear()
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
        stop()
    }

    override fun resume() {
        // Native Android TTS does not offer resume
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
                    isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
                    quality = if (voice.quality >= Voice.QUALITY_HIGH) "High" else "Normal"
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

    override fun setOnWordRangeListener(listener: ((utteranceId: String, start: Int, end: Int, word: String) -> Unit)?) {
        this.wordRangeListener = listener
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking = false
            callbackMap.clear()
            textMap.clear()
        } catch (e: Exception) {
            // Safe cleanup
        }
    }

    /**
     * Resolves language codes to Locales cleanly using BCP-47 tags or Builder.
     */
    internal fun mapLanguageCodeToLocale(languageCode: String): Locale {
        val cleanCode = languageCode.lowercase().trim()
        return when {
            cleanCode == "bn" || cleanCode == "ben" || cleanCode == "bengali" ->
                Locale.Builder().setLanguage("bn").setRegion("IN").build()
            cleanCode == "hi" || cleanCode == "hin" || cleanCode == "hindi" ->
                Locale.Builder().setLanguage("hi").setRegion("IN").build()
            cleanCode == "en" || cleanCode == "eng" || cleanCode == "english" ->
                Locale.US
            cleanCode == "hinglish" ->
                Locale.Builder().setLanguage("hi").setRegion("IN").build()
            cleanCode.contains("-") ->
                Locale.forLanguageTag(languageCode)
            else ->
                Locale.Builder().setLanguage(cleanCode).build()
        }
    }
}

