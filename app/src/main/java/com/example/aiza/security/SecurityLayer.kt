package com.example.aiza.security

import com.example.aiza.diagnostics.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SecurityLayer {
    fun evaluateAction(
        category: ActionCategory,
        target: String,
        description: String,
        payload: String,
        isOriginExternal: Boolean = false
    ): SecurityDecision

    fun registerPendingConfirmation(request: ConfirmationRequest)
    fun resolveConfirmation(requestId: String, approved: Boolean): Boolean
    fun getPendingConfirmation(requestId: String): ConfirmationRequest?
    val pendingConfirmations: StateFlow<List<ConfirmationRequest>>
}

class DefaultSecurityLayer(
    private val logger: DiagnosticLogger
) : SecurityLayer {

    private val _pendingConfirmations = MutableStateFlow<List<ConfirmationRequest>>(emptyList())
    override val pendingConfirmations: StateFlow<List<ConfirmationRequest>> = _pendingConfirmations.asStateFlow()

    private val approvedActionIds = mutableSetOf<String>()

    override fun evaluateAction(
        category: ActionCategory,
        target: String,
        description: String,
        payload: String,
        isOriginExternal: Boolean
    ): SecurityDecision {
        // Critical Policy: External content can NEVER trigger sensitive actions automatically
        if (isOriginExternal) {
            logger.logDiagnostic(
                category = "SECURITY",
                message = "DENIED: Attempt to trigger sensitive action ($category) from untrusted external source.",
                level = DiagnosticLogger.Level.SECURITY_AUDIT
            )
            return SecurityDecision.Denied("Security violation: External content cannot trigger sensitive operations.")
        }

        val requiresConfirmation = when (category) {
            ActionCategory.COMMUNICATION_SEND,
            ActionCategory.COMMUNICATION_CALL,
            ActionCategory.FILE_DELETION,
            ActionCategory.FINANCIAL_TRANSACTION,
            ActionCategory.PUBLIC_POST,
            ActionCategory.CUSTOM_SENSITIVE -> true

            ActionCategory.SYSTEM_SETTING_CHANGE -> true
        }

        if (requiresConfirmation) {
            val request = ConfirmationRequest(
                actionCategory = category,
                title = "Authorization Required",
                description = description,
                target = target,
                payloadPreview = payload
            )
            registerPendingConfirmation(request)

            logger.logDiagnostic(
                category = "SECURITY",
                message = "Confirmation required for [${category.name}] on target '$target'.",
                level = DiagnosticLogger.Level.SECURITY_AUDIT,
                metadata = mapOf("actionId" to request.id, "target" to target)
            )

            return SecurityDecision.RequiresConfirmation(request)
        }

        logger.logDiagnostic(
            category = "SECURITY",
            message = "Action approved: [${category.name}] on target '$target'.",
            level = DiagnosticLogger.Level.SECURITY_AUDIT
        )
        return SecurityDecision.Allowed
    }

    override fun registerPendingConfirmation(request: ConfirmationRequest) {
        val current = _pendingConfirmations.value.toMutableList()
        current.add(request)
        _pendingConfirmations.value = current
    }

    override fun resolveConfirmation(requestId: String, approved: Boolean): Boolean {
        val request = _pendingConfirmations.value.find { it.id == requestId } ?: return false
        _pendingConfirmations.value = _pendingConfirmations.value.filter { it.id != requestId }

        if (approved) {
            approvedActionIds.add(requestId)
            logger.logDiagnostic(
                category = "SECURITY",
                message = "Asik AUTHORIZED sensitive action: ${request.title} (${request.target})",
                level = DiagnosticLogger.Level.SECURITY_AUDIT
            )
        } else {
            logger.logDiagnostic(
                category = "SECURITY",
                message = "Asik REJECTED sensitive action: ${request.title} (${request.target})",
                level = DiagnosticLogger.Level.SECURITY_AUDIT
            )
        }
        return approved
    }

    override fun getPendingConfirmation(requestId: String): ConfirmationRequest? {
        return _pendingConfirmations.value.find { it.id == requestId }
    }
}
