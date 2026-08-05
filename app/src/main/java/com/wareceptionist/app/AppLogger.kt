package com.wareceptionist.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val PREFS_NAME = "CallCaptureLogs"
    private const val KEY_LOGS = "logs"

    fun log(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLogs = prefs.getString(KEY_LOGS, "") ?: ""
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = "[$timestamp] $message\n"
        
        val updatedLogs = newLog + currentLogs
        
        // Keep logs from getting too long (keep first 20000 chars)
        val truncatedLogs = if (updatedLogs.length > 20000) updatedLogs.substring(0, 20000) else updatedLogs
        
        prefs.edit().putString(KEY_LOGS, truncatedLogs).apply()
    }

    fun getLogs(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGS, "No logs yet.")?.takeIf { it.isNotEmpty() } ?: "No logs yet."
    }

    fun getRecentLogs(context: Context): String {
        val allLogs = getLogs(context)
        if (allLogs == "No logs yet.") return allLogs

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Fallback to yesterday if no logs today
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        val lines = allLogs.split("\n")
        
        val todayLogs = lines.filter { it.startsWith("[$todayDate") }
        if (todayLogs.isNotEmpty()) {
            return todayLogs.joinToString("\n").replace("[$todayDate ", "[")
        }

        val yesterdayLogs = lines.filter { it.startsWith("[$yesterdayDate") }
        if (yesterdayLogs.isNotEmpty()) {
            return yesterdayLogs.joinToString("\n").replace("[$yesterdayDate ", "[YEST ")
        }

        return "No recent activity."
    }

    fun clearLogs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LOGS, "").apply()
    }
}
