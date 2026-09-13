package com.example.aiza.modules.contracts

import com.example.aiza.modules.AizaModule
import com.example.aiza.modules.ModuleCategory
import com.example.aiza.modules.ModuleStatus

/**
 * 1. Natural Voice Assistant Module
 */
interface NaturalVoiceAssistantModule : AizaModule {
    suspend fun startListening()
    suspend fun stopListening()
    suspend fun speak(text: String, languageCode: String)
}

/**
 * 2. Background Operation Module
 */
interface BackgroundOperationModule : AizaModule {
    fun scheduleWorker(taskName: String, intervalSeconds: Long)
    fun cancelWorker(taskName: String)
}

/**
 * 3. Wake Word Module (e.g. "Hey Aiza")
 */
interface WakeWordModule : AizaModule {
    fun startWakeWordDetection(keyword: String = "Hey Aiza", onDetected: () -> Unit)
    fun stopWakeWordDetection()
}

/**
 * 4. 3D Floating Avatar Module
 */
interface FloatingAvatar3DModule : AizaModule {
    fun showOverlay()
    fun hideOverlay()
    fun setAnimationState(state: String) // "idle", "listening", "speaking", "thinking"
}

/**
 * 5. Android Automation Module (Accessibility / UI automation)
 */
interface AndroidAutomationModule : AizaModule {
    suspend fun clickNode(accessibilityId: String): Boolean
    suspend fun scroll(direction: String): Boolean
    suspend fun enterText(accessibilityId: String, text: String): Boolean
}

/**
 * 6. Screen Reading Module
 */
interface ScreenReadingModule : AizaModule {
    suspend fun captureCurrentScreenContext(): String?
}

/**
 * 7. Web Search and Research Module
 */
interface WebSearchResearchModule : AizaModule {
    suspend fun searchWeb(query: String): List<String>
    suspend fun summarizeUrl(url: String): String
}

/**
 * 8. File Management Module
 */
interface FileManagementModule : AizaModule {
    suspend fun listFiles(path: String): List<String>
    suspend fun readFile(path: String): String?
    suspend fun writeFile(path: String, content: String): Boolean
    suspend fun deleteFile(path: String): Boolean
}

/**
 * 9. Calls and Messaging Module
 */
interface CallsAndMessagingModule : AizaModule {
    suspend fun initiateCall(phoneNumber: String): Boolean
    suspend fun sendSms(phoneNumber: String, message: String): Boolean
}

/**
 * 10. Notification Intelligence Module
 */
interface NotificationIntelligenceModule : AizaModule {
    suspend fun triageNotification(packageName: String, title: String, text: String): String
}

/**
 * 11. Smart Automation Module
 */
interface SmartAutomationModule : AizaModule {
    fun registerRoutine(name: String, trigger: String, actions: List<String>)
    fun triggerRoutine(name: String)
}

/**
 * 12. Reminders and Alarms Module
 */
interface RemindersAlarmsModule : AizaModule {
    suspend fun setAlarm(hour: Int, minute: Int, label: String)
    suspend fun setReminder(timestamp: Long, text: String)
}

/**
 * 13. Long-Term Memory Module
 */
interface LongTermMemoryModule : AizaModule {
    suspend fun storeVector(key: String, embedding: FloatArray, metadata: Map<String, String>)
    suspend fun querySimilar(embedding: FloatArray, topK: Int): List<String>
}

/**
 * 14. Multiple AI Connectors Module
 */
interface MultipleAIConnectorsModule : AizaModule {
    fun registerConnector(id: String, endpoint: String)
    fun routeQuery(preferredModel: String, query: String)
}

/**
 * 15. Proactive Intelligence Module
 */
interface ProactiveIntelligenceModule : AizaModule {
    suspend fun evaluateContextAndSuggest(): String?
}
