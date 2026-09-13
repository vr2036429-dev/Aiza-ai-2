package com.example.aiza.tools

import com.example.aiza.security.ActionCategory
import com.example.aiza.security.SensitivityLevel

/**
 * Result returned by an Aiza tool or module.
 */
sealed class ToolResult(val isSuccess: Boolean) {
    data class Success(
        val output: String,
        val structuredData: Map<String, Any?> = emptyMap(),
        val executionTimeMs: Long = 0L
    ) : ToolResult(true)

    data class Failure(
        val errorMessage: String,
        val recoverySuggestion: String? = null,
        val errorCode: String = "TOOL_EXEC_ERROR"
    ) : ToolResult(false)

    data class RequiresConfirmation(
        val confirmationId: String,
        val prompt: String,
        val target: String,
        val actionCategory: ActionCategory
    ) : ToolResult(false)
}

/**
 * Common interface for tools pluggable into Aiza's Tool Router.
 */
interface AizaTool {
    val id: String
    val name: String
    val description: String
    val sensitivityLevel: SensitivityLevel
    val actionCategory: ActionCategory

    suspend fun execute(parameters: Map<String, Any?>): ToolResult
}
