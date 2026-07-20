package com.wareceptionist.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wareceptionist.app.db.AppDatabase
import com.wareceptionist.app.db.ChatMessage
import com.wareceptionist.app.db.ChatSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class WhatsAppNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val systemPrompt = """
        You are a warm, efficient, and professional virtual receptionist for UserXpert. 
        Your primary goal is to provide a form link for clients to fill out their details. 
        
        TONE & STYLE:
        - Sound conversational, friendly, and naturally human. Use casual greetings.
        - Keep responses extremely brief (1-2 short sentences maximum).
        - Be empathetic and professional. Do not sound robotic.

        OPERATIONAL RULES:
        1. If the user's last message is a greeting or general inquiry, greet them and ask which service they need by providing this exact numbered list:
           1. Custom Websites
           2. E-commerce Solutions
           3. UI/UX Design Services
           4. Web Apps & Portals
           5. Analytics Dashboards
           6. AI Chatbots
           7. Business Automation
           8. SEO & Digital Marketing
        2. If the user's last message contains a number or service name from the list above, your ONLY response should be this exact link and a short message asking them to fill it out: YOUR_FORM_LINK_HERE
        3. If the user's last message indicates they have filled out the form (e.g. "I have submitted the form"), reply with exactly: "Thanks for filling that out! 🎉 Our team is reviewing your details right now, and we'll reach out very soon to discuss the next steps for your project. Have a great day!"
        
        CRITICAL CONSTRAINTS:
        - NEVER simulate or write the user's responses. You only speak for the receptionist.
        - NEVER output prefixes like "Assistant:" or "User:".
        - ONLY output the exact text message you want to send right now.
    """.trimIndent()

    companion object {
        private val lastProcessedMessages = mutableMapOf<String, String>()
        private val isProcessing = mutableMapOf<String, Boolean>()
        private val lastReplyTime = mutableMapOf<String, Long>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enable_wa_ai", true)) return

        val packageName = sbn.packageName
        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            val notification = sbn.notification
            val extras = notification.extras
            
            // Is it a group chat? Skip it.
            val isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
            if (isGroupConversation) return

            val sender = extras.getString(Notification.EXTRA_TITLE) ?: return
            val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
            
            // Filter out WhatsApp system background notifications and the bot's own sent messages
            if (sender.contains("WhatsApp", ignoreCase = true) || 
                messageText.contains("Checking for new message", ignoreCase = true) ||
                messageText.startsWith("You:", ignoreCase = true) ||
                messageText.startsWith("✓", ignoreCase = true)) {
                return
            }
            
            // Deduplication
            if (lastProcessedMessages[sender] == messageText) {
                return 
            }
            if (isProcessing[sender] == true) {
                return
            }
            if (System.currentTimeMillis() - lastReplyTime.getOrDefault(sender, 0L) < 15000) {
                return // Cooldown to avoid multiple replies to fast messages
            }
            lastProcessedMessages[sender] = messageText
            isProcessing[sender] = true
            
            AppLogger.log(this, "📩 Incoming from $sender: $messageText")

            // Find the quick reply action
            var replyAction: Notification.Action? = null
            if (notification.actions != null) {
                for (action in notification.actions) {
                    if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                        replyAction = action
                        break
                    }
                }
            }

            if (replyAction != null) {
                handleIncomingMessage(sender, messageText, replyAction, this)
            } else {
                AppLogger.log(this, "⚠️ No Quick Reply action found for $sender")
            }
        }
    }

    private fun handleIncomingMessage(sender: String, messageText: String, replyAction: Notification.Action, context: Context) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context).chatDao()
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val apiKey = prefs.getString("groq_api_key", "")
                
                val aiProvider = prefs.getString("ai_provider", "Groq") ?: "Groq"
                
                if (apiKey.isNullOrEmpty()) {
                    AppLogger.log(context, "❌ $aiProvider API Key is missing. Cannot reply.")
                    return@launch
                }

            // Save user message to DB
            db.insertSession(ChatSession(sender, System.currentTimeMillis()))
            db.insertMessage(ChatMessage(sessionPhone = sender, role = "user", content = messageText, timestamp = System.currentTimeMillis()))

            // Build history
            val allMessages = db.getMessagesForSession(sender)
            val jsonMessages = JSONArray()
            
            // System prompt
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", systemPrompt)
            jsonMessages.put(systemMsg)
            
            for (msg in allMessages) {
                val role = if (msg.role == "model") "assistant" else "user"
                val jsonMsg = JSONObject()
                jsonMsg.put("role", role)
                jsonMsg.put("content", msg.content)
                jsonMessages.put(jsonMsg)
            }
            val aiModel = prefs.getString("ai_model", "llama-3.3-70b-versatile")
            
            val requestBody = JSONObject()
            requestBody.put("model", aiModel)
            requestBody.put("messages", jsonMessages)
            requestBody.put("max_tokens", 150)
            requestBody.put("temperature", 0.6)
            
            val toolsArray = JSONArray()
            val bookApptTool = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "book_appointment")
                    put("description", "Books an appointment in the calendar. Call this when the user has confirmed a specific date and time.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("date", JSONObject().apply {
                                put("type", "string")
                                put("description", "The date of the appointment in YYYY-MM-DD format (e.g. 2026-07-20)")
                            })
                            put("time", JSONObject().apply {
                                put("type", "string")
                                put("description", "The time of the appointment in HH:MM format (24-hour, e.g. 15:30)")
                            })
                            put("clientName", JSONObject().apply {
                                put("type", "string")
                                put("description", "The name of the client, if known. Otherwise use 'Client'")
                            })
                        })
                        put("required", JSONArray().apply {
                            put("date")
                            put("time")
                            put("clientName")
                        })
                    })
                })
            }
            toolsArray.put(bookApptTool)
            requestBody.put("tools", toolsArray)
            requestBody.put("tool_choice", "auto")
            
            val stopArray = JSONArray()
            stopArray.put("\n[") // Stop if it tries to generate a WhatsApp timestamp
            stopArray.put("User:")
            stopArray.put("Client:")
            requestBody.put("stop", stopArray)
            
            val aiUrl = prefs.getString("ai_url", "https://api.groq.com/openai/v1/chat/completions")
            try {
                val url = URL(aiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true
                
                connection.outputStream.use { os ->
                    val input = requestBody.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseStr = reader.readText()
                    reader.close()
                    
                    val responseJson = JSONObject(responseStr)
                    val choices = responseJson.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val messageObj = choices.getJSONObject(0).getJSONObject("message")
                        
                        if (messageObj.has("tool_calls")) {
                            val toolCalls = messageObj.getJSONArray("tool_calls")
                            for (i in 0 until toolCalls.length()) {
                                val toolCall = toolCalls.getJSONObject(i)
                                if (toolCall.getJSONObject("function").getString("name") == "book_appointment") {
                                    val args = JSONObject(toolCall.getJSONObject("function").getString("arguments"))
                                    val dateStr = args.optString("date", "")
                                    val timeStr = args.optString("time", "")
                                    val name = args.optString("clientName", "Client")
                                    
                                    try {
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                        val date = sdf.parse("$dateStr $timeStr")
                                        if (date != null) {
                                            val success = CalendarHelper.bookAppointment(context, date.time, 30, name, "Booked via WhatsApp AI")
                                            val replyText = if (success) {
                                                "Perfect! I've booked your appointment for $dateStr at $timeStr. See you then!"
                                            } else {
                                                "I'm sorry, I couldn't save the appointment to the calendar due to a system error. Please hold on while a human assists you."
                                            }
                                            db.insertMessage(ChatMessage(sessionPhone = sender, role = "model", content = replyText, timestamp = System.currentTimeMillis()))
                                            sendReply(replyAction, replyText)
                                            val now = System.currentTimeMillis()
                                            lastReplyTime[sender] = now
                                            lastProcessedMessages[sender] = replyText
                                            prefs.edit().putLong("last_bot_reply_time", now).apply()
                                            AppLogger.log(context, "🤖 $aiProvider Booked Appointment for $sender: $dateStr $timeStr")
                                            return@launch // Stop further processing for this message
                                        }
                                    } catch(e: Exception) {
                                        AppLogger.log(context, "❌ Error parsing appointment date: ${e.message}")
                                    }
                                } else if (toolCall.getJSONObject("function").getString("name") == "save_lead_to_sheet") {
                                    val args = JSONObject(toolCall.getJSONObject("function").getString("arguments"))
                                    val cat = args.optString("category", "")
                                    val sum = args.optString("summary", "")
                                    saveLeadToSheet(context, sender, cat, sum)
                                    
                                    val replyText = "Got it! I've noted down that you're interested in $cat. How would you like to proceed? We can discuss details now, or I can book a quick call for you."
                                    db.insertMessage(ChatMessage(sessionPhone = sender, role = "model", content = replyText, timestamp = System.currentTimeMillis()))
                                    sendReply(replyAction, replyText)
                                    val now = System.currentTimeMillis()
                                    lastReplyTime[sender] = now
                                    lastProcessedMessages[sender] = replyText
                                    prefs.edit().putLong("last_bot_reply_time", now).apply()
                                    return@launch
                                }
                            }
                        }
                        
                        // Normal text reply
                        val replyText = messageObj.optString("content", "")
                        if (replyText.isNotEmpty()) {
                            db.insertMessage(ChatMessage(sessionPhone = sender, role = "model", content = replyText, timestamp = System.currentTimeMillis()))
                            sendReply(replyAction, replyText)
                            val now = System.currentTimeMillis()
                            lastReplyTime[sender] = now
                            lastProcessedMessages[sender] = replyText
                            prefs.edit().putLong("last_bot_reply_time", now).apply()
                            AppLogger.log(context, "🤖 $aiProvider Replied to $sender: $replyText")
                        }
                    }
                } else {
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                    val errorStr = errorReader.readText()
                    errorReader.close()
                    AppLogger.log(context, "❌ $aiProvider API Error: HTTP $responseCode - $errorStr")
                }
            } catch (e: Exception) {
                AppLogger.log(context, "❌ $aiProvider Network Error: ${e.message}")
            }
            } catch (e: Exception) {
                AppLogger.log(context, "❌ General Error: ${e.message}")
            } finally {
                isProcessing[sender] = false
            }
        }
    }

    private fun sendReply(replyAction: Notification.Action, replyText: String) {
        val remoteInputs = replyAction.remoteInputs
        if (remoteInputs.isNullOrEmpty()) return

        val remoteInput = remoteInputs[0]
        val localIntent = android.content.Intent()
        val localBundle = android.os.Bundle()
        localBundle.putCharSequence(remoteInput.resultKey, replyText)
        android.app.RemoteInput.addResultsToIntent(remoteInputs, localIntent, localBundle)

        try {
            replyAction.actionIntent.send(this, 0, localIntent)
        } catch (e: android.app.PendingIntent.CanceledException) {
            e.printStackTrace()
        }
    }

    private fun saveLeadToSheet(context: Context, phone: String, category: String, summary: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://script.google.com/macros/s/AKfycbxLRftndaH_znmmYtWfL9mmP9hoWXiPaBb8sOGBO5DPXZncXF4hX5akHaMgj8CEcMwW/exec")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val json = JSONObject().apply {
                    put("phoneNumber", phone)
                    put("callType", "WhatsApp Lead")
                    put("date", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))
                    put("time", java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
                    put("duration", category) // repurpose duration for category
                    put("messageText", summary)
                    put("messageStatus", "Analyzed")
                }
                val os = java.io.OutputStreamWriter(connection.outputStream)
                os.write(json.toString())
                os.flush()
                os.close()
                val code = connection.responseCode
                AppLogger.log(context, "📝 Lead Saved to Sheet. Category: $category")
            } catch (e: Exception) {
                AppLogger.log(context, "❌ Error saving lead to sheet: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not used
    }
}
