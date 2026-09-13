package com.example.aiza.core.model

/**
 * Supported natural languages for Aiza.
 * Aiza automatically understands the user's language and responds in the appropriate language.
 */
enum class Language(val displayName: String, val code: String) {
    ENGLISH("English", "en"),
    HINDI("Hindi (हिन्दी)", "hi"),
    HINGLISH("Hinglish", "hi-en"),
    BENGALI("Bengali (বাংলা)", "bn"),
    UNKNOWN("Auto-Detect", "auto");

    companion object {
        fun detect(text: String): Language {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return ENGLISH

            // Bengali Unicode range: \u0980-\u09FF
            val bengaliCharCount = trimmed.count { it in '\u0980'..'\u09FF' }
            if (bengaliCharCount > 0 && bengaliCharCount > trimmed.length * 0.15) {
                return BENGALI
            }

            // Devanagari Unicode range: \u0900-\u097F
            val devanagariCharCount = trimmed.count { it in '\u0900'..'\u097F' }
            if (devanagariCharCount > 0 && devanagariCharCount > trimmed.length * 0.15) {
                return HINDI
            }

            // Hinglish patterns (Hindi written in Latin script)
            val lower = trimmed.lowercase()
            val hinglishKeywords = listOf(
                "kya", "kaise", "kaisa", "batao", "bataiye", "karo", "kardo", "hai", "hain", "karna",
                "samay", "aaj", "kal", "kyun", "mera", "meri", "mere", "apna", "shukriya", "dhanyawad",
                "namaste", "haal", "kaam", "thik", "theek", "bhai", "yaar", "madad"
            )
            val hinglishMatchCount = hinglishKeywords.count { lower.contains(Regex("\\b$it\\b")) }
            if (hinglishMatchCount >= 1) {
                return HINGLISH
            }

            // Bengali written in Latin script (Banglish)
            val banglishKeywords = listOf(
                "kemon", "acho", "achhen", "ki", "korcho", "korchen", "bolo", "bolun", "bhalo", "dhonnobad",
                "amar", "amake", "ekhoni", "ajke", "somoy", "shomoy"
            )
            val banglishMatchCount = banglishKeywords.count { lower.contains(Regex("\\b$it\\b")) }
            if (banglishMatchCount >= 1) {
                return BENGALI
            }

            return ENGLISH
        }
    }
}
