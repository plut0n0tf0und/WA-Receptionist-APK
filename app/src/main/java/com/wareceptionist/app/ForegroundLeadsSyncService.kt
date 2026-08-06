package com.wareceptionist.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ForegroundLeadsSyncService : Service() {

    private val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbxLRftndaH_znmmYtWfL9mmP9hoWXiPaBb8sOGBO5DPXZncXF4hX5akHaMgj8CEcMwW/exec"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "SYNC_CHANNEL_ID")
            .setContentTitle("Enquiry Auto Responder")
            .setContentText("Syncing new leads continuously...")
            .setSmallIcon(R.mipmap.ic_launcher) // Assumes standard icon exists
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPollingLoop()
        return START_STICKY
    }

    private fun startPollingLoop() {
        scope.launch {
            while (isActive) {
                try {
                    var currentUrl = URL(WEB_APP_URL)
                    var connection = currentUrl.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    
                    val json = JSONObject().apply {
                        put("action", "get_new_leads")
                    }
                    
                    val outputStream = OutputStreamWriter(connection.outputStream)
                    outputStream.write(json.toString())
                    outputStream.flush()
                    outputStream.close()
                    
                    var responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                        responseCode == 307 || responseCode == 308) {
                        
                        val redirectUrl = connection.getHeaderField("Location")
                        if (redirectUrl != null) {
                            connection = URL(redirectUrl).openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            responseCode = connection.responseCode
                        }
                    }
                    
                    if (responseCode in 200..299) {
                        val responseMsg = connection.inputStream.bufferedReader().use { it.readText() }
                        if (responseMsg.trim().startsWith("{")) {
                            val responseJson = JSONObject(responseMsg)
                            
                            if (responseJson.optString("status") == "success") {
                            val leadsArray = responseJson.optJSONArray("leads") ?: JSONArray()
                            
                            if (leadsArray.length() > 0) {
                                for (i in 0 until leadsArray.length()) {
                                    val lead = leadsArray.getJSONObject(i)
                                    val phone = lead.optString("phoneNumber")
                                    val name = lead.optString("name", "there")
                                    val type = lead.optString("type", "lead")
                                    val email = lead.optString("email", "")
                                    
                                    if (phone.isNotEmpty()) {
                                        AppLogger.log(applicationContext, "🤖 Sending confirmation to new $type: $name")
                                        val message = when (type) {
                                            "ticket" -> "Hi $name, we got your issue! We also have sent an email about the details of your submission - we will get back to you."
                                            "abandoned_lead" -> "Hi $name, we noticed you started your project enquiry on userXpert but didn't finish. Do you have any questions or need help completing it?"
                                            else -> "Hi $name, thanks for your submission! We also have sent an email about the details of your submission - we will get back to you."
                                        }
                                        sendWhatsAppConfirmation(phone, message)
                                        
                                        // Sleep briefly to let Accessibility Service do its job before opening the next one
                                        delay(8000)
                                    }
                                }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.log(applicationContext, "❌ Error syncing leads (Foreground): ${e.message}")
                }
                
                // Wait 10 seconds before polling again
                delay(10000)
            }
        }
    }

    private fun sendWhatsAppConfirmation(phoneNumber: String, message: String) {
        try {
            val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            if (cleanNumber.length < 5) return

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
            
            try {
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putLong("last_bot_reply_time", System.currentTimeMillis()).apply()
                startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            AppLogger.log(applicationContext, "❌ Failed to open WhatsApp for lead confirmation: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SYNC_CHANNEL_ID",
                "Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the app alive to instantly check for new leads"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
