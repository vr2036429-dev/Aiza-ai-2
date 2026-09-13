package com.example.aiza.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class MemoryType {
    PREFERENCE,
    ROUTINE,
    CONVERSATION_CONTEXT,
    USER_INSTRUCTION,
    LONG_TERM_GOAL
}

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Memory abstraction for Aiza.
 * Allows storing, retrieving, and searching user preferences, context, and instructions.
 * Designed so a full Room / Vector DB can replace or back it in later stages.
 */
interface MemoryInterface {
    suspend fun saveMemory(type: MemoryType, key: String, value: String)
    suspend fun getMemory(type: MemoryType, key: String): MemoryEntry?
    suspend fun getMemoriesByType(type: MemoryType): List<MemoryEntry>
    suspend fun getAllMemories(): List<MemoryEntry>
    suspend fun deleteMemory(id: String): Boolean
    val memories: StateFlow<List<MemoryEntry>>
}

class DefaultMemoryStore : MemoryInterface {

    private val _memories = MutableStateFlow<List<MemoryEntry>>(
        listOf(
            MemoryEntry(
                type = MemoryType.PREFERENCE,
                key = "user_name",
                value = "Asik"
            ),
            MemoryEntry(
                type = MemoryType.PREFERENCE,
                key = "assistant_persona",
                value = "Calm, intelligent, confident, slightly witty"
            ),
            MemoryEntry(
                type = MemoryType.PREFERENCE,
                key = "primary_languages",
                value = "English, Hindi, Hinglish, Bengali"
            ),
            MemoryEntry(
                type = MemoryType.USER_INSTRUCTION,
                key = "address_style",
                value = "Address Asik respectfully as Sir or Boss occasionally when fitting"
            ),
            MemoryEntry(
                type = MemoryType.LONG_TERM_GOAL,
                key = "assistant_evolution",
                value = "Expand Aiza modularly with voice, automation, and screen reading"
            )
        )
    )
    override val memories: StateFlow<List<MemoryEntry>> = _memories.asStateFlow()

    override suspend fun saveMemory(type: MemoryType, key: String, value: String) {
        val current = _memories.value.filterNot { it.type == type && it.key == key }
        val newEntry = MemoryEntry(type = type, key = key, value = value)
        _memories.value = current + newEntry
    }

    override suspend fun getMemory(type: MemoryType, key: String): MemoryEntry? {
        return _memories.value.find { it.type == type && it.key == key }
    }

    override suspend fun getMemoriesByType(type: MemoryType): List<MemoryEntry> {
        return _memories.value.filter { it.type == type }
    }

    override suspend fun getAllMemories(): List<MemoryEntry> {
        return _memories.value
    }

    override suspend fun deleteMemory(id: String): Boolean {
        val exists = _memories.value.any { it.id == id }
        if (exists) {
            _memories.value = _memories.value.filterNot { it.id == id }
        }
        return exists
    }
}
