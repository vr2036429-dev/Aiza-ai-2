package com.example.aiza.modules

import com.example.aiza.diagnostics.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages modular plug-ins for Aiza.
 * Allows adding new capability modules at runtime without redesigning Aiza Core.
 */
class ModuleRegistry(
    private val logger: DiagnosticLogger
) {
    private val _modules = MutableStateFlow<Map<String, AizaModule>>(emptyMap())
    val modules: StateFlow<Map<String, AizaModule>> = _modules.asStateFlow()

    init {
        // Register foundation readiness stubs for all 15 planned modules
        registerFoundationStubs()
    }

    fun registerModule(module: AizaModule) {
        val updated = _modules.value.toMutableMap()
        updated[module.id] = module
        _modules.value = updated

        logger.logDiagnostic(
            category = "MODULE",
            message = "Plugged in module: ${module.name} (v${module.version}) [Category: ${module.category.name}]",
            level = DiagnosticLogger.Level.INFO
        )
    }

    fun getModule(id: String): AizaModule? = _modules.value[id]

    fun getAllModules(): List<AizaModule> = _modules.value.values.toList()

    private fun registerFoundationStubs() {
        val stubs = listOf(
            FoundationModuleStub("mod_voice", "Natural Voice Assistant", "Full duplex speech-to-text and voice generation", ModuleCategory.VOICE_AND_AUDIO),
            FoundationModuleStub("mod_background", "Background Operation", "WorkManager background polling & triggers", ModuleCategory.SYSTEM_AUTOMATION),
            FoundationModuleStub("mod_wake_word", "Wake Word Engine", "Low-power on-device 'Hey Aiza' detection", ModuleCategory.VOICE_AND_AUDIO),
            FoundationModuleStub("mod_3d_avatar", "3D Floating Avatar", "Interactive HUD floating companion", ModuleCategory.VOICE_AND_AUDIO),
            FoundationModuleStub("mod_automation", "Android Automation", "Accessibility tree automation & workflows", ModuleCategory.SYSTEM_AUTOMATION),
            FoundationModuleStub("mod_screen_read", "Screen Reading", "MediaProjection visual context analyzer", ModuleCategory.HARDWARE_AND_SENSORS),
            FoundationModuleStub("mod_web_research", "Web Search & Research", "Autonomous internet querying and summarization", ModuleCategory.RESEARCH_AND_WEB),
            FoundationModuleStub("mod_file_mgmt", "File Management", "Document indexing, parsing, and storage", ModuleCategory.SYSTEM_AUTOMATION),
            FoundationModuleStub("mod_communication", "Calls & Messaging", "Telephony and messaging dispatch engine", ModuleCategory.COMMUNICATION),
            FoundationModuleStub("mod_notifications", "Notification Intelligence", "Notification listening, filtering, and priority alerts", ModuleCategory.COMMUNICATION),
            FoundationModuleStub("mod_smart_auto", "Smart Automation", "Multi-step contextual routine triggers", ModuleCategory.SYSTEM_AUTOMATION),
            FoundationModuleStub("mod_reminders", "Reminders & Alarms", "Exact alarm clock and schedule manager", ModuleCategory.SYSTEM_AUTOMATION),
            FoundationModuleStub("mod_long_term_mem", "Long-Term Memory", "Vector embedding and semantic recall", ModuleCategory.MEMORY_AND_STORAGE),
            FoundationModuleStub("mod_multi_ai", "Multiple AI Connectors", "Hot-swappable secondary model router", ModuleCategory.CORE_BRAIN),
            FoundationModuleStub("mod_proactive", "Proactive Intelligence", "Predictive suggestions based on routines", ModuleCategory.PROACTIVE_AI)
        )

        val map = stubs.associateBy { it.id }
        _modules.value = map
    }

    private class FoundationModuleStub(
        override val id: String,
        override val name: String,
        override val description: String,
        override val category: ModuleCategory,
        override val version: String = "1.0.0-foundation",
        override val status: ModuleStatus = ModuleStatus.READY
    ) : AizaModule {
        override suspend fun initialize(): Boolean = true
        override suspend fun shutdown() {}
    }
}
