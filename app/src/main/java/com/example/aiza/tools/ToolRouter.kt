package com.example.aiza.tools

import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.security.ActionCategory
import com.example.aiza.security.SecurityDecision
import com.example.aiza.security.SecurityLayer
import com.example.aiza.security.SensitivityLevel

/**
 * Central routing and execution engine for all Aiza tools and future feature modules.
 */
interface ToolRouter {
    fun registerTool(tool: AizaTool)
    fun getTool(id: String): AizaTool?
    fun getAllTools(): List<AizaTool>
    suspend fun routeAndExecute(
        toolId: String,
        parameters: Map<String, Any?>,
        authorizedConfirmationId: String? = null
    ): ToolResult
}

class DefaultToolRouter(
    private val securityLayer: SecurityLayer,
    private val logger: DiagnosticLogger
) : ToolRouter {

    private val toolRegistry = mutableMapOf<String, AizaTool>()

    override fun registerTool(tool: AizaTool) {
        toolRegistry[tool.id] = tool
        logger.logDiagnostic(
            category = "TOOL",
            message = "Registered tool: ${tool.name} [ID: ${tool.id}]",
            level = DiagnosticLogger.Level.DEBUG
        )
    }

    override fun getTool(id: String): AizaTool? = toolRegistry[id]

    override fun getAllTools(): List<AizaTool> = toolRegistry.values.toList()

    override suspend fun routeAndExecute(
        toolId: String,
        parameters: Map<String, Any?>,
        authorizedConfirmationId: String?
    ): ToolResult {
        val tool = toolRegistry[toolId] ?: return ToolResult.Failure(
            errorMessage = "No registered tool or module matches ID '$toolId'.",
            recoverySuggestion = "Verify that the requested module is plugged into Aiza Core."
        )

        val target = parameters["target"]?.toString() ?: parameters["task"]?.toString() ?: "System"
        val payload = parameters.toString()

        // If user already approved this specific confirmation ID, proceed
        val wasPreApproved = authorizedConfirmationId != null &&
                securityLayer.resolveConfirmation(authorizedConfirmationId, approved = true)

        if (!wasPreApproved && tool.sensitivityLevel == SensitivityLevel.CONFIRMATION_REQUIRED) {
            val decision = securityLayer.evaluateAction(
                category = tool.actionCategory,
                target = target,
                description = "Execute ${tool.name}: $payload",
                payload = payload,
                isOriginExternal = false
            )

            when (decision) {
                is SecurityDecision.RequiresConfirmation -> {
                    return ToolResult.RequiresConfirmation(
                        confirmationId = decision.confirmationRequest.id,
                        prompt = "Security clearance required to execute ${tool.name}.",
                        target = target,
                        actionCategory = tool.actionCategory
                    )
                }
                is SecurityDecision.Denied -> {
                    return ToolResult.Failure(
                        errorMessage = "Execution blocked by Security Layer: ${decision.reason}",
                        recoverySuggestion = "Explicit authorization by Asik is mandatory."
                    )
                }
                is SecurityDecision.Allowed -> {
                    // Allowed, continue to execute
                }
            }
        }

        logger.logDiagnostic(
            category = "TOOL",
            message = "Executing tool '${tool.name}' with parameters: $parameters",
            level = DiagnosticLogger.Level.INFO
        )

        return try {
            val result = tool.execute(parameters)
            if (result is ToolResult.Success) {
                logger.logDiagnostic(
                    category = "TOOL",
                    message = "Tool '${tool.name}' succeeded in ${result.executionTimeMs}ms.",
                    level = DiagnosticLogger.Level.INFO
                )
            } else if (result is ToolResult.Failure) {
                logger.logDiagnostic(
                    category = "TOOL",
                    message = "Tool '${tool.name}' failed: ${result.errorMessage}",
                    level = DiagnosticLogger.Level.ERROR
                )
            }
            result
        } catch (e: Exception) {
            logger.logDiagnostic(
                category = "TOOL",
                message = "Exception during tool execution: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.ERROR
            )
            ToolResult.Failure(
                errorMessage = "Execution failed unexpectedly: ${e.localizedMessage ?: "Unknown fault"}",
                recoverySuggestion = "Check system logs or retry with different parameters."
            )
        }
    }
}
