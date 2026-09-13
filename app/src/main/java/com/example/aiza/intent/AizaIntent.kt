package com.example.aiza.intent

/**
 * Categorization of user intent understood by Aiza Core.
 */
sealed class AizaIntent(val name: String, val category: String) {
    /** General social / conversational query */
    data class NormalConversation(val topic: String? = null) :
        AizaIntent("Normal Conversation", "Social")

    /** Query seeking factual knowledge, explanations, or analysis */
    data class InformationRequest(val query: String) :
        AizaIntent("Information Request", "Knowledge")

    /** Request to control device settings, sensors, or query status */
    data class DeviceAction(val action: String, val parameters: Map<String, String> = emptyMap()) :
        AizaIntent("Device Action", "Device")

    /** Request for reading, creating, moving, or deleting files */
    data class FileOperation(val operation: String, val target: String, val isDestructive: Boolean = false) :
        AizaIntent("File Operation", "System")

    /** Request to send messages, make phone calls, or compose emails */
    data class Communication(val target: String, val message: String, val channel: String = "sms") :
        AizaIntent("Communication", "Communication")

    /** Request to set a reminder, alarm, or timer */
    data class Reminder(val task: String, val timeHint: String? = null) :
        AizaIntent("Reminder & Task", "Productivity")

    /** Request to trigger a sequence, routine, or automated script */
    data class Automation(val routineName: String, val params: Map<String, String> = emptyMap()) :
        AizaIntent("Automation", "Automation")

    /** Request to search the web, research current events, or summarize websites */
    data class WebResearch(val topic: String) :
        AizaIntent("Web Research", "Research")

    /** Request to post or read notifications */
    data class Notification(val title: String, val body: String) :
        AizaIntent("Notification", "System")

    /** Extensible point for future tool tasks */
    data class FutureToolTask(val taskName: String, val parameters: Map<String, String> = emptyMap()) :
        AizaIntent("Tool Task", "Extensible")
}

data class IntentClassificationResult(
    val intent: AizaIntent,
    val confidence: Float,
    val requiresTool: Boolean,
    val toolId: String? = null,
    val toolParameters: Map<String, Any?> = emptyMap(),
    val extractedLanguageHint: com.example.aiza.core.model.Language = com.example.aiza.core.model.Language.ENGLISH
)
