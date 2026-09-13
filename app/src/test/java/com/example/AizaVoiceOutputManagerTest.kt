package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStateManager
import com.example.aiza.voice.output.AndroidTextToSpeechEngine
import com.example.aiza.voice.output.TextToSpeechEngine
import com.example.aiza.voice.output.VoiceInfo
import com.example.aiza.voice.output.VoiceOutputManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AizaVoiceOutputManagerTest {

    private lateinit var context: Context
    private lateinit var logger: DiagnosticLogger
    private lateinit var stateManager: VoiceStateManager
    private lateinit var fakeEngine: TestTtsEngine
    private lateinit var outputManager: VoiceOutputManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logger = DiagnosticLogger()
        stateManager = VoiceStateManager(logger)
        fakeEngine = TestTtsEngine(engineId = "fake_primary", engineName = "Primary Test Engine")
        outputManager = VoiceOutputManager(fakeEngine, stateManager, logger)
    }

    @Test
    fun testInitialization() {
        var initResult = false
        outputManager.initialize { ready ->
            initResult = ready
        }
        assertTrue("Engine should initialize to ready", initResult)
        assertTrue(outputManager.isReady)
    }

    @Test
    fun testNaturalSpeechSanitization() {
        val rawMarkdown = """
            Hello Asik! **System check** is 100% complete & optimal w/ 25°C temperature.
            Visit https://aiza.ai for updates.
            [TOOL: get_weather]
            ```kotlin
            fun main() { println("test") }
            ```
            Use `status_code` to verify.
        """.trimIndent()

        val sanitized = outputManager.sanitizeForNaturalSpeech(rawMarkdown)

        assertFalse("Should not contain markdown bold", sanitized.contains("**"))
        assertFalse("Should not contain backticks", sanitized.contains("`"))
        assertFalse("Should not contain raw URL", sanitized.contains("https://"))
        assertFalse("Should not contain tool tags", sanitized.contains("[TOOL"))
        assertTrue("Should expand & to 'and'", sanitized.contains(" and "))
        assertTrue("Should expand % to 'percent'", sanitized.contains(" percent "))
        assertTrue("Should expand °C to 'degrees Celsius'", sanitized.contains("degrees Celsius"))
        assertTrue("Should expand w/ to 'with'", sanitized.contains(" with "))
        assertTrue("Should convert URL to 'web link'", sanitized.contains("web link"))
        assertTrue("Should handle code block politely", sanitized.contains("Here is the code block."))
        assertTrue("Should preserve inline identifier", sanitized.contains("status_code"))
    }

    @Test
    fun testSpeakCompletesAndTransitionsStates() {
        var speechStartedFired = false
        var speechCompletedFired = false
        var onDoneFired = false

        outputManager.onSpeechStarted = { speechStartedFired = true }
        outputManager.onSpeechCompleted = { speechCompletedFired = true }

        outputManager.speak(
            text = "Welcome back Asik.",
            languageCode = "en-US",
            onDone = { onDoneFired = true }
        )

        // TestTtsEngine auto-simulates onStart() then onDone() synchronously in speak()
        assertTrue("onSpeechStarted callback should be triggered", speechStartedFired)
        assertTrue("onSpeechCompleted callback should be triggered", speechCompletedFired)
        assertTrue("onDone lambda should be triggered", onDoneFired)
        assertEquals("Should return to IDLE state after speaking", VoiceState.IDLE, stateManager.status.value.state)
        assertEquals(1, fakeEngine.spokenUtterances.size)
        assertEquals("Welcome back Asik.", fakeEngine.spokenUtterances.first())
    }

    @Test
    fun testStopSpeakingInterruptsPlayback() {
        var speechInterruptedFired = false
        outputManager.onSpeechInterrupted = { speechInterruptedFired = true }

        // Start manual speaking mode in fake engine
        fakeEngine.autoComplete = false
        outputManager.speak("Long running response from Aiza")

        assertEquals(VoiceState.SPEAKING, stateManager.status.value.state)
        assertTrue(outputManager.isSpeaking)

        outputManager.stopSpeaking()

        assertTrue("onSpeechInterrupted should be called", speechInterruptedFired)
        assertEquals(VoiceState.IDLE, stateManager.status.value.state)
        assertFalse(fakeEngine.isSpeaking)
        assertFalse(outputManager.isSpeaking)
    }

    @Test
    fun testSpeechErrorTransitionsState() {
        fakeEngine.simulateError = "Audio output device disconnected"

        outputManager.speak("Testing speech error handling")

        assertEquals(VoiceState.ERROR, stateManager.status.value.state)
        val error = stateManager.status.value.error
        assertNotNull(error)
        assertTrue(error is VoiceError.TextToSpeechError)
        assertEquals("Audio output device disconnected", (error as VoiceError.TextToSpeechError).reason)
    }

    @Test
    fun testSwappableEngineArchitecture() {
        val secondaryEngine = TestTtsEngine(engineId = "neural_onnx", engineName = "Neural Piper TTS Engine")

        outputManager.setPitch(1.2f)
        outputManager.setSpeechRate(1.1f)
        outputManager.setVoice("en-neural-high")

        var swapReady = false
        outputManager.setEngine(secondaryEngine) { ready ->
            swapReady = ready
        }

        assertTrue("New engine should be ready after swap", swapReady)
        assertEquals(secondaryEngine, outputManager.currentEngine)
        assertEquals("neural_onnx", outputManager.currentEngine.engineId)
        assertEquals("Neural Piper TTS Engine", outputManager.currentEngine.engineName)
        assertEquals(1.2f, secondaryEngine.currentPitch, 0.01f)
        assertEquals(1.1f, secondaryEngine.currentRate, 0.01f)
        assertEquals("en-neural-high", secondaryEngine.selectedVoice)

        // Test speaking through the newly swapped engine
        outputManager.speak("Testing swapped engine speech")
        assertEquals(1, secondaryEngine.spokenUtterances.size)
        assertEquals("Testing swapped engine speech", secondaryEngine.spokenUtterances.first())
    }

    @Test
    fun testWordRangeVisemeCallback() {
        var lastWord: String? = null
        var lastStart = -1
        var lastEnd = -1

        outputManager.onWordSpoken = { _, word, start, end ->
            lastWord = word
            lastStart = start
            lastEnd = end
        }

        fakeEngine.emitWordRange("utt-1", 0, 5, "Hello")

        assertEquals("Hello", lastWord)
        assertEquals(0, lastStart)
        assertEquals(5, lastEnd)
    }

    @Test
    fun testAndroidTtsEngineLocaleMapping() {
        val engine = AndroidTextToSpeechEngine(context, logger)

        val usLocale = engine.mapLanguageCodeToLocale("en")
        assertEquals("en", usLocale.language)

        val hindiLocale = engine.mapLanguageCodeToLocale("hi")
        assertEquals("hi", hindiLocale.language)
        assertEquals("IN", hindiLocale.country)

        val bengaliLocale = engine.mapLanguageCodeToLocale("bn")
        assertEquals("bn", bengaliLocale.language)
        assertEquals("IN", bengaliLocale.country)

        val hinglishLocale = engine.mapLanguageCodeToLocale("hinglish")
        assertEquals("hi", hinglishLocale.language)

        val bcp47Locale = engine.mapLanguageCodeToLocale("es-ES")
        assertEquals("es", bcp47Locale.language)
    }

    private class TestTtsEngine(
        override val engineId: String,
        override val engineName: String
    ) : TextToSpeechEngine {
        override var isReady: Boolean = false
        private var _isSpeaking: Boolean = false
        override val isSpeaking: Boolean get() = _isSpeaking

        var autoComplete: Boolean = true
        var simulateError: String? = null
        val spokenUtterances = mutableListOf<String>()

        var currentPitch: Float = 1.0f
        var currentRate: Float = 1.0f
        var selectedVoice: String? = null

        private var wordRangeListener: ((String, Int, Int, String) -> Unit)? = null

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
            spokenUtterances.add(text)
            if (simulateError != null) {
                onError(simulateError!!)
                return
            }

            _isSpeaking = true
            onStart()

            if (autoComplete) {
                _isSpeaking = false
                onDone()
            }
        }

        override fun stop() {
            _isSpeaking = false
        }

        override fun pause() {
            _isSpeaking = false
        }

        override fun resume() {
            _isSpeaking = true
        }

        override fun setPitch(pitch: Float) {
            currentPitch = pitch
        }

        override fun setSpeechRate(rate: Float) {
            currentRate = rate
        }

        override fun getAvailableVoices(): List<VoiceInfo> {
            return listOf(
                VoiceInfo("en-neural-high", "English (United States)", false, "High"),
                VoiceInfo("bn-in-natural", "Bengali (India)", false, "High")
            )
        }

        override fun setVoice(voiceName: String): Boolean {
            selectedVoice = voiceName
            return true
        }

        override fun setOnWordRangeListener(listener: ((utteranceId: String, start: Int, end: Int, word: String) -> Unit)?) {
            this.wordRangeListener = listener
        }

        override fun shutdown() {
            _isSpeaking = false
            isReady = false
        }

        fun emitWordRange(utteranceId: String, start: Int, end: Int, word: String) {
            wordRangeListener?.invoke(utteranceId, start, end, word)
        }
    }
}
