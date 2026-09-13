package com.example.aiza.voice.output

import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStateManager

/**
 * High-level manager coordinating text-to-speech output, natural voice formatting,
 * instant user interruption, and voice state transitions.
 *
 * Designed with a swappable TextToSpeechEngine interface allowing the underlying
 * synthesis provider to be replaced (e.g. Android native TTS, neural models, cloud engines).
 */
class VoiceOutputManager(
    initialEngine: TextToSpeechEngine,
    private val stateManager: VoiceStateManager,
    private val logger: DiagnosticLogger
) {
    /**
     * The active TextToSpeechEngine instance. Can be replaced dynamically at runtime.
     */
    var currentEngine: TextToSpeechEngine = initialEngine
        private set

    private var currentPitch: Float = 1.0f
    private var currentSpeechRate: Float = 1.0f
    private var currentVoiceName: String? = null

    // Interruption, completion, and word-range listeners
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null
    var onSpeechInterrupted: (() -> Unit)? = null

    /**
     * Real-time word range callback fired as each word is spoken.
     * Useful for word-by-word visualizers and 3D avatar lip-sync / visemes.
     */
    var onWordSpoken: ((utteranceId: String, word: String, startOffset: Int, endOffset: Int) -> Unit)? = null

    init {
        attachWordRangeListener(currentEngine)
    }

    val isReady: Boolean
        get() = currentEngine.isReady

    val isSpeaking: Boolean
        get() = currentEngine.isSpeaking || stateManager.status.value.isSpeaking

    /**
     * Swaps the active TextToSpeechEngine with another implementation (e.g. neural TTS, cloud TTS).
     * Automatically transfers current pitch, rate, and word listeners to the new engine.
     */
    fun setEngine(newEngine: TextToSpeechEngine, onReady: (Boolean) -> Unit = {}) {
        logger.logDiagnostic(
            category = "VOICE_OUTPUT",
            message = "Switching TTS engine from [${currentEngine.engineName}] to [${newEngine.engineName}]",
            level = DiagnosticLogger.Level.INFO
        )

        // Stop existing engine if speaking
        if (isSpeaking) {
            stopSpeaking()
        }

        currentEngine = newEngine
        attachWordRangeListener(newEngine)

        // Apply existing voice parameters
        newEngine.setPitch(currentPitch)
        newEngine.setSpeechRate(currentSpeechRate)
        currentVoiceName?.let { newEngine.setVoice(it) }

        if (!newEngine.isReady) {
            newEngine.initialize { ready ->
                logger.logDiagnostic(
                    category = "VOICE_OUTPUT",
                    message = "New TTS engine [${newEngine.engineName}] initialized: ready=$ready",
                    level = if (ready) DiagnosticLogger.Level.INFO else DiagnosticLogger.Level.WARNING
                )
                onReady(ready)
            }
        } else {
            onReady(true)
        }
    }

    private fun attachWordRangeListener(engine: TextToSpeechEngine) {
        engine.setOnWordRangeListener { utteranceId, start, end, word ->
            onWordSpoken?.invoke(utteranceId, word, start, end)
        }
    }

    fun initialize(onReady: (Boolean) -> Unit = {}) {
        currentEngine.initialize(onReady)
    }

    /**
     * Converts response text to speech, automatically formatting it for natural prosody,
     * stripping unpronounceable code/markdown symbols, and updating reactive voice states.
     */
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

        // Clean markdown, symbols, URLs, and code blocks for natural spoken audio
        val cleanSpokenText = sanitizeForNaturalSpeech(text)
        if (cleanSpokenText.isBlank()) {
            stateManager.resetToIdle()
            onDone()
            return
        }

        logger.logDiagnostic(
            category = "VOICE_OUTPUT",
            message = "Dispatching spoken audio: \"${cleanSpokenText.take(60)}...\"",
            level = DiagnosticLogger.Level.DEBUG
        )

        currentEngine.speak(
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
     * Interrupts speech immediately on user touch, wake word, or UI control.
     */
    fun stopSpeaking() {
        currentEngine.stop()
        stateManager.resetToIdle()
        onSpeechInterrupted?.invoke()
        logger.logDiagnostic(
            category = "VOICE_OUTPUT",
            message = "Voice output halted immediately by user interruption.",
            level = DiagnosticLogger.Level.INFO
        )
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        currentEngine.setSpeechRate(currentSpeechRate)
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        currentEngine.setPitch(currentPitch)
    }

    fun setVoice(voiceName: String): Boolean {
        currentVoiceName = voiceName
        return currentEngine.setVoice(voiceName)
    }

    fun getAvailableVoices(): List<VoiceInfo> {
        return currentEngine.getAvailableVoices()
    }

    fun shutdown() {
        currentEngine.shutdown()
    }

    /**
     * Preprocesses technical assistant responses into fluid, natural spoken language.
     */
    internal fun sanitizeForNaturalSpeech(rawText: String): String {
        return rawText
            // 1. Replace multi-line code blocks with brief spoken notification
            .replace(Regex("```(?:[a-zA-Z0-9_-]+)?\\n[\\s\\S]*?```"), " Here is the code block. ")
            // 2. Inline code snippets: strip backticks so identifiers are read cleanly
            .replace(Regex("`([^`]+)`"), "$1")
            // 3. Remove internal system / diagnostics / tool tags like [TOOL: ...] or [EXEC: ...]
            .replace(Regex("\\[[A-Z_]+(?::\\s*[^]]+)?\\]"), "")
            // 4. Convert URLs to a natural spoken phrase
            .replace(Regex("https?://\\S+"), "web link")
            // 5. Expand technical symbols to natural words
            .replace("&", " and ")
            .replace("%", " percent ")
            .replace("+", " plus ")
            .replace("°C", " degrees Celsius ")
            .replace("°F", " degrees Fahrenheit ")
            .replace("₹", " rupees ")
            .replace("$", " dollars ")
            .replace("€", " euros ")
            .replace("£", " pounds ")
            .replace("@", " at ")
            .replace("w/", " with ")
            .replace("vs.", " versus ")
            .replace("e.g.", " for example ")
            .replace("i.e.", " that is ")
            // 6. Strip markdown italics with underscores (preserving snake_case identifiers)
            .replace(Regex("(?<=\\s|^)_(.+?)_(?=\\s|$)"), "$1")
            // 7. Strip remaining markdown formatting symbols
            .replace(Regex("[*#~|><]"), "")
            // 8. Normalize redundant whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

