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

/**
 * Dependency container configuring and wiring Aiza Core.
 */
object AizaContainer {

    @Volatile
    private var instance: AizaCore? = null

    fun getAizaCore(context: Context): AizaCore {
        return instance ?: synchronized(this) {
            instance ?: buildAizaCore(context.applicationContext).also { instance = it }
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
}
