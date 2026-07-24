package com.wareceptionist.app

import com.wareceptionist.app.db.AppDatabase
import com.wareceptionist.app.db.ChatSession
import com.wareceptionist.app.db.ChatMessage
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogService : Service() {

    private val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbxLRftndaH_znmmYtWfL9mmP9hoWXiPaBb8sOGBO5DPXZncXF4hX5akHaMgj8CEcMwW/exec"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "CallCaptureChannel"

    private var lastProcessedCallId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Capture Bot")
            .setContentText("Processing call details...")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)

        // Wait a few seconds for the Android system to finish writing to the Call Log
        Handler(Looper.getMainLooper()).postDelayed({
            processLatestCall()
        }, 3000)

        return START_NOT_STICKY
    }

    private fun processLatestCall() {
        Thread {
            try {
                val cursor: Cursor? = contentResolver.query(
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
                        
                        // Filter removed for testing: always process so second calls from same number get the WhatsApp text
                        if (true) { // if (id != lastProcessedCallId) {
                            lastProcessedCallId = id
                            
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

                            AppLogger.log(this, "Found new call: $typeString from $number (Duration: $duration s)")

                            val leadId = "L-" + System.currentTimeMillis()
                            
                            sendDataToSheets(
                                leadId = leadId,
                                phoneNumber = number ?: "Unknown",
                                callType = typeString,
                                dateStr = dateFormat.format(dateObj),
                                timeStr = timeFormat.format(dateObj),
                                duration = duration ?: "0"
                            )
                            
                            number?.let { phone ->
                                try {
                                    val db = AppDatabase.getDatabase(this@CallLogService).chatDao()
                                    db.insertSession(ChatSession(phone, System.currentTimeMillis()))
                                    db.insertMessage(ChatMessage(sessionPhone = phone, role = "model", content = "System: Call received. lead_id=$leadId", timestamp = System.currentTimeMillis()))
                                    AppLogger.log(this@CallLogService, "Saved lead_id to local chat db for $phone")
                                } catch (e: Exception) {
                                    AppLogger.log(this@CallLogService, "Failed to save lead_id to local db: ${e.message}")
                                }
                            }

                            // Fire WhatsApp Intent for Missed/Rejected calls
                            if (typeCode == CallLog.Calls.REJECTED_TYPE || typeCode == CallLog.Calls.MISSED_TYPE) {
                                number?.let { sendWhatsAppIntro(it, leadId) }
                            }
                        } else {
                            AppLogger.log(this, "Call ID $id already processed. Skipping.")
                        }
                    } else {
                        AppLogger.log(this, "Could not find any call in Call Log.")
                    }
                }
            } catch (e: Exception) {
                AppLogger.log(this, "Error reading call log: ${e.message}")
            } finally {
                stopForeground(true)
                stopSelf()
            }
        }.start()
    }

    private fun sendWhatsAppIntro(phoneNumber: String, leadId: String) {
        try {
            // Basic sanitization: keep '+' and digits
            val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            if (cleanNumber.length < 5) return

            val message = """
                Hey! 👋 Welcome to UserXpert.

                We received your call. Let us know what you're looking for, and we'll be happy to help.

                How can we help? 
                Reply with number of the service you need
                
                1. Promo Website - Requirement
                2. Customer care - Raise ticket
            """.trimIndent()
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage"
            
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val isBusinessInstalled = try {
                packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e: Exception) { false }
            
            if (isBusinessInstalled) {
                intent.setPackage("com.whatsapp.w4b")
            } else {
                val isNormalInstalled = try {
                    packageManager.getPackageInfo("com.whatsapp", 0)
                    true
                } catch (e: Exception) { false }
                if (isNormalInstalled) intent.setPackage("com.whatsapp")
            }
            
            startActivity(intent)
            AppLogger.log(this, "🤖 Phase 1: Opened WhatsApp to message $cleanNumber")
        } catch (e: Exception) {
            AppLogger.log(this, "❌ Failed to open WhatsApp: ${e.message}")
        }
    }

    private fun sendDataToSheets(leadId: String, phoneNumber: String, callType: String, dateStr: String, timeStr: String, duration: String) {
        try {
            val url = URL(WEB_APP_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            
            val json = JSONObject().apply {
                put("leadId", leadId)
                put("phoneNumber", phoneNumber)
                put("callType", callType)
                put("date", dateStr)
                put("time", timeStr)
                put("duration", duration)
            }
            
            val outputStream = OutputStreamWriter(connection.outputStream)
            outputStream.write(json.toString())
            outputStream.flush()
            outputStream.close()
            
            val responseCode = connection.responseCode
            val responseMsg = connection.inputStream.bufferedReader().use { it.readText() }
            
            AppLogger.log(this, "📝 Sent to Sheets. Code: $responseCode, Response: $responseMsg")
            
        } catch (e: Exception) {
            AppLogger.log(this, "❌ Network Error sending data: ${e.message}. (We will add a retry queue in Phase 2!)")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Capture Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
