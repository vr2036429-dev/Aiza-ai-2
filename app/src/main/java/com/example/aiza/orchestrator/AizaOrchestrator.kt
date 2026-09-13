package com.example.aiza.orchestrator

import com.example.aiza.brain.AIMessage
import com.example.aiza.brain.AIProviderRegistry
import com.example.aiza.brain.AIRequest
import com.example.aiza.brain.AIRole
import com.example.aiza.core.model.AizaResponse
import com.example.aiza.core.model.AssistantStatus
import com.example.aiza.core.model.Language
import com.example.aiza.core.model.UserRequest
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.intent.IntentClassifier
import com.example.aiza.response.ResponseManager
import com.example.aiza.security.SecurityLayer
import com.example.aiza.tools.ToolResult
import com.example.aiza.tools.ToolRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AizaOrchestrator {
    val status: StateFlow<AssistantStatus>

    suspend fun processRequest(
        request: UserRequest,
        conversationHistory: List<AIMessage> = emptyList(),
        authorizedConfirmationId: String? = null
    ): AizaResponse
}

class DefaultAizaOrchestrator(
    private val intentClassifier: IntentClassifier,
    private val toolRouter: ToolRouter,
    private val securityLayer: SecurityLayer,
    private val aiProviderRegistry: AIProviderRegistry,
    private val responseManager: ResponseManager,
    private val logger: DiagnosticLogger
) : AizaOrchestrator {

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    override val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    override suspend fun processRequest(
        request: UserRequest,
        conversationHistory: List<AIMessage>,
        authorizedConfirmationId: String?
    ): AizaResponse {
        val startTime = System.currentTimeMillis()

        try {
            // STEP 1: Understand
            _status.value = AssistantStatus.UNDERSTANDING
            logger.logDiagnostic(
                category = "ORCHESTRATOR",
                message = "Step 1 [Understand]: Receiving input from ${request.userName}: \"${request.text}\"",
                level = DiagnosticLogger.Level.INFO
            )

            // STEP 2: Classify Intent & Detect Language
            _status.value = AssistantStatus.ANALYZING_INTENT
            val intentResult = intentClassifier.classify(request)
            val effectiveLanguage = request.explicitLanguage ?: intentResult.extractedLanguageHint

            logger.logDiagnostic(
                category = "ORCHESTRATOR",
                message = "Step 2 [Classify]: Intent='${intentResult.intent.name}', Lang='${effectiveLanguage.displayName}', RequiresTool=${intentResult.requiresTool}",
                level = DiagnosticLogger.Level.INFO
            )

            var toolResult: ToolResult? = null
            var requiresConfirmation = false
            var pendingActionId: String? = null
            var pendingActionDesc: String? = null

            // STEP 3: Select Appropriate Module/Tool (if needed)
            if (intentResult.requiresTool && intentResult.toolId != null) {
                // STEP 4: Security Check
                _status.value = AssistantStatus.CHECKING_SECURITY
                logger.logDiagnostic(
                    category = "ORCHESTRATOR",
                    message = "Step 3/4 [Tool Selection & Security Check]: Target tool '${intentResult.toolId}'",
                    level = DiagnosticLogger.Level.INFO
                )

                // STEP 5: Execute via Tool Router
                _status.value = AssistantStatus.EXECUTING_TOOL
                val execResult = toolRouter.routeAndExecute(
                    toolId = intentResult.toolId,
                    parameters = intentResult.toolParameters,
                    authorizedConfirmationId = authorizedConfirmationId
                )

                // STEP 6: Verify Result
                logger.logDiagnostic(
                    category = "ORCHESTRATOR",
                    message = "Step 6 [Verify Result]: Execution returned isSuccess=${execResult.isSuccess}",
                    level = DiagnosticLogger.Level.INFO
                )

                toolResult = execResult

                if (execResult is ToolResult.RequiresConfirmation) {
                    _status.value = AssistantStatus.AWAITING_CONFIRMATION
                    requiresConfirmation = true
                    pendingActionId = execResult.confirmationId
                    pendingActionDesc = execResult.prompt
                }
            }

            // STEP 7: Generate AI Response
            var rawAIContent: String? = null

            // If no tool was needed, or if we need conversational synthesis:
            if (toolResult == null || (!requiresConfirmation && toolResult is ToolResult.Success && toolResult.output.length < 50)) {
                _status.value = AssistantStatus.THINKING
                logger.logDiagnostic(
                    category = "ORCHESTRATOR",
                    message = "Step 7 [Generate Response]: Querying AI Brain (${aiProviderRegistry.activeProvider.value.displayName})",
                    level = DiagnosticLogger.Level.INFO
                )

                val aiRequest = AIRequest(
                    prompt = request.text,
                    conversationHistory = conversationHistory,
                    targetLanguage = effectiveLanguage,
                    userName = request.userName
                )

                val provider = aiProviderRegistry.activeProvider.value
                val aiResult = provider.generateResponse(aiRequest)
                rawAIContent = aiResult.getOrNull()?.content
            }

            _status.value = AssistantStatus.SPEAKING
            val finalFormattedText = responseManager.formatResponse(
                userName = request.userName,
                language = effectiveLanguage,
                intent = intentResult.intent,
                rawAIContent = rawAIContent,
                toolResult = toolResult
            )

            val totalDuration = System.currentTimeMillis() - startTime

            logger.logDiagnostic(
                category = "ORCHESTRATOR",
                message = "Workflow completed in ${totalDuration}ms. Response ready for ${request.userName}.",
                level = DiagnosticLogger.Level.INFO
            )

            if (!requiresConfirmation) {
                _status.value = AssistantStatus.IDLE
            }

            return AizaResponse(
                requestId = request.id,
                text = finalFormattedText,
                language = effectiveLanguage,
                intent = intentResult.intent,
                toolResult = toolResult,
                requiresConfirmation = requiresConfirmation,
                pendingActionId = pendingActionId,
                pendingActionDescription = pendingActionDesc,
                executionDurationMs = totalDuration
            )

        } catch (e: Exception) {
            _status.value = AssistantStatus.ERROR
            logger.logDiagnostic(
                category = "ORCHESTRATOR",
                message = "Fatal orchestration exception: ${e.localizedMessage}",
                level = DiagnosticLogger.Level.ERROR
            )

            return AizaResponse(
                requestId = request.id,
                text = "An unexpected error occurred while processing your request, Asik Sir. Please check system diagnostics.",
                language = Language.ENGLISH,
                intent = com.example.aiza.intent.AizaIntent.NormalConversation(),
                toolResult = ToolResult.Failure(
                    errorMessage = e.localizedMessage ?: "Unknown Orchestrator Error",
                    recoverySuggestion = "Aiza Core has safely caught the error and preserved system state."
                ),
                executionDurationMs = System.currentTimeMillis() - startTime
            ).also {
                _status.value = AssistantStatus.IDLE
            }
        }
    }
}
