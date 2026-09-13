package com.example.aiza.security

import java.util.UUID

enum class SensitivityLevel {
    SAFE,                    // Read-only info, device stats, calculations
    CONFIRMATION_REQUIRED,   // Sensitive actions: sending messages, making calls, deleting files, purchases
    RESTRICTED               // High risk, disallowed or strictly sandboxed
}

enum class ActionCategory {
    COMMUNICATION_SEND,      // Sending SMS, emails, instant messages
    COMMUNICATION_CALL,      // Dialing phone calls
    FILE_DELETION,           // Deleting or modifying local files
    FINANCIAL_TRANSACTION,   // Purchases, payments, transfers
    PUBLIC_POST,             // Posting to social media or public forums
    SYSTEM_SETTING_CHANGE,   // Modifying critical OS settings
    CUSTOM_SENSITIVE         // Other potentially harmful or irreversible actions
}

data class ConfirmationRequest(
    val id: String = UUID.randomUUID().toString(),
    val actionCategory: ActionCategory,
    val title: String,
    val description: String,
    val target: String,
    val payloadPreview: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class SecurityDecision {
    object Allowed : SecurityDecision()

    data class RequiresConfirmation(
        val confirmationRequest: ConfirmationRequest
    ) : SecurityDecision()

    data class Denied(
        val reason: String
    ) : SecurityDecision()
}
