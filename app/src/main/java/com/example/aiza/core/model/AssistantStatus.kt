package com.example.aiza.core.model

/**
 * Real-time operational state of the Aiza Assistant.
 */
enum class AssistantStatus(val label: String, val isBusy: Boolean) {
    IDLE("Ready for Asik", false),
    UNDERSTANDING("Understanding Request...", true),
    ANALYZING_INTENT("Analyzing Intent...", true),
    CHECKING_SECURITY("Verifying Security Layer...", true),
    THINKING("Thinking...", true),
    EXECUTING_TOOL("Executing Module...", true),
    AWAITING_CONFIRMATION("Awaiting Asik's Authorization", false),
    SPEAKING("Responding...", false),
    ERROR("Error Detected", false)
}
