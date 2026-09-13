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
import com.example.aiza.voice.DefaultNaturalVoiceAssistantModule
import com.example.aiza.voice.VoiceSettings
import com.example.aiza.voice.VoiceStatusSnapshot
import com.example.aiza.voice.output.VoiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AizaViewModel(application: Application) : AndroidViewModel(application) {

    private val core: AizaCore = AizaContainer.getAizaCore(application)
    val voiceAssistant: DefaultNaturalVoiceAssistantModule = AizaContainer.getVoiceAssistant(application)

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

    // Voice State & Settings
    val voiceStatus: StateFlow<VoiceStatusSnapshot> = voiceAssistant.voiceStatus
    val voiceSettings: StateFlow<VoiceSettings> = voiceAssistant.voiceSettings

    private val _showVoiceSettingsDialog = MutableStateFlow(false)
    val showVoiceSettingsDialog: StateFlow<Boolean> = _showVoiceSettingsDialog.asStateFlow()

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
        voiceAssistant.settingsRepository.setPreferredLanguage(language)
    }

    fun openDiagnostics(open: Boolean) {
        _showDiagnosticsSheet.value = open
    }

    fun openVoiceSettings(open: Boolean) {
        _showVoiceSettingsDialog.value = open
    }

    fun clearHistory() {
        core.clearConversation()
    }

    fun clearDiagnostics() {
        core.logger.clear()
    }

    // Voice Actions
    fun startVoiceListening() {
        viewModelScope.launch {
            voiceAssistant.startListening()
        }
    }

    fun stopVoiceListening() {
        viewModelScope.launch {
            voiceAssistant.stopListening()
        }
    }

    fun stopSpeaking() {
        voiceAssistant.stopSpeaking()
    }

    fun cancelVoice() {
        voiceAssistant.cancel()
    }

    fun setVoiceAssistantEnabled(enabled: Boolean) {
        voiceAssistant.settingsRepository.setVoiceAssistantEnabled(enabled)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        voiceAssistant.settingsRepository.setWakeWordEnabled(enabled)
        voiceAssistant.wakeWordEngine.setEnabled(enabled)
    }

    fun setSpeechRate(rate: Float) {
        voiceAssistant.settingsRepository.setSpeechRate(rate)
        voiceAssistant.voiceOutputManager.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        voiceAssistant.settingsRepository.setPitch(pitch)
        voiceAssistant.voiceOutputManager.setPitch(pitch)
    }

    fun setPreferredVoiceLanguage(language: Language) {
        voiceAssistant.settingsRepository.setPreferredLanguage(language)
    }

    fun setSelectedVoice(voiceName: String?) {
        voiceAssistant.settingsRepository.setSelectedVoiceName(voiceName)
        if (voiceName != null) {
            voiceAssistant.voiceOutputManager.setVoice(voiceName)
        }
    }

    fun testVoice() {
        voiceAssistant.testVoice()
    }

    fun getAvailableVoices(): List<VoiceInfo> = voiceAssistant.getAvailableVoices()

    fun hasMicrophonePermission(): Boolean = voiceAssistant.voiceInputManager.hasMicrophonePermission()
}
