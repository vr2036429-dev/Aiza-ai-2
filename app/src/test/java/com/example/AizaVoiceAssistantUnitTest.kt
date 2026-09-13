package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiza.brain.AIProvider
import com.example.aiza.brain.AIProviderRegistry
import com.example.aiza.brain.AIRequest
import com.example.aiza.brain.AIResponse
import com.example.aiza.core.DefaultAizaCore
import com.example.aiza.core.model.Language
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.intent.DefaultIntentClassifier
import com.example.aiza.memory.DefaultMemoryStore
import com.example.aiza.modules.ModuleRegistry
import com.example.aiza.orchestrator.DefaultAizaOrchestrator
import com.example.aiza.response.DefaultResponseManager
import com.example.aiza.security.DefaultSecurityLayer
import com.example.aiza.tools.DefaultToolRouter
import com.example.aiza.voice.DefaultNaturalVoiceAssistantModule
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceSettingsRepository
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStateManager
import com.example.aiza.voice.input.SpeechRecognitionEngine
import com.example.aiza.voice.input.VoiceInputManager
import com.example.aiza.voice.output.TextToSpeechEngine
import com.example.aiza.voice.output.VoiceInfo
import com.example.aiza.voice.output.VoiceOutputManager
import com.example.aiza.voice.wakeword.WakeWordEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AizaVoiceAssistantUnitTest {

    private lateinit var context: Context
    private lateinit var logger: DiagnosticLogger
    private lateinit var voiceStateManager: VoiceStateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logger = DiagnosticLogger()
        voiceStateManager = VoiceStateManager(logger)
    }

    @Test
    fun testVoiceStateManagerStateTransitions() {
        assertEquals(VoiceState.IDLE, voiceStateManager.status.value.state)
        assertFalse(voiceStateManager.status.value.isListening)
        assertFalse(voiceStateManager.status.value.isSpeaking)

        // 1. Transition to Listening
        voiceStateManager.transitionTo(VoiceState.LISTENING)
        assertEquals(VoiceState.LISTENING, voiceStateManager.status.value.state)
        assertTrue(voiceStateManager.status.value.isListening)

        // 2. Partial Speech Update
        voiceStateManager.transitionTo(VoiceState.LISTENING, partialText = "What is the")
        assertEquals(VoiceState.LISTENING, voiceStateManager.status.value.state)
        assertEquals("What is the", voiceStateManager.status.value.partialText)

        // 3. Transition to Processing
        voiceStateManager.transitionTo(VoiceState.PROCESSING, partialText = "What is the time?")
        assertEquals(VoiceState.PROCESSING, voiceStateManager.status.value.state)
        assertEquals("What is the time?", voiceStateManager.status.value.partialText)

        // 4. Transition to Speaking
        voiceStateManager.transitionTo(VoiceState.SPEAKING)
        assertEquals(VoiceState.SPEAKING, voiceStateManager.status.value.state)
        assertTrue(voiceStateManager.status.value.isSpeaking)

        // 5. Transition to Stop Speaking (Reset)
        voiceStateManager.resetToIdle()
        assertEquals(VoiceState.IDLE, voiceStateManager.status.value.state)
        assertFalse(voiceStateManager.status.value.isSpeaking)

        // 6. Transition to Error
        voiceStateManager.transitionTo(VoiceState.ERROR, error = VoiceError.MicrophonePermissionDenied)
        assertEquals(VoiceState.ERROR, voiceStateManager.status.value.state)
        assertNotNull(voiceStateManager.status.value.error)
        assertTrue(voiceStateManager.status.value.error is VoiceError.MicrophonePermissionDenied)

        // 7. Reset back to IDLE
        voiceStateManager.resetToIdle()
        assertEquals(VoiceState.IDLE, voiceStateManager.status.value.state)
    }

    @Test
    fun testVoiceErrorMessages() {
        val permError = VoiceError.MicrophonePermissionDenied
        assertTrue(permError.userMessage.contains("Microphone permission"))

        val busyError = VoiceError.MicrophoneUnavailable
        assertTrue(busyError.userMessage.contains("Microphone hardware"))

        val netError = VoiceError.NetworkError
        assertTrue(netError.userMessage.contains("Network connection"))

        val noMatchError = VoiceError.NoSpeechDetected
        assertTrue(noMatchError.userMessage.contains("No speech was detected"))
    }

    @Test
    fun testFullVoicePipelineSpeechToCoreToTts() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val memory = DefaultMemoryStore()
        val security = DefaultSecurityLayer(logger)
        val router = DefaultToolRouter(security, logger)
        val responseManager = DefaultResponseManager()
        val dummyAiProvider = object : AIProvider {
            override val id: String = "test_provider"
            override val displayName: String = "Test Provider"
            override val isConfigured: Boolean = true
            override suspend fun generateResponse(request: AIRequest): Result<AIResponse> {
                return Result.success(
                    AIResponse(
                        content = "Battery level is optimal at 94 percent, Asik Sir.",
                        detectedLanguage = request.targetLanguage,
                        modelName = "TestBrain"
                    )
                )
            }
            override suspend fun ping(): Boolean = true
        }
        val aiRegistry = AIProviderRegistry(dummyAiProvider)
        val moduleRegistry = ModuleRegistry(logger)
        val classifier = DefaultIntentClassifier()
        val orchestrator = DefaultAizaOrchestrator(
            intentClassifier = classifier,
            toolRouter = router,
            securityLayer = security,
            aiProviderRegistry = aiRegistry,
            responseManager = responseManager,
            logger = logger
        )
        val core = DefaultAizaCore(
            orchestrator = orchestrator,
            toolRouter = router,
            securityLayer = security,
            memory = memory,
            moduleRegistry = moduleRegistry,
            aiProviderRegistry = aiRegistry,
            logger = logger
        )

        val testSpeechEngine = FakeSpeechRecognitionEngine()
        val testTtsEngine = FakeTextToSpeechEngine()
        val testWakeWordEngine = FakeWakeWordEngine()

        val voiceInputManager = VoiceInputManager(context, testSpeechEngine, voiceStateManager, logger)
        val voiceOutputManager = VoiceOutputManager(testTtsEngine, voiceStateManager, logger)
        val settingsRepo = VoiceSettingsRepository()

        val voiceModule = DefaultNaturalVoiceAssistantModule(
            voiceInputManager = voiceInputManager,
            voiceOutputManager = voiceOutputManager,
            stateManager = voiceStateManager,
            wakeWordEngine = testWakeWordEngine,
            settingsRepository = settingsRepo,
            aizaCoreProvider = { core },
            logger = logger,
            scope = testScope
        )

        voiceModule.initialize()
        advanceUntilIdle()
        assertEquals(VoiceState.IDLE, voiceStateManager.status.value.state)

        // Simulate speech recognition returning final text
        voiceInputManager.onSpeechRecognized?.invoke("Hello Aiza")
        advanceUntilIdle()

        // Verify TTS engine received speech synthesis call
        assertTrue("Expected TTS engine to speak, received: ${testTtsEngine.spokenPhrases.size}", testTtsEngine.spokenPhrases.isNotEmpty())
        val spokenText = testTtsEngine.spokenPhrases.last()
        assertTrue("Spoken response should not be blank", spokenText.isNotBlank())
        assertTrue("Response should contain Sir or Asik, got: $spokenText", spokenText.contains("Sir") || spokenText.contains("Asik"))

        // Simulate Asik interrupting Aiza speaking
        voiceModule.stopSpeaking()
        advanceUntilIdle()
        assertEquals(VoiceState.IDLE, voiceStateManager.status.value.state)
        assertFalse(testTtsEngine.isSpeaking)
    }

    @Test
    fun testWakeWordTriggersSpeechListening() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val memory = DefaultMemoryStore()
        val security = DefaultSecurityLayer(logger)
        val router = DefaultToolRouter(security, logger)
        val responseManager = DefaultResponseManager()
        val dummyAiProvider = object : AIProvider {
            override val id: String = "test_provider"
            override val displayName: String = "Test Provider"
            override val isConfigured: Boolean = true
            override suspend fun generateResponse(request: AIRequest): Result<AIResponse> {
                return Result.success(
                    AIResponse(content = "Yes Asik?", detectedLanguage = request.targetLanguage, modelName = "TestBrain")
                )
            }
            override suspend fun ping(): Boolean = true
        }
        val aiRegistry = AIProviderRegistry(dummyAiProvider)
        val moduleRegistry = ModuleRegistry(logger)
        val classifier = DefaultIntentClassifier()
        val orchestrator = DefaultAizaOrchestrator(
            intentClassifier = classifier,
            toolRouter = router,
            securityLayer = security,
            aiProviderRegistry = aiRegistry,
            responseManager = responseManager,
            logger = logger
        )
        val core = DefaultAizaCore(
            orchestrator = orchestrator,
            toolRouter = router,
            securityLayer = security,
            memory = memory,
            moduleRegistry = moduleRegistry,
            aiProviderRegistry = aiRegistry,
            logger = logger
        )

        val testSpeechEngine = FakeSpeechRecognitionEngine()
        val testTtsEngine = FakeTextToSpeechEngine()
        val testWakeWordEngine = FakeWakeWordEngine()

        val voiceInputManager = VoiceInputManager(context, testSpeechEngine, voiceStateManager, logger)
        val voiceOutputManager = VoiceOutputManager(testTtsEngine, voiceStateManager, logger)
        val settingsRepo = VoiceSettingsRepository()

        val voiceModule = DefaultNaturalVoiceAssistantModule(
            voiceInputManager = voiceInputManager,
            voiceOutputManager = voiceOutputManager,
            stateManager = voiceStateManager,
            wakeWordEngine = testWakeWordEngine,
            settingsRepository = settingsRepo,
            aizaCoreProvider = { core },
            logger = logger,
            scope = testScope
        )

        var wakeWordFired = false
        voiceModule.onWakeWordDetected = {
            wakeWordFired = true
        }

        voiceModule.initialize()
        advanceUntilIdle()

        // Enable wake word
        settingsRepo.setWakeWordEnabled(true)
        testWakeWordEngine.setEnabled(true)
        advanceUntilIdle()

        // Simulate acoustic detection of wake word
        testWakeWordEngine.simulateWakeWordDetected()
        advanceUntilIdle()

        // Wake word callback must have fired
        assertTrue(wakeWordFired)
    }

    @Test
    fun testVoiceSettingsUpdates() {
        val settingsRepo = VoiceSettingsRepository()

        settingsRepo.updateSettings {
            it.copy(
                speechRate = 1.25f,
                pitch = 1.10f,
                preferredLanguage = Language.HINDI
            )
        }

        assertEquals(1.25f, settingsRepo.settings.value.speechRate)
        assertEquals(1.10f, settingsRepo.settings.value.pitch)
        assertEquals(Language.HINDI, settingsRepo.settings.value.preferredLanguage)
    }
}

// ---- Test Fakes for Unit Testing ----

private class FakeSpeechRecognitionEngine : SpeechRecognitionEngine {
    var isListeningActive: Boolean = false
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var onErrorCallback: ((VoiceError) -> Unit)? = null

    override fun isRecognitionAvailable(): Boolean = true

    override fun startListening(
        languageCode: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (VoiceError) -> Unit,
        onRmsChanged: (Float) -> Unit
    ) {
        isListeningActive = true
        onPartial = onPartialResult
        onFinal = onFinalResult
        onErrorCallback = onError
    }

    override fun stopListening() {
        isListeningActive = false
    }

    override fun cancel() {
        isListeningActive = false
    }

    override fun destroy() {
        isListeningActive = false
    }

    fun simulatePartialText(text: String) {
        onPartial?.invoke(text)
    }

    fun simulateFinalResult(text: String) {
        isListeningActive = false
        onFinal?.invoke(text)
    }

    fun simulateError(error: VoiceError) {
        isListeningActive = false
        onErrorCallback?.invoke(error)
    }
}

private class FakeTextToSpeechEngine : TextToSpeechEngine {
    override var isReady: Boolean = true
    override var isSpeaking: Boolean = false
    val spokenPhrases = mutableListOf<String>()

    override fun initialize(onReady: (Boolean) -> Unit) {
        isReady = true
        onReady(true)
    }

    override fun speak(
        text: String,
        languageCode: String,
        utteranceId: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        isSpeaking = true
        spokenPhrases.add(text)
        onStart()
        // Simulate speech completing in test
        isSpeaking = false
        onDone()
    }

    override fun stop() {
        isSpeaking = false
    }

    override fun pause() {
        isSpeaking = false
    }

    override fun resume() {
        isSpeaking = true
    }

    override fun setPitch(pitch: Float) {}
    override fun setSpeechRate(rate: Float) {}
    override fun getAvailableVoices(): List<VoiceInfo> = listOf(VoiceInfo("en-us-x-sfg", "English (United States)"))
    override fun setVoice(voiceName: String): Boolean = true
    override fun shutdown() { isSpeaking = false }
}

private class FakeWakeWordEngine : WakeWordEngine {
    override val id: String = "fake_wake_word"
    override val name: String = "Fake Wake Word Engine"
    override val isContinuousDetectionSupported: Boolean = true
    private var _isEnabled = false
    override val isEnabled: Boolean get() = _isEnabled

    private var detectionCallback: (() -> Unit)? = null

    override fun setEnabled(enabled: Boolean) {
        _isEnabled = enabled
    }

    override fun startDetection(keyword: String, onWakeWordDetected: () -> Unit) {
        detectionCallback = onWakeWordDetected
    }

    override fun stopDetection() {
        detectionCallback = null
    }

    fun simulateWakeWordDetected() {
        detectionCallback?.invoke()
    }
}
