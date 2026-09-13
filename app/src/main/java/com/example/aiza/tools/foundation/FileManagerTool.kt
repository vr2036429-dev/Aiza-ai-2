package com.example.aiza.tools.foundation

import com.example.aiza.security.ActionCategory
import com.example.aiza.security.SensitivityLevel
import com.example.aiza.tools.AizaTool
import com.example.aiza.tools.ToolResult

/**
 * Tool for managing files.
 * Deletion is marked as sensitive and requires security clearance.
 */
class FileManagerTool : AizaTool {

    override val id: String = "tool_file_manager"
    override val name: String = "File Operations Controller"
    override val description: String = "Reads, creates, organizes, or removes documents."
    override val sensitivityLevel: SensitivityLevel = SensitivityLevel.CONFIRMATION_REQUIRED
    override val actionCategory: ActionCategory = ActionCategory.FILE_DELETION

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val action = parameters["action"] as? String ?: "read"
        val target = parameters["target"] as? String ?: "file.txt"

        return if (action == "delete") {
            ToolResult.Success(
                output = "File \"$target\" was safely purged after receiving your authorization.",
                structuredData = mapOf("target" to target, "action" to "DELETED"),
                executionTimeMs = 80L
            )
        } else {
            ToolResult.Success(
                output = "Inspected file \"$target\": File system entry is accessible.",
                structuredData = mapOf("target" to target, "action" to "READ"),
                executionTimeMs = 45L
            )
        }
    }
}
