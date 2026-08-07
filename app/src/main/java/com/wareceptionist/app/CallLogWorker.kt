package com.wareceptionist.app

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.CallLog
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbxLRftndaH_znmmYtWfL9mmP9hoWXiPaBb8sOGBO5DPXZncXF4hX5akHaMgj8CEcMwW/exec"

    override suspend fun doWork(): Result {
        // Wait a few seconds for the Android system to finish writing to the Call Log
        delay(3000)
        
        try {
            val cursor: Cursor? = appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                    val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                    val id = it.getString(idIndex)
                    
                    val prefs = appContext.getSharedPreferences("CallCaptureLogs", Context.MODE_PRIVATE)
                    val lastProcessedCallId = prefs.getString("lastProcessedCallId", null)
                    
                    // Filter to prevent duplicate processing of the same call log entry
                    if (id != lastProcessedCallId) {
                        prefs.edit().putString("lastProcessedCallId", id).apply()
                        
                        val number = it.getString(numberIndex)
                        val typeCode = it.getInt(typeIndex)
                        val dateMillis = it.getLong(dateIndex)
                        val duration = it.getString(durationIndex)

                        val typeString = when (typeCode) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            CallLog.Calls.MISSED_TYPE -> "Missed"
                            CallLog.Calls.REJECTED_TYPE -> "Rejected"
                            else -> "Unknown ($typeCode)"
                        }

                        val dateObj = Date(dateMillis)
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                        AppLogger.log(appContext, "Found new call: $typeString from $number (Duration: $duration s)")

                        val messageText = if (typeCode == CallLog.Calls.REJECTED_TYPE || typeCode == CallLog.Calls.MISSED_TYPE) {
                            """
                            Welcome to UserX.in 👋

                            How can we help you today?

                            Reply with the number of the service you need:
                            1 — Discuss a New Project
                            2 — Get Customer Support
                            """.trimIndent()
                        } else {
                            ""
                        }
                        
                        val (leadId, _) = LeadIdManager.getOrCreateLeadId(appContext, number ?: "Unknown")

                        var finalMessageText = messageText
                        
                        val msgStatus = if (finalMessageText.isNotEmpty()) "Sent (Auto)" else "N/A"

                        val success = sendDataToSheets(
                            leadId = leadId,
                            phoneNumber = number ?: "Unknown",
                            callType = typeString,
                            dateStr = dateFormat.format(dateObj),
                            timeStr = timeFormat.format(dateObj),
                            duration = duration ?: "0",
                            messageText = finalMessageText,
                            messageStatus = msgStatus
                        )

                        // Fire WhatsApp Intent for Incoming/Missed calls
                        if (finalMessageText.isNotEmpty()) {
                            number?.let { num -> 
                                // Inject the bot's intro message into the AI's memory database
                                kotlinx.coroutines.runBlocking {
                                    val db = com.wareceptionist.app.db.AppDatabase.getDatabase(appContext).chatDao()
                                    db.insertSession(com.wareceptionist.app.db.ChatSession(num, System.currentTimeMillis()))
                                    db.insertMessage(com.wareceptionist.app.db.ChatMessage(
                                        sessionPhone = num, 
                                        role = "model", 
                                        content = finalMessageText, 
                                        timestamp = System.currentTimeMillis()
                                    ))
                                }
                                
                                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putLong("last_bot_reply_time", System.currentTimeMillis()).apply()
                                
                                sendWhatsAppIntro(num, finalMessageText) 
                            }
                        }
                        
                        if (!success) {
                            return Result.retry()
                        }
                    } else {
                        AppLogger.log(appContext, "Call ID $id already processed. Skipping.")
                    }
                } else {
                    AppLogger.log(appContext, "Could not find any call in Call Log.")
                }
            }
        } catch (e: Exception) {
            AppLogger.log(appContext, "Error reading call log: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }

    private fun sendWhatsAppIntro(phoneNumber: String, message: String) {
        try {
            val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            if (cleanNumber.length < 5) return

            val bannerUri = BannerHelper.getBannerUri(appContext)
            val digitsOnly = phoneNumber.filter { it.isDigit() }
            val jid = "$digitsOnly@s.whatsapp.net"

            val intent = if (bannerUri != null) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, bannerUri)
                    putExtra(Intent.EXTRA_TEXT, message)
                    putExtra("jid", jid)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage"
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            val isBusinessInstalled = try {
                appContext.packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e: Exception) { false }
            
            if (isBusinessInstalled) {
                intent.setPackage("com.whatsapp.w4b")
            } else {
                val isNormalInstalled = try {
                    appContext.packageManager.getPackageInfo("com.whatsapp", 0)
                    true
                } catch (e: Exception) { false }
                if (isNormalInstalled) intent.setPackage("com.whatsapp")
            }
            
            try {
                appContext.startActivity(intent)
                AppLogger.log(appContext, "🤖 Phase 1: Opened WhatsApp with Banner Image to message $cleanNumber")
            } catch (e: android.content.ActivityNotFoundException) {
                val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage"
                val fallbackIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallbackIntent)
                AppLogger.log(appContext, "🤖 Phase 1: Opened WhatsApp via Browser Fallback")
            }
        } catch (e: Exception) {
            AppLogger.log(appContext, "❌ Failed to open WhatsApp: ${e.message}")
        }
    }

    private fun sendDataToSheets(leadId: String, phoneNumber: String, callType: String, dateStr: String, timeStr: String, duration: String, messageText: String, messageStatus: String): Boolean {
        return try {
            val url = java.net.URL(WEB_APP_URL)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            
            val json = org.json.JSONObject().apply {
                put("leadId", leadId)
                put("phoneNumber", phoneNumber)
                put("callType", callType)
                put("date", dateStr)
                put("time", timeStr)
                put("duration", duration)
                put("messageText", messageText)
                put("messageStatus", messageStatus)
            }
            
            val outputStream = OutputStreamWriter(connection.outputStream)
            outputStream.write(json.toString())
            outputStream.flush()
            outputStream.close()
            
            val responseCode = connection.responseCode
            val responseMsg = connection.inputStream.bufferedReader().use { it.readText() }
            
            AppLogger.log(appContext, "📝 Sent to Sheets. Code: $responseCode, Response: $responseMsg")
            responseCode in 200..299
        } catch (e: Exception) {
            AppLogger.log(appContext, "❌ Network Error sending data: ${e.message}. Will retry.")
            false
        }
    }
}
