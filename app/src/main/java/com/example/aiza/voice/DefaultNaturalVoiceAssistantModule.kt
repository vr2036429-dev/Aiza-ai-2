package com.example.aiza.voice

import com.example.aiza.core.AizaCore
import com.example.aiza.core.model.Language
import com.example.aiza.core.model.RequestSource
import com.example.aiza.core.model.UserRequest
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.modules.ModuleCategory
import com.example.aiza.modules.ModuleStatus
import com.example.aiza.modules.contracts.NaturalVoiceAssistantModule
import com.example.aiza.voice.input.VoiceInputManager
import com.example.aiza.voice.output.VoiceInfo
import com.example.aiza.voice.output.VoiceOutputManager
import com.example.aiza.voice.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Complete, modular Natural Voice Assistant system conforming to NaturalVoiceAssistantModule.
 * Coordinates Voice Input -> Speech Recognition -> Aiza Core -> AI Brain -> Response -> TTS -> Audio Output.
 */
class DefaultNaturalVoiceAssistantModule(
    val voiceInputManager: VoiceInputManager,
    val voiceOutputManager: VoiceOutputManager,
    val stateManager: VoiceStateManager,
    val wakeWordEngine: WakeWordEngine,
    val settingsRepository: VoiceSettingsRepository,
    private val aizaCoreProvider: () -> AizaCore,
    private val logger: DiagnosticLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) : NaturalVoiceAssistantModule {

    override val id: String = "mod_voice"
    override val name: String = "Natural Voice Assistant"
    override val description: String = "Natural bidirectional voice conversation engine for Asik."
    override val version: String = "2.0.0"
    override val category: ModuleCategory = ModuleCategory.VOICE_AND_AUDIO

    private var _status: ModuleStatus = ModuleStatus.UNINITIALIZED
    override val status: ModuleStatus get() = _status

    val voiceStatus: StateFlow<VoiceStatusSnapshot> get() = stateManager.status
    val voiceSettings: StateFlow<VoiceSettings> get() = settingsRepository.settings

    // Hooks for future background Aiza engine and 3D avatar module
    var onWakeWordDetected: (() -> Unit)? = null
    var onSpeechRecognized: ((String) -> Unit)? = null
    var onSpeechError: ((VoiceError) -> Unit)? = null
    var onTtsStarted: (() -> Unit)? = null
    var onTtsCompleted: (() -> Unit)? = null

    init {
        setupVoicePipeline()
    }

    private fun setupVoicePipeline() {
        // Wire voice input callbacks
        voiceInputManager.onSpeechRecognized = { recognizedText ->
            onSpeechRecognized?.invoke(recognizedText)
            handleRecognizedSpeech(recognizedText)
        }

        voiceInputManager.onSpeechError = { error ->
            onSpeechError?.invoke(error)
            logger.logDiagnostic(
                category = "VOICE_PIPELINE",
                message = "Voice input error: ${error.userMessage}",
                level = DiagnosticLogger.Level.WARNING
            )
        }

        // Wire voice output callbacks
        voiceOutputManager.onSpeechStarted = {
            onTtsStarted?.invoke()
        }

        voiceOutputManager.onSpeechCompleted = {
            onTtsCompleted?.invoke()
        }

        voiceOutputManager.onSpeechInterrupted = {
            logger.logDiagnostic(
                category = "VOICE_PIPELINE",
                message = "Speech playback interrupted immediately.",
                level = DiagnosticLogger.Level.INFO
            )
        }
    }

    override suspend fun initialize(): Boolean {
        return try {
            voiceOutputManager.initialize { ready ->
                _status = if (ready) ModuleStatus.ACTIVE else ModuleStatus.READY
            }

            // Sync settings with engines
            val currentSettings = settingsRepository.settings.value
            voiceOutputManager.setPitch(currentSettings.pitch)
            voiceOutputManager.setSpeechRate(currentSettings.speechRate)
            currentSettings.selectedVoiceName?.let { voiceOutputManager.setVoice(it) }
            wakeWordEngine.setEnabled(currentSettings.isWakeWordEnabled)

            // Setup wake word listener
            wakeWordEngine.startDetection(keyword = "Hey Aiza") {
                onWakeWordDetected?.invoke()
                scope.launch {
                    startListening()
                }
            }

            _status = ModuleStatus.ACTIVE
            logger.logDiagnostic(
                category = "VOICE_MODULE",
                message = "Natural Voice Assistant Module initialized and plugged into Aiza Core.",
                level = DiagnosticLogger.Level.INFO
            )
            true
        } catch (e: Exception) {
            _status = ModuleStatus.ERROR
            logger.logDiagnostic(
                category = "VOICE_MODULE",
                message = "Failed to initialize Natural Voice Assistant: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.ERROR
            )
            false
        }
    }

    override suspend fun startListening() {
        if (!settingsRepository.settings.value.isVoiceAssistantEnabled) {
            logger.logDiagnostic(
                category = "VOICE_PIPELINE",
                message = "Voice Assistant is toggled OFF in settings.",
                level = DiagnosticLogger.Level.WARNING
            )
            return
        }

        // Stop any active speech before listening
        if (stateManager.status.value.isSpeaking) {
            stopSpeaking()
        }

        val preferredLang = settingsRepository.settings.value.preferredLanguage
        val langCode = mapLanguageToBcp47(preferredLang)
        voiceInputManager.startListening(languageCode = langCode)
    }

    override suspend fun stopListening() {
        voiceInputManager.stopListening()
    }

    override suspend fun speak(text: String, languageCode: String) {
        val currentSettings = settingsRepository.settings.value
        voiceOutputManager.setPitch(currentSettings.pitch)
        voiceOutputManager.setSpeechRate(currentSettings.speechRate)
        voiceOutputManager.speak(text = text, languageCode = languageCode)
    }

    /**
     * Immediately interrupts Aiza while speaking.
     */
    fun stopSpeaking() {
        voiceOutputManager.stopSpeaking()
    }

    /**
     * Cancels active listening or speaking and returns to IDLE.
     */
    fun cancel() {
        voiceInputManager.cancel()
        voiceOutputManager.stopSpeaking()
        stateManager.resetToIdle()
    }

    private fun handleRecognizedSpeech(text: String) {
        scope.launch {
            try {
                val currentSettings = settingsRepository.settings.value
                val preferredLang = if (currentSettings.preferredLanguage != Language.UNKNOWN) {
                    currentSettings.preferredLanguage
                } else {
                    null
                }

                logger.logDiagnostic(
                    category = "VOICE_PIPELINE",
                    message = "Dispatching spoken input to Aiza Core: \"$text\"",
                    level = DiagnosticLogger.Level.INFO
                )

                // Dispatch to existing Aiza Core (No duplicate brain or orchestrator)
                val core = aizaCoreProvider()
                val userRequest = UserRequest(
                    text = text,
                    userName = "Asik",
                    explicitLanguage = preferredLang,
                    source = RequestSource.VOICE_INPUT
                )

                val response = core.dispatchUserRequest(userRequest)

                // Voice Output flow: Speak response if autoSpeak enabled
                if (currentSettings.autoSpeakResponses && response.text.isNotBlank()) {
                    val speechLangCode = mapLanguageToBcp47(response.language)
                    speak(text = response.text, languageCode = speechLangCode)
                } else {
                    stateManager.resetToIdle()
                }

            } catch (e: Exception) {
                stateManager.transitionTo(
                    VoiceState.ERROR,
                    error = VoiceError.Unknown("Processing failed: ${e.localizedMessage}")
                )
                logger.logDiagnostic(
                    category = "VOICE_PIPELINE",
                    message = "Error in voice pipeline: ${e.localizedMessage}",
                    level = DiagnosticLogger.Level.ERROR
                )
            }
        }
    }

    fun testVoice(sampleText: String? = null) {
        scope.launch {
            val settings = settingsRepository.settings.value
            val text = sampleText ?: when (settings.preferredLanguage) {
                Language.BENGALI -> "নমস্কার Asik Sir, আইজার ভয়েস সিস্টেম প্রস্তুত।"
                Language.HINDI -> "नमस्ते Asik Sir, आईज़ा की आवाज़ प्रणाली तैयार है।"
                Language.HINGLISH -> "Hello Asik Boss, Aiza ki voice settings perfectly set hain."
                else -> "Greetings Asik Sir. Aiza's voice assistant is fully operational."
            }
            speak(text, mapLanguageToBcp47(settings.preferredLanguage))
        }
    }

    fun getAvailableVoices(): List<VoiceInfo> = voiceOutputManager.getAvailableVoices()

    override suspend fun shutdown() {
        voiceInputManager.destroy()
        voiceOutputManager.shutdown()
        wakeWordEngine.stopDetection()
        _status = ModuleStatus.DISABLED
    }

    private fun mapLanguageToBcp47(language: Language): String {
        return when (language) {
            Language.BENGALI -> "bn-IN"
            Language.HINDI -> "hi-IN"
            Language.HINGLISH -> "hi-IN"
            Language.ENGLISH -> "en-US"
            Language.UNKNOWN -> "en-US"
        }
    }
}
