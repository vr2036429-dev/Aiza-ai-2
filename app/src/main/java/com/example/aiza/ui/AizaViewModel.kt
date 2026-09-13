package com.example.aiza.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiza.core.AizaContainer
import com.example.aiza.core.AizaCore
import com.example.aiza.core.ConversationTurn
import com.example.aiza.core.model.AssistantStatus
import com.example.aiza.core.model.Language
import com.example.aiza.core.model.UserRequest
import com.example.aiza.diagnostics.DiagnosticEvent
import com.example.aiza.memory.MemoryEntry
import com.example.aiza.modules.AizaModule
import com.example.aiza.security.ConfirmationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AizaViewModel(application: Application) : AndroidViewModel(application) {

    private val core: AizaCore = AizaContainer.getAizaCore(application)

    val status: StateFlow<AssistantStatus> = core.status
    val conversationHistory: StateFlow<List<ConversationTurn>> = core.conversationHistory
    val diagnosticEvents: StateFlow<List<DiagnosticEvent>> = core.logger.events

    val pendingConfirmations: StateFlow<List<ConfirmationRequest>> = core.securityLayer.pendingConfirmations
    val latestPendingConfirmation: StateFlow<ConfirmationRequest?> = core.securityLayer.pendingConfirmations
        .map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val modules: StateFlow<List<AizaModule>> = core.moduleRegistry.modules
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val memories: StateFlow<List<MemoryEntry>> = core.memory.memories

    private val _selectedLanguage = MutableStateFlow(Language.UNKNOWN)
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _showDiagnosticsSheet = MutableStateFlow(false)
    val showDiagnosticsSheet: StateFlow<Boolean> = _showDiagnosticsSheet.asStateFlow()

    val activeProviderName: String
        get() = core.aiProviderRegistry.activeProvider.value.displayName

    val isProviderConfigured: Boolean
        get() = core.aiProviderRegistry.activeProvider.value.isConfigured

    fun sendRequest(text: String) {
        if (text.isBlank()) return
        val currentLang = if (_selectedLanguage.value != Language.UNKNOWN) _selectedLanguage.value else null

        val request = UserRequest(
            text = text.trim(),
            userName = "Asik",
            explicitLanguage = currentLang
        )

        viewModelScope.launch {
            core.dispatchUserRequest(request)
        }
    }

    fun authorizeAction(confirmationId: String, approved: Boolean) {
        viewModelScope.launch {
            core.authorizeSensitiveAction(confirmationId, approved)
        }
    }

    fun setLanguageFilter(language: Language) {
        _selectedLanguage.value = language
    }

    fun openDiagnostics(open: Boolean) {
        _showDiagnosticsSheet.value = open
    }

    fun clearHistory() {
        core.clearConversation()
    }

    fun clearDiagnostics() {
        core.logger.clear()
    }
}
