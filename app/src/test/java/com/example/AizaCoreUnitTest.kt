package com.example

import com.example.aiza.brain.AIProvider
import com.example.aiza.brain.AIProviderRegistry
import com.example.aiza.brain.AIRequest
import com.example.aiza.brain.AIResponse
import com.example.aiza.core.DefaultAizaCore
import com.example.aiza.core.model.Language
import com.example.aiza.core.model.UserRequest
import com.example.aiza.diagnostics.DiagnosticLogger
import com.example.aiza.intent.AizaIntent
import com.example.aiza.intent.DefaultIntentClassifier
import com.example.aiza.memory.DefaultMemoryStore
import com.example.aiza.modules.ModuleRegistry
import com.example.aiza.orchestrator.DefaultAizaOrchestrator
import com.example.aiza.response.DefaultResponseManager
import com.example.aiza.security.ActionCategory
import com.example.aiza.security.DefaultSecurityLayer
import com.example.aiza.security.SecurityDecision
import com.example.aiza.tools.DefaultToolRouter
import com.example.aiza.tools.foundation.QuickReminderTool
import com.example.aiza.tools.foundation.SendMessageTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AizaCoreUnitTest {

    @Test
    fun testLanguageAutoDetection() {
        assertEquals(Language.ENGLISH, Language.detect("What is the status of the system?"))
        assertEquals(Language.BENGALI, Language.detect("কেমন আছো আইজা? সবকিছু ঠিক আছে?"))
        assertEquals(Language.HINDI, Language.detect("आज का मौसम कैसा रहेगा?"))
        assertEquals(Language.HINGLISH, Language.detect("Kaisa chal raha hai Aiza?"))
    }

    @Test
    fun testIntentClassification() = runBlocking {
        val classifier = DefaultIntentClassifier()

        val commRequest = UserRequest(text = "Send message to Rahul: I will reach soon")
        val commResult = classifier.classify(commRequest)
        assertTrue(commResult.intent is AizaIntent.Communication)
        assertTrue(commResult.requiresTool)
        assertEquals("tool_communication_send", commResult.toolId)

        val fileRequest = UserRequest(text = "Delete file confidential.pdf")
        val fileResult = classifier.classify(fileRequest)
        assertTrue(fileResult.intent is AizaIntent.FileOperation)
        assertTrue(fileResult.requiresTool)

        val reminderRequest = UserRequest(text = "Set reminder to buy groceries")
        val reminderResult = classifier.classify(reminderRequest)
        assertTrue(reminderResult.intent is AizaIntent.Reminder)
    }

    @Test
    fun testSecurityLayerRequiresConfirmationForSensitiveActions() {
        val logger = DiagnosticLogger()
        val securityLayer = DefaultSecurityLayer(logger)

        val decision = securityLayer.evaluateAction(
            category = ActionCategory.COMMUNICATION_SEND,
            target = "Rahul",
            description = "Send message",
            payload = "Hello",
            isOriginExternal = false
        )

        assertTrue(decision is SecurityDecision.RequiresConfirmation)
    }

    @Test
    fun testSecurityLayerRejectsUntrustedExternalTriggers() {
        val logger = DiagnosticLogger()
        val securityLayer = DefaultSecurityLayer(logger)

        val decision = securityLayer.evaluateAction(
            category = ActionCategory.COMMUNICATION_SEND,
            target = "Rahul",
            description = "Send message",
            payload = "Hello",
            isOriginExternal = true // Untrusted external content
        )

        assertTrue(decision is SecurityDecision.Denied)
    }

    @Test
    fun testEndToEndAizaCoreOrchestration() = runBlocking {
        val logger = DiagnosticLogger()
        val securityLayer = DefaultSecurityLayer(logger)
        val toolRouter = DefaultToolRouter(securityLayer, logger)
        val reminderTool = QuickReminderTool()
        val messageTool = SendMessageTool()
        toolRouter.registerTool(reminderTool)
        toolRouter.registerTool(messageTool)

        val dummyAiProvider = object : AIProvider {
            override val id: String = "test_provider"
            override val displayName: String = "Test Provider"
            override val isConfigured: Boolean = true
            override suspend fun generateResponse(request: AIRequest): Result<AIResponse> {
                return Result.success(
                    AIResponse(
                        content = "Understood, Asik Sir.",
                        detectedLanguage = request.targetLanguage,
                        modelName = "TestModel"
                    )
                )
            }
            override suspend fun ping(): Boolean = true
        }

        val aiRegistry = AIProviderRegistry(dummyAiProvider)
        val memoryStore = DefaultMemoryStore()
        val moduleRegistry = ModuleRegistry(logger)
        val intentClassifier = DefaultIntentClassifier()
        val responseManager = DefaultResponseManager()

        val orchestrator = DefaultAizaOrchestrator(
            intentClassifier = intentClassifier,
            toolRouter = toolRouter,
            securityLayer = securityLayer,
            aiProviderRegistry = aiRegistry,
            responseManager = responseManager,
            logger = logger
        )

        val core = DefaultAizaCore(
            orchestrator = orchestrator,
            toolRouter = toolRouter,
            securityLayer = securityLayer,
            memory = memoryStore,
            moduleRegistry = moduleRegistry,
            aiProviderRegistry = aiRegistry,
            logger = logger
        )

        // Test normal conversation
        val response1 = core.dispatchUserRequest(UserRequest(text = "Hello Aiza"))
        assertNotNull(response1.text)
        assertTrue(response1.text.contains("Asik") || response1.text.contains("Sir"))

        // Test safe tool execution (Reminder)
        val response2 = core.dispatchUserRequest(UserRequest(text = "Set reminder to prepare meeting notes"))
        assertNotNull(response2.toolResult)
        assertTrue(response2.toolResult?.isSuccess == true)

        // Test sensitive tool execution (Requires Confirmation)
        val response3 = core.dispatchUserRequest(UserRequest(text = "Send message to John: Project status is ready"))
        assertTrue(response3.requiresConfirmation)
        assertNotNull(response3.pendingActionId)

        // Authorize action
        val authorizedResponse = core.authorizeSensitiveAction(response3.pendingActionId!!, approved = true)
        assertNotNull(authorizedResponse)
        assertTrue(authorizedResponse?.toolResult?.isSuccess == true)
    }

    @Test
    fun testGeminiAIProviderConformsToAIProvider() = runBlocking {
        val logger = DiagnosticLogger()
        // Instantiate GeminiAIProvider directly
        val provider: AIProvider = com.example.aiza.brain.GeminiAIProvider(logger = logger)

        assertNotNull(provider.id)
        assertNotNull(provider.displayName)
        assertTrue(provider.displayName.contains("Gemini"))

        // Test request generation through the AIProvider interface
        val request = AIRequest(
            prompt = "Hello Aiza",
            targetLanguage = Language.ENGLISH,
            userName = "Asik"
        )
        val result = provider.generateResponse(request)
        assertTrue(result.isSuccess)

        val response = result.getOrNull()
        assertNotNull(response)
        assertNotNull(response?.content)
        assertTrue(response?.content?.isNotBlank() == true)
    }
}
