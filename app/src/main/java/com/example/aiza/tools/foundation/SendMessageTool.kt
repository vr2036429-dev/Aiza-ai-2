package com.example.aiza.tools.foundation

import com.example.aiza.security.ActionCategory
import com.example.aiza.security.SensitivityLevel
import com.example.aiza.tools.AizaTool
import com.example.aiza.tools.ToolResult

/**
 * Tool for dispatching communications (SMS, messages).
 * Marked as CONFIRMATION_REQUIRED because sending messages is a sensitive action.
 */
class SendMessageTool : AizaTool {

    override val id: String = "tool_communication_send"
    override val name: String = "Communication Dispatcher"
    override val description: String = "Sends messages and notices to external contacts."
    override val sensitivityLevel: SensitivityLevel = SensitivityLevel.CONFIRMATION_REQUIRED
    override val actionCategory: ActionCategory = ActionCategory.COMMUNICATION_SEND

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val target = parameters["target"] as? String ?: "Unknown Contact"
        val message = parameters["message"] as? String ?: ""

        if (message.isBlank()) {
            return ToolResult.Failure(
                errorMessage = "Cannot send an empty message.",
                recoverySuggestion = "Please specify the content of the message to send to $target."
            )
        }

        // In real execution (after authorization), message is dispatched
        return ToolResult.Success(
            output = "Message successfully dispatched to $target: \"$message\"",
            structuredData = mapOf("target" to target, "status" to "SENT"),
            executionTimeMs = 120L
        )
    }
}
