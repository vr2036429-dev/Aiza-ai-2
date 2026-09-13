package com.example.aiza.response

import com.example.aiza.core.model.Language
import com.example.aiza.intent.AizaIntent
import com.example.aiza.tools.ToolResult

/**
 * Produces polished, natural, concise, context-aware responses in the user's language
 * (English, Hindi, Hinglish, Bengali), maintaining Aiza's calm, confident, slightly witty persona.
 */
interface ResponseManager {
    fun formatResponse(
        userName: String,
        language: Language,
        intent: AizaIntent,
        rawAIContent: String?,
        toolResult: ToolResult?
    ): String
}

class DefaultResponseManager : ResponseManager {

    override fun formatResponse(
        userName: String,
        language: Language,
        intent: AizaIntent,
        rawAIContent: String?,
        toolResult: ToolResult?
    ): String {
        // If a tool was executed, synthesize its outcome directly and naturally
        if (toolResult != null) {
            return formatToolOutcome(userName, language, toolResult)
        }

        // If raw AI response is present, return it cleaned up
        if (!rawAIContent.isNullOrBlank()) {
            return rawAIContent.trim()
        }

        // Default natural fallback
        return when (language) {
            Language.BENGALI -> "আমি প্রস্তুত, $userName Sir। আপনার পরবর্তী নির্দেশ বলুন।"
            Language.HINDI -> "मैं तैयार हूँ, $userName Sir। अपना अगला आदेश बताइए।"
            Language.HINGLISH -> "Main ready hoon, $userName Boss. Bataiye aage kya karna hai."
            else -> "Ready when you are, $userName Sir. What shall we tackle next?"
        }
    }

    private fun formatToolOutcome(
        userName: String,
        language: Language,
        toolResult: ToolResult
    ): String {
        return when (toolResult) {
            is ToolResult.Success -> {
                when (language) {
                    Language.BENGALI -> "${toolResult.output}\n\nকাজটি সফলভাবে সম্পন্ন হয়েছে, $userName Boss।"
                    Language.HINDI -> "${toolResult.output}\n\nकाम पूरा हो गया है, $userName Sir।"
                    Language.HINGLISH -> "${toolResult.output}\n\nTask smoothly complete ho gaya hai, $userName Boss."
                    else -> "${toolResult.output}\n\nAction executed successfully, $userName Sir."
                }
            }
            is ToolResult.Failure -> {
                // Never fabricate or pretend it succeeded
                val suggestion = toolResult.recoverySuggestion?.let { "\nSuggestion: $it" } ?: ""
                when (language) {
                    Language.BENGALI -> "ত্রুটি ঘটেছে, $userName: ${toolResult.errorMessage}$suggestion"
                    Language.HINDI -> "कार्रवाई पूरी नहीं हो सकी, $userName: ${toolResult.errorMessage}$suggestion"
                    Language.HINGLISH -> "Action complete nahi ho paya, $userName Boss: ${toolResult.errorMessage}$suggestion"
                    else -> "Action could not be completed, $userName: ${toolResult.errorMessage}$suggestion"
                }
            }
            is ToolResult.RequiresConfirmation -> {
                when (language) {
                    Language.BENGALI -> "নিরাপত্তা নিশ্চিতকরণ প্রয়োজন, $userName Boss: ${toolResult.prompt} (টার্গেট: ${toolResult.target})"
                    Language.HINDI -> "सुरक्षा अनुमति आवश्यक है, $userName Sir: ${toolResult.prompt} (लक्ष्य: ${toolResult.target})"
                    Language.HINGLISH -> "Security authorization chahiye, $userName Boss: ${toolResult.prompt} (Target: ${toolResult.target})"
                    else -> "Security clearance required, $userName Boss: ${toolResult.prompt} (Target: ${toolResult.target})"
                }
            }
        }
    }
}
