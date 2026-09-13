package com.example.aiza.core

import android.content.Context
import com.example.BuildConfig
import com.example.aiza.brain.AIProviderRegistry
import com.example.aiza.brain.GeminiAIProvider
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.intent.DefaultIntentClassifier
import com.example.aiza.memory.DefaultMemoryStore
import com.example.aiza.modules.ModuleRegistry
import com.example.aiza.orchestrator.DefaultAizaOrchestrator
import com.example.aiza.response.DefaultResponseManager
import com.example.aiza.security.DefaultSecurityLayer
import com.example.aiza.tools.DefaultToolRouter
import com.example.aiza.tools.foundation.DeviceStatusTool
import com.example.aiza.tools.foundation.FileManagerTool
import com.example.aiza.tools.foundation.QuickReminderTool
import com.example.aiza.tools.foundation.SendMessageTool
import com.example.aiza.voice.DefaultNaturalVoiceAssistantModule
import com.example.aiza.voice.VoiceSettingsRepository
import com.example.aiza.voice.VoiceStateManager
import com.example.aiza.voice.input.AndroidSpeechRecognitionEngine
import com.example.aiza.voice.input.VoiceInputManager
import com.example.aiza.voice.output.AndroidTextToSpeechEngine
import com.example.aiza.voice.output.VoiceOutputManager
import com.example.aiza.voice.wakeword.FoundationWakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dependency container configuring and wiring Aiza Core and modular extensions.
 */
object AizaContainer {

    @Volatile
    private var instance: AizaCore? = null

    @Volatile
    private var voiceModuleInstance: DefaultNaturalVoiceAssistantModule? = null

    fun getAizaCore(context: Context): AizaCore {
        return instance ?: synchronized(this) {
            instance ?: buildAizaCore(context.applicationContext).also { instance = it }
        }
    }

    fun getVoiceAssistant(context: Context): DefaultNaturalVoiceAssistantModule {
        return voiceModuleInstance ?: synchronized(this) {
            voiceModuleInstance ?: buildVoiceAssistant(context.applicationContext, getAizaCore(context)).also {
                voiceModuleInstance = it
            }
        }
    }

    private fun buildAizaCore(appContext: Context): AizaCore {
        val logger = DiagnosticLogger()
        val securityLayer = DefaultSecurityLayer(logger)
        val toolRouter = DefaultToolRouter(securityLayer, logger)

        // Register Foundation Tools
        toolRouter.registerTool(DeviceStatusTool(appContext))
        toolRouter.registerTool(QuickReminderTool())
        toolRouter.registerTool(SendMessageTool())
        toolRouter.registerTool(FileManagerTool())

        val memoryStore = DefaultMemoryStore()
        val moduleRegistry = ModuleRegistry(logger)

        // Setup AI Provider with Gemini
        val geminiProvider = GeminiAIProvider(
            apiKeyProvider = {
                try {
                    BuildConfig.GEMINI_API_KEY
                } catch (e: Throwable) {
                    ""
                }
            },
            logger = logger
        )
        val aiProviderRegistry = AIProviderRegistry(geminiProvider)

        val intentClassifier = DefaultIntentClassifier()
        val responseManager = DefaultResponseManager()

        val orchestrator = DefaultAizaOrchestrator(
            intentClassifier = intentClassifier,
            toolRouter = toolRouter,
            securityLayer = securityLayer,
            aiProviderRegistry = aiProviderRegistry,
            responseManager = responseManager,
            logger = logger
        )

        return DefaultAizaCore(
            orchestrator = orchestrator,
            toolRouter = toolRouter,
            securityLayer = securityLayer,
            memory = memoryStore,
            moduleRegistry = moduleRegistry,
            aiProviderRegistry = aiProviderRegistry,
            logger = logger
        )
    }

    private fun buildVoiceAssistant(appContext: Context, core: AizaCore): DefaultNaturalVoiceAssistantModule {
        val logger = core.logger
        val stateManager = VoiceStateManager(logger)
        val ttsEngine = AndroidTextToSpeechEngine(appContext, logger)
        val voiceOutputManager = VoiceOutputManager(ttsEngine, stateManager, logger)
        val speechEngine = AndroidSpeechRecognitionEngine(appContext, logger)
        val voiceInputManager = VoiceInputManager(appContext, speechEngine, stateManager, logger)
        val wakeWordEngine = FoundationWakeWordEngine(logger)
        val settingsRepo = VoiceSettingsRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val module = DefaultNaturalVoiceAssistantModule(
            voiceInputManager = voiceInputManager,
            voiceOutputManager = voiceOutputManager,
            stateManager = stateManager,
            wakeWordEngine = wakeWordEngine,
            settingsRepository = settingsRepo,
            aizaCoreProvider = { core },
            logger = logger,
            scope = scope
        )

        // Register into core ModuleRegistry
        core.moduleRegistry.registerModule(module)

        // Initialize asynchronously
        scope.launch {
            module.initialize()
        }

        return module
    }
}
