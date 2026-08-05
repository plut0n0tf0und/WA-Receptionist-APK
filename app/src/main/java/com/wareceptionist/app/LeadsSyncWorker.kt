package com.wareceptionist.app

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

class LeadsSyncWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbxLRftndaH_znmmYtWfL9mmP9hoWXiPaBb8sOGBO5DPXZncXF4hX5akHaMgj8CEcMwW/exec"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AppLogger.log(appContext, "🔄 Syncing new leads from Google Sheets...")
            
            val url = URL(WEB_APP_URL)
            val connection = url.openConnection() as HttpURLConnection
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
            
            if (connection.responseCode in 200..299) {
                val responseMsg = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseMsg)
                
                if (responseJson.optString("status") == "success") {
                    val leadsArray = responseJson.optJSONArray("leads") ?: JSONArray()
                    
                    if (leadsArray.length() == 0) {
                        AppLogger.log(appContext, "✅ No new leads to message.")
                        return@withContext Result.success()
                    }
                    
                    for (i in 0 until leadsArray.length()) {
                        val lead = leadsArray.getJSONObject(i)
                        val phone = lead.optString("phoneNumber")
                        val name = lead.optString("name", "there")
                        val type = lead.optString("type", "lead")
                        val email = lead.optString("email", "")
                        
                        if (phone.isNotEmpty()) {
                            AppLogger.log(appContext, "🤖 Sending confirmation to new $type: $name")
                            val message = when (type) {
                                "ticket" -> "Hi $name, we got your issue! We also have sent an email about the details of your submission - we will get back to you."
                                "abandoned_lead" -> "Hi $name, we noticed you started your project enquiry on userXpert but didn't finish. Do you have any questions or need help completing it?"
                                else -> "Hi $name, thanks for your submission! We also have sent an email about the details of your submission - we will get back to you."
                            }
                            sendWhatsAppConfirmation(phone, message)
                            
                            // Sleep briefly to let Accessibility Service do its job before opening the next one
                            kotlinx.coroutines.delay(8000)
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            AppLogger.log(appContext, "❌ Error syncing leads: ${e.message}")
            Result.retry()
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
                appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putLong("last_bot_reply_time", System.currentTimeMillis()).apply()
                appContext.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            AppLogger.log(appContext, "❌ Failed to open WhatsApp for lead confirmation: ${e.message}")
        }
    }
}
