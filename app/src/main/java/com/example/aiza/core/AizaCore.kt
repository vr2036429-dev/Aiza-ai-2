package com.example.aiza.core

import com.example.aiza.brain.AIMessage
import com.example.aiza.brain.AIProviderRegistry
import com.example.aiza.brain.AIRole
import com.example.aiza.core.model.AizaResponse
import com.example.aiza.core.model.AssistantStatus
import com.example.aiza.core.model.UserRequest
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.memory.MemoryInterface
import com.example.aiza.memory.MemoryType
import com.example.aiza.modules.ModuleRegistry
import com.example.aiza.orchestrator.AizaOrchestrator
import com.example.aiza.security.SecurityLayer
import com.example.aiza.tools.ToolRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central Aiza Core system interface.
 */
interface AizaCore {
    val status: StateFlow<AssistantStatus>
    val conversationHistory: StateFlow<List<ConversationTurn>>

    val orchestrator: AizaOrchestrator
    val toolRouter: ToolRouter
    val securityLayer: SecurityLayer
    val memory: MemoryInterface
    val moduleRegistry: ModuleRegistry
    val aiProviderRegistry: AIProviderRegistry
    val logger: DiagnosticLogger

    suspend fun dispatchUserRequest(request: UserRequest): AizaResponse
    suspend fun authorizeSensitiveAction(confirmationId: String, approved: Boolean): AizaResponse?
    fun clearConversation()
}

data class ConversationTurn(
    val request: UserRequest,
    val response: AizaResponse,
    val timestamp: Long = System.currentTimeMillis()
)

class DefaultAizaCore(
    override val orchestrator: AizaOrchestrator,
    override val toolRouter: ToolRouter,
    override val securityLayer: SecurityLayer,
    override val memory: MemoryInterface,
    override val moduleRegistry: ModuleRegistry,
    override val aiProviderRegistry: AIProviderRegistry,
    override val logger: DiagnosticLogger
) : AizaCore {

    override val status: StateFlow<AssistantStatus> = orchestrator.status

    private val _conversationHistory = MutableStateFlow<List<ConversationTurn>>(emptyList())
    override val conversationHistory: StateFlow<List<ConversationTurn>> = _conversationHistory.asStateFlow()

    private val lastPendingRequests = mutableMapOf<String, UserRequest>()

    override suspend fun dispatchUserRequest(request: UserRequest): AizaResponse {
        logger.logDiagnostic(
            category = "CORE",
            message = "Aiza Core received request [${request.id}] from ${request.userName}: '${request.text}'",
            level = DiagnosticLogger.Level.INFO
        )

        // Compile prior turns into multi-turn messages
        val history = _conversationHistory.value.flatMap { turn ->
            listOf(
                AIMessage(role = AIRole.USER, content = turn.request.text),
                AIMessage(role = AIRole.ASSISTANT, content = turn.response.text)
            )
        }

        val response = orchestrator.processRequest(
            request = request,
            conversationHistory = history
        )

        // If requires confirmation, keep reference to original request for retry after authorization
        if (response.requiresConfirmation && response.pendingActionId != null) {
            lastPendingRequests[response.pendingActionId] = request
        }

        // Store in local conversation history
        _conversationHistory.value = _conversationHistory.value + ConversationTurn(request, response)

        // Save conversation context to memory
        memory.saveMemory(
            type = MemoryType.CONVERSATION_CONTEXT,
            key = "last_interaction",
            value = "User asked '${request.text}', Aiza responded in ${response.language.displayName}"
        )

        return response
    }

    override suspend fun authorizeSensitiveAction(confirmationId: String, approved: Boolean): AizaResponse? {
        val originalRequest = lastPendingRequests.remove(confirmationId) ?: return null

        logger.logDiagnostic(
            category = "CORE",
            message = "Processing authorization decision for Action [$confirmationId]: Approved=$approved",
            level = DiagnosticLogger.Level.SECURITY_AUDIT
        )

        if (!approved) {
            securityLayer.resolveConfirmation(confirmationId, approved = false)
            val deniedResponse = AizaResponse(
                requestId = originalRequest.id,
                text = "Authorization declined by Asik. The sensitive operation has been cancelled safely.",
                language = originalRequest.explicitLanguage ?: com.example.aiza.core.model.Language.ENGLISH,
                intent = com.example.aiza.intent.AizaIntent.NormalConversation(),
                requiresConfirmation = false
            )
            _conversationHistory.value = _conversationHistory.value + ConversationTurn(originalRequest, deniedResponse)
            return deniedResponse
        }

        // Re-execute through orchestrator with pre-approved confirmation token
        val approvedResponse = orchestrator.processRequest(
            request = originalRequest,
            conversationHistory = emptyList(),
            authorizedConfirmationId = confirmationId
        )

        _conversationHistory.value = _conversationHistory.value + ConversationTurn(originalRequest, approvedResponse)
        return approvedResponse
    }

    override fun clearConversation() {
        _conversationHistory.value = emptyList()
        logger.logDiagnostic(
            category = "CORE",
            message = "Conversation history reset by Asik.",
            level = DiagnosticLogger.Level.INFO
        )
    }
}
