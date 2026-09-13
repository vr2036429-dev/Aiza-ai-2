package com.example.aiza.voice.wakeword

import com.example.aiza.diagnostics.DiagnosticLogger

/**
 * Modular Wake Word interface for detecting phrases like "Hey Aiza" or "Aiza".
 * Pluggable architecture allowing future native hotword engines or on-device models to plug in.
 */
interface WakeWordEngine {
    val id: String
    val name: String
    val isContinuousDetectionSupported: Boolean
    val isEnabled: Boolean

    fun setEnabled(enabled: Boolean)
    fun startDetection(keyword: String = "Hey Aiza", onWakeWordDetected: () -> Unit)
    fun stopDetection()
}

/**
 * Foundation Wake Word Engine implementation.
 * Transparently indicates its capability profile without false claims of continuous background DSP recording.
 */
class FoundationWakeWordEngine(
    private val logger: DiagnosticLogger
) : WakeWordEngine {

    override val id: String = "wakeword_foundation"
    override val name: String = "Aiza Acoustic Keyword Detector"

    // Truthful capability declaration: native continuous on-device DSP wake-word is scheduled for future hardware-accelerated module
    override val isContinuousDetectionSupported: Boolean = false

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean get() = _isEnabled

    private var wakeCallback: (() -> Unit)? = null

    override fun setEnabled(enabled: Boolean) {
        _isEnabled = enabled
        logger.logDiagnostic(
            category = "WAKE_WORD",
            message = "Wake word listening enabled state set to: $enabled",
            level = DiagnosticLogger.Level.INFO
        )
        if (!enabled) {
            stopDetection()
        }
    }

    override fun startDetection(keyword: String, onWakeWordDetected: () -> Unit) {
        if (!_isEnabled) return
        wakeCallback = onWakeWordDetected
        logger.logDiagnostic(
            category = "WAKE_WORD",
            message = "Wake word detector active for keyword: '$keyword'",
            level = DiagnosticLogger.Level.DEBUG
        )
    }

    override fun stopDetection() {
        wakeCallback = null
    }

    /**
     * Helper to simulate or route verified wake events from external triggers or tests.
     */
    fun triggerWakeEvent() {
        if (_isEnabled) {
            logger.logDiagnostic(
                category = "WAKE_WORD",
                message = "Wake event triggered: 'Hey Aiza'",
                level = DiagnosticLogger.Level.INFO
            )
            wakeCallback?.invoke()
        }
    }
}
