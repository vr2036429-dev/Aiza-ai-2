package com.example.aiza.modules

/**
 * Lifecycle state of an Aiza feature module.
 */
enum class ModuleStatus {
    UNINITIALIZED,
    READY,
    ACTIVE,
    SUSPENDED,
    DISABLED,
    ERROR
}

enum class ModuleCategory {
    CORE_BRAIN,
    VOICE_AND_AUDIO,
    SYSTEM_AUTOMATION,
    HARDWARE_AND_SENSORS,
    COMMUNICATION,
    RESEARCH_AND_WEB,
    MEMORY_AND_STORAGE,
    PROACTIVE_AI
}

/**
 * Base extension contract for all future Aiza modules.
 * Any new capability plugs into Aiza Core via this contract without modifying the core system.
 */
interface AizaModule {
    val id: String
    val name: String
    val description: String
    val version: String
    val category: ModuleCategory
    val status: ModuleStatus

    suspend fun initialize(): Boolean
    suspend fun shutdown()
}
