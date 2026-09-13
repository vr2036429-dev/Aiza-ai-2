package com.example.aiza.intent

import com.example.aiza.core.model.Language
import com.example.aiza.core.model.UserRequest

/**
 * Interface for understanding user intent from natural language input.
 * Supports multi-language requests (English, Hindi, Hinglish, Bengali).
 */
interface IntentClassifier {
    suspend fun classify(request: UserRequest): IntentClassificationResult
}

class DefaultIntentClassifier : IntentClassifier {

    override suspend fun classify(request: UserRequest): IntentClassificationResult {
        val rawText = request.text.trim()
        val detectedLang = request.explicitLanguage ?: Language.detect(rawText)
        val textLower = rawText.lowercase()

        // 1. Communication check (Sending messages, calling)
        if (matchesPattern(textLower, listOf(
                "send message", "send a message", "message to", "sms to", "whatsapp", "call", "phone call",
                "message bhejo", "call karo", "phone lagao", "sms karo", "sandesh bhejo",
                "message pathao", "call koro", "phone koro", "barta pathao"
            ))) {
            val target = extractTarget(rawText) ?: "Contact"
            val body = extractContent(rawText) ?: "Hello from Asik"
            return IntentClassificationResult(
                intent = AizaIntent.Communication(target = target, message = body, channel = "message"),
                confidence = 0.92f,
                requiresTool = true,
                toolId = "tool_communication_send",
                toolParameters = mapOf("target" to target, "message" to body),
                extractedLanguageHint = detectedLang
            )
        }

        // 2. File Operation check (Delete, create, manage files)
        if (matchesPattern(textLower, listOf(
                "delete file", "remove file", "delete document", "file delete", "file hatao", "file erase",
                "file muche dao", "delete folder", "file create", "save file"
            ))) {
            val isDelete = textLower.contains("delete") || textLower.contains("hatao") || textLower.contains("muche")
            val targetFile = extractFileTarget(rawText)
            return IntentClassificationResult(
                intent = AizaIntent.FileOperation(
                    operation = if (isDelete) "delete" else "manage",
                    target = targetFile,
                    isDestructive = isDelete
                ),
                confidence = 0.90f,
                requiresTool = true,
                toolId = "tool_file_manager",
                toolParameters = mapOf("action" to (if (isDelete) "delete" else "read"), "target" to targetFile),
                extractedLanguageHint = detectedLang
            )
        }

        // 3. Reminder & Timer check
        if (matchesPattern(textLower, listOf(
                "remind me", "set reminder", "reminder to", "alarm", "timer",
                "yaad dilana", "yaad dila do", "reminder lagao", "ghanta bajao",
                "mone koriye dao", "reminder dao", "alarm dao"
            ))) {
            return IntentClassificationResult(
                intent = AizaIntent.Reminder(task = rawText),
                confidence = 0.88f,
                requiresTool = true,
                toolId = "tool_quick_reminder",
                toolParameters = mapOf("task" to rawText),
                extractedLanguageHint = detectedLang
            )
        }

        // 4. Device Action (Battery, system stats, settings)
        if (matchesPattern(textLower, listOf(
                "battery", "device status", "system status", "storage space", "battery kitni hai",
                "battery charge", "phone status", "device info", "battery koto", "charge koto"
            ))) {
            return IntentClassificationResult(
                intent = AizaIntent.DeviceAction(action = "get_device_status"),
                confidence = 0.95f,
                requiresTool = true,
                toolId = "tool_device_status",
                toolParameters = emptyMap(),
                extractedLanguageHint = detectedLang
            )
        }

        // 5. Web Research
        if (matchesPattern(textLower, listOf(
                "search for", "google for", "look up", "research", "web search",
                "khojo", "pata karo", "search karo", "dhundo",
                "khujo", "shondhan koro", "research koro"
            ))) {
            return IntentClassificationResult(
                intent = AizaIntent.WebResearch(topic = rawText),
                confidence = 0.85f,
                requiresTool = false,
                extractedLanguageHint = detectedLang
            )
        }

        // 6. Automation
        if (matchesPattern(textLower, listOf("automate", "routine", "run script", "daily routine", "subah ka routine"))) {
            return IntentClassificationResult(
                intent = AizaIntent.Automation(routineName = rawText),
                confidence = 0.85f,
                requiresTool = false,
                extractedLanguageHint = detectedLang
            )
        }

        // 7. Information Request vs Normal Conversation
        val isQuestion = rawText.endsWith("?") || matchesPattern(textLower, listOf(
            "what", "who", "when", "where", "why", "how", "explain", "tell me about",
            "kya", "kaun", "kab", "kahan", "kyun", "kaise", "batao",
            "ki", "ke", "kobe", "kothay", "kano", "kemon", "bolo"
        ))

        return if (isQuestion) {
            IntentClassificationResult(
                intent = AizaIntent.InformationRequest(query = rawText),
                confidence = 0.82f,
                requiresTool = false,
                extractedLanguageHint = detectedLang
            )
        } else {
            IntentClassificationResult(
                intent = AizaIntent.NormalConversation(topic = null),
                confidence = 0.80f,
                requiresTool = false,
                extractedLanguageHint = detectedLang
            )
        }
    }

    private fun matchesPattern(text: String, patterns: List<String>): Boolean {
        return patterns.any { text.contains(it) }
    }

    private fun extractTarget(text: String): String? {
        val lower = text.lowercase()
        val toIdx = lower.indexOf(" to ")
        if (toIdx != -1) {
            val afterTo = text.substring(toIdx + 4).trim()
            val words = afterTo.split(" ")
            if (words.isNotEmpty()) return words[0].filter { it.isLetter() }
        }
        val koIdx = lower.indexOf(" ko ")
        if (koIdx != -1) {
            val beforeKo = text.substring(0, koIdx).trim()
            val words = beforeKo.split(" ")
            if (words.isNotEmpty()) return words.last().filter { it.isLetter() }
        }
        val keIdx = lower.indexOf(" ke ")
        if (keIdx != -1) {
            val beforeKe = text.substring(0, keIdx).trim()
            val words = beforeKe.split(" ")
            if (words.isNotEmpty()) return words.last().filter { it.isLetter() }
        }
        return null
    }

    private fun extractContent(text: String): String? {
        val colonIdx = text.indexOf(":")
        if (colonIdx != -1 && colonIdx < text.length - 1) {
            return text.substring(colonIdx + 1).trim()
        }
        val quoteStart = text.indexOf('"')
        val quoteEnd = text.lastIndexOf('"')
        if (quoteStart != -1 && quoteEnd > quoteStart) {
            return text.substring(quoteStart + 1, quoteEnd).trim()
        }
        return text
    }

    private fun extractFileTarget(text: String): String {
        val words = text.split(" ")
        val fileWord = words.find { it.contains(".") && it.length > 3 }
        return fileWord ?: "document.pdf"
    }
}
