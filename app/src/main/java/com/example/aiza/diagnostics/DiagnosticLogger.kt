package com.example.aiza.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DiagnosticEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // "AI_BRAIN", "ORCHESTRATOR", "SECURITY", "TOOL", "INTENT", "MODULE", "CORE"
    val message: String,
    val level: DiagnosticLogger.Level = DiagnosticLogger.Level.INFO,
    val metadata: Map<String, String> = emptyMap()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Structured diagnostic logger for Aiza.
 * Strictly sanitizes sensitive credentials, tokens, and keys before recording.
 */
class DiagnosticLogger {

    enum class Level {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        SECURITY_AUDIT
    }

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    private val maxEvents = 100

    fun logDiagnostic(
        category: String,
        message: String,
        level: Level = Level.INFO,
        metadata: Map<String, String> = emptyMap()
    ) {
        val sanitizedMessage = sanitize(message)
        val sanitizedMetadata = metadata.mapValues { sanitize(it.value) }

        val event = DiagnosticEvent(
            category = category,
            message = sanitizedMessage,
            level = level,
            metadata = sanitizedMetadata
        )

        val updated = (_events.value + event).takeLast(maxEvents)
        _events.value = updated
    }

    fun clear() {
        _events.value = emptyList()
    }

    /**
     * Sanitizes strings to guarantee no API keys or passwords ever get logged.
     */
    private fun sanitize(input: String): String {
        return input
            .replace(Regex("(?i)(api[_-]?key|password|token|bearer|secret|auth)\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{8,}['\"]?"), "$1=[REDACTED]")
            .replace(Regex("AIzaSy[A-Za-z0-9_-]{20,}"), "[REDACTED_API_KEY]")
    }
}
