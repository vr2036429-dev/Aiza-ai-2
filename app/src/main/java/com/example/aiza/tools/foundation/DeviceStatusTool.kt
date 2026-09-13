package com.example.aiza.tools.foundation

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.example.aiza.security.ActionCategory
import com.example.aiza.security.SensitivityLevel
import com.example.aiza.tools.AizaTool
import com.example.aiza.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tool for safely retrieving device state, battery, and system metrics.
 */
class DeviceStatusTool(
    private val context: Context
) : AizaTool {

    override val id: String = "tool_device_status"
    override val name: String = "Device Status Inspector"
    override val description: String = "Inspects device battery, Android version, and system time."
    override val sensitivityLevel: SensitivityLevel = SensitivityLevel.SAFE
    override val actionCategory: ActionCategory = ActionCategory.SYSTEM_SETTING_CHANGE

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 88
            val isCharging = bm?.isCharging ?: false

            val timeStr = SimpleDateFormat("hh:mm a, EEEE, MMMM d", Locale.getDefault()).format(Date())
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val model = "${Build.MANUFACTURER} ${Build.MODEL}"

            val output = buildString {
                append("Battery: $batteryLevel% (${if (isCharging) "Charging" else "On Battery"})\n")
                append("Device: $model\n")
                append("System Time: $timeStr\n")
                append("Platform: $osVersion")
            }

            ToolResult.Success(
                output = output,
                structuredData = mapOf(
                    "batteryLevel" to batteryLevel,
                    "isCharging" to isCharging,
                    "time" to timeStr,
                    "model" to model
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolResult.Failure(
                errorMessage = "Failed to query device state: ${e.localizedMessage ?: "Unknown hardware error"}",
                recoverySuggestion = "Verify system service availability."
            )
        }
    }
}
