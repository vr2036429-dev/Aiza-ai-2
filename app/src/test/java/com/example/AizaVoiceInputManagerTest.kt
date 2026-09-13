package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStateManager
import com.example.aiza.voice.input.AndroidSpeechRecognitionEngine
import com.example.aiza.voice.input.SpeechRecognitionEngine
import com.example.aiza.voice.input.VoiceInputManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AizaVoiceInputManagerTest {

    private lateinit var context: Context
    private lateinit var logger: DiagnosticLogger
    private lateinit var stateManager: VoiceStateManager
    private lateinit var fakeEngine: TestSpeechRecognitionEngine
    private lateinit var voiceInputManager: VoiceInputManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logger = DiagnosticLogger()
        stateManager = VoiceStateManager(logger)
        fakeEngine = TestSpeechRecognitionEngine()
        voiceInputManager = VoiceInputManager(context, fakeEngine, stateManager, logger)
    }

    @Test
    fun testMicrophonePermissionDeniedBlocksListening() {
        // Ensure RECORD_AUDIO permission is denied
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)

        assertFalse("Permission should be denied", voiceInputManager.hasMicrophonePermission())
        assertFalse("isPermissionGranted should be false", voiceInputManager.isPermissionGranted)

        var permissionDeniedCallbackFired = false
        var speechErrorFired: VoiceError? = null

        voiceInputManager.onPermissionDenied = {
            permissionDeniedCallbackFired = true
        }
        voiceInputManager.onSpeechError = { error ->
            speechErrorFired = error
        }

        voiceInputManager.startListening("en-US")

        assertTrue("onPermissionDenied should have been invoked", permissionDeniedCallbackFired)
        assertNotNull("onSpeechError should have received an error", speechErrorFired)
        assertTrue(
            "Error should be MicrophonePermissionDenied",
            speechErrorFired is VoiceError.MicrophonePermissionDenied
        )
        assertEquals(VoiceState.ERROR, stateManager.status.value.state)
        assertFalse("Engine should not have been started", fakeEngine.isListening)
    }

    @Test
    fun testMicrophonePermissionGrantedStartsListening() {
        // Grant RECORD_AUDIO permission
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        assertTrue("Permission should be granted", voiceInputManager.hasMicrophonePermission())
        assertTrue("isPermissionGranted should be true", voiceInputManager.isPermissionGranted)

        var listeningStartedFired = false
        voiceInputManager.onListeningStarted = {
            listeningStartedFired = true
        }

        voiceInputManager.startListening("en-US")

        assertTrue("Engine should be listening", fakeEngine.isListening)
        assertTrue("onListeningStarted should have fired", listeningStartedFired)
        assertEquals(VoiceState.LISTENING, stateManager.status.value.state)
        assertEquals("en-US", fakeEngine.lastLanguageCode)
    }

    @Test
    fun testPartialSpeechHypothesesStreaming() {
        grantMicrophonePermission()

        var receivedPartial: String? = null
        voiceInputManager.onPartialResult = { partial ->
            receivedPartial = partial
        }

        voiceInputManager.startListening("en-US")
        fakeEngine.emitPartial("Hello")

        assertEquals("Hello", receivedPartial)
        assertEquals("Hello", stateManager.status.value.partialText)
        assertEquals(VoiceState.LISTENING, stateManager.status.value.state)

        fakeEngine.emitPartial("Hello Aiza")
        assertEquals("Hello Aiza", receivedPartial)
        assertEquals("Hello Aiza", stateManager.status.value.partialText)
    }

    @Test
    fun testFinalSpeechAudioConversionToText() {
        grantMicrophonePermission()

        var finalResult: String? = null
        voiceInputManager.onSpeechRecognized = { text ->
            finalResult = text
        }

        voiceInputManager.startListening("en-US")
        fakeEngine.emitPartial("Turn on")
        fakeEngine.emitFinal("Turn on the living room lights")

        assertEquals("Turn on the living room lights", finalResult)
        assertEquals("Turn on the living room lights", stateManager.status.value.partialText)
        assertEquals(VoiceState.PROCESSING, stateManager.status.value.state)
    }

    @Test
    fun testRmsDecibelsNormalization() {
        grantMicrophonePermission()

        var lastNormalizedRms = 0f
        voiceInputManager.onRmsChanged = { rms ->
            lastNormalizedRms = rms
        }

        voiceInputManager.startListening("en-US")

        // Input 4.0 dB: (4 + 2) / 12 = 0.5
        fakeEngine.emitRms(4.0f)
        assertEquals(0.5f, lastNormalizedRms, 0.01f)
        assertEquals(0.5f, stateManager.status.value.soundLevelDb, 0.01f)

        // Lower bound clamp test: -10 dB -> should clamp to 0.0
        fakeEngine.emitRms(-10.0f)
        assertEquals(0.0f, lastNormalizedRms, 0.01f)

        // Upper bound clamp test: 20 dB -> should clamp to 1.0
        fakeEngine.emitRms(20.0f)
        assertEquals(1.0f, lastNormalizedRms, 0.01f)
    }

    @Test
    fun testSpeechRecognitionUnavailableError() {
        grantMicrophonePermission()
        fakeEngine.available = false

        var reportedError: VoiceError? = null
        voiceInputManager.onSpeechError = { error ->
            reportedError = error
        }

        voiceInputManager.startListening("en-US")

        assertTrue(
            "Error should be SpeechRecognitionUnavailable",
            reportedError is VoiceError.SpeechRecognitionUnavailable
        )
        assertEquals(VoiceState.ERROR, stateManager.status.value.state)
    }

    @Test
    fun testStopListeningCancelAndDestroyLifecycle() {
        grantMicrophonePermission()

        var listeningStopped = false
        voiceInputManager.onListeningStopped = {
            listeningStopped = true
        }

        voiceInputManager.startListening("en-US")
        assertEquals(VoiceState.LISTENING, stateManager.status.value.state)

        voiceInputManager.stopListening()
        assertTrue("onListeningStopped should fire on stop", listeningStopped)
        assertEquals(VoiceState.PROCESSING, stateManager.status.value.state)
        assertFalse(fakeEngine.isListening)

        voiceInputManager.cancel()
        assertEquals(VoiceState.IDLE, stateManager.status.value.state)

        voiceInputManager.destroy()
        assertTrue("fakeEngine should be destroyed", fakeEngine.isDestroyed)
    }

    @Test
    fun testAndroidSpeechRecognitionEngineErrorMapping() {
        val engine = AndroidSpeechRecognitionEngine(context, logger)

        // Test error mapping
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_AUDIO) is VoiceError.MicrophoneUnavailable)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) is VoiceError.MicrophonePermissionDenied)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_NETWORK) is VoiceError.NetworkError)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT) is VoiceError.NetworkError)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_NO_MATCH) is VoiceError.NoSpeechDetected)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) is VoiceError.NoSpeechDetected)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY) is VoiceError.MicrophoneUnavailable)
        assertTrue(engine.mapSpeechErrorCode(android.speech.SpeechRecognizer.ERROR_SERVER) is VoiceError.NetworkError)
        assertTrue(engine.mapSpeechErrorCode(9999) is VoiceError.Unknown)
    }

    private fun grantMicrophonePermission() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    private class TestSpeechRecognitionEngine : SpeechRecognitionEngine {
        var available: Boolean = true
        var _isListening: Boolean = false
        override val isListening: Boolean get() = _isListening
        var isDestroyed: Boolean = false
        var lastLanguageCode: String? = null

        private var onPartial: ((String) -> Unit)? = null
        private var onFinal: ((String) -> Unit)? = null
        private var onError: ((VoiceError) -> Unit)? = null
        private var onRms: ((Float) -> Unit)? = null

        override fun isRecognitionAvailable(): Boolean = available

        override fun startListening(
            languageCode: String,
            onPartialResult: (String) -> Unit,
            onFinalResult: (String) -> Unit,
            onError: (VoiceError) -> Unit,
            onRmsChanged: (Float) -> Unit
        ) {
            _isListening = true
            lastLanguageCode = languageCode
            this.onPartial = onPartialResult
            this.onFinal = onFinalResult
            this.onError = onError
            this.onRms = onRmsChanged
        }

        override fun stopListening() {
            _isListening = false
        }

        override fun cancel() {
            _isListening = false
        }

        override fun destroy() {
            _isListening = false
            isDestroyed = true
        }

        fun emitPartial(text: String) {
            onPartial?.invoke(text)
        }

        fun emitFinal(text: String) {
            _isListening = false
            onFinal?.invoke(text)
        }

        fun emitError(error: VoiceError) {
            _isListening = false
            onError?.invoke(error)
        }

        fun emitRms(rms: Float) {
            onRms?.invoke(rms)
        }
    }
}
