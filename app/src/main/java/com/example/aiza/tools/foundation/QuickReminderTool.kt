package com.example.aiza.tools.foundation

import com.example.aiza.security.ActionCategory
import com.example.aiza.security.SensitivityLevel
import com.example.aiza.tools.AizaTool
import com.example.aiza.tools.ToolResult

/**
 * Tool for managing immediate reminders and tasks for Asik.
 */
class QuickReminderTool : AizaTool {

    override val id: String = "tool_quick_reminder"
    override val name: String = "Quick Reminder Engine"
    override val description: String = "Schedules personal reminders and notifications for Asik."
    override val sensitivityLevel: SensitivityLevel = SensitivityLevel.SAFE
    override val actionCategory: ActionCategory = ActionCategory.CUSTOM_SENSITIVE

    private val reminders = mutableListOf<String>()

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        val task = parameters["task"] as? String ?: return ToolResult.Failure(
            errorMessage = "No reminder task description provided.",
            recoverySuggestion = "Specify what you would like to be reminded about."
        )

        reminders.add(task)

        return ToolResult.Success(
            output = "Reminder recorded successfully: \"$task\". I will keep this actively queued for you, Sir.",
            structuredData = mapOf("task" to task, "totalActive" to reminders.size),
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    fun getActiveReminders(): List<String> = reminders.toList()
}
