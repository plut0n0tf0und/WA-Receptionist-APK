package com.wareceptionist.app

import app.rive.runtime.kotlin.core.Rive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var permCall: TextView
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Rive before setting content view
        Rive.init(this)
        
        setContentView(R.layout.activity_main)
        
        logText = findViewById(R.id.logText)
        permCall = findViewById(R.id.permCall)
        
        findViewById<Button>(R.id.btnViewFullLog).setOnClickListener {
            startActivity(android.content.Intent(this, FullLogActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnGrantCallPerms).setOnClickListener {
            requestCallPermissions()
        }
        
        findViewById<Button>(R.id.btnGrantAccessibility).setOnClickListener {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            AppLogger.log(this, "Opening Accessibility Settings...")
        }
        
        findViewById<Button>(R.id.btnGrantNotification).setOnClickListener {
            startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            AppLogger.log(this, "Opening Notification Settings...")
        }
        
        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val inputApiKey = findViewById<android.widget.EditText>(R.id.inputApiKey)
        inputApiKey.setText(prefs.getString("groq_api_key", ""))
        
        findViewById<Button>(R.id.btnSaveApiKey).setOnClickListener {
            val key = inputApiKey.text.toString().trim()
            
            val provider: String
            val url: String
            val model: String
            
            if (key.startsWith("gsk_")) {
                provider = "Groq"
                url = "https://api.groq.com/openai/v1/chat/completions"
                model = "llama-3.3-70b-versatile"
            } else if (key.startsWith("sk-or-")) {
                provider = "OpenRouter"
                url = "https://openrouter.ai/api/v1/chat/completions"
                model = "google/gemini-1.5-pro"
            } else if (key.startsWith("sk-proj-") || key.startsWith("sk-")) {
                provider = "OpenAI"
                url = "https://api.openai.com/v1/chat/completions"
                model = "gpt-4o"
            } else {
                provider = "Unknown"
                url = "https://api.groq.com/openai/v1/chat/completions" // Fallback
                model = "llama-3.3-70b-versatile"
                android.widget.Toast.makeText(this, "Unrecognized Key! Defaulting to Groq.", android.widget.Toast.LENGTH_LONG).show()
            }
            
            prefs.edit()
                .putString("groq_api_key", key)
                .putString("ai_provider", provider)
                .putString("ai_url", url)
                .putString("ai_model", model)
                .apply()
                
            if (provider != "Unknown") {
                android.widget.Toast.makeText(this, "$provider API Key Auto-Detected & Saved!", android.widget.Toast.LENGTH_SHORT).show()
                AppLogger.log(this, "Auto-configured AI Provider: $provider (Model: $model)")
                updateLogs()
            }
        }
        
        val inputBatteryPhone = findViewById<android.widget.EditText>(R.id.inputBatteryPhone)
        inputBatteryPhone.setText(prefs.getString("battery_alert_phone", "6380066280"))

        findViewById<Button>(R.id.btnSaveBatteryPhone).setOnClickListener {
            val phone = inputBatteryPhone.text.toString().trim()
            prefs.edit().putString("battery_alert_phone", phone).apply()
            android.widget.Toast.makeText(this, "Target Battery Alert Phone Saved!", android.widget.Toast.LENGTH_SHORT).show()
            AppLogger.log(this, "Target Battery Alert Phone set to: $phone")
            updateLogs()
        }
        
        findViewById<android.widget.ImageView>(R.id.btnRefreshLogs).setOnClickListener {
            updateLogs()
            android.widget.Toast.makeText(this, "Logs Refreshed", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<android.widget.ImageView>(R.id.btnAppInfo)?.setOnClickListener {
            showInfoDialog()
        }
        
        // Setup Module Toggles with Confirmation Dialogs
        setupToggles()

        // Start Foreground Service for continuous Lead Sync (replaces 15-min WorkManager)
        val syncServiceIntent = android.content.Intent(this, ForegroundLeadsSyncService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(this, syncServiceIntent)
        } else {
            startService(syncServiceIntent)
        }

        // Start Battery Monitoring Service
        val batteryServiceIntent = android.content.Intent(this, BatteryMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(this, batteryServiceIntent)
        } else {
            startService(batteryServiceIntent)
        }

        updatePermissionsUI()
        updateLogs()
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionsUI()
        updateLogs()
        updateRiveTime()
    }
    
    private fun updateRiveTime() {
        val riveAnimation = findViewById<app.rive.runtime.kotlin.RiveAnimationView>(R.id.riveAnimation)
        if (riveAnimation != null) {
            // Indian Standard Time is UTC+5:30
            val istZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            val calendar = java.util.Calendar.getInstance(istZone)
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            
            // Assuming night is between 6:00 PM (18) and 6:00 AM (6)
            val isNight = hour < 6 || hour >= 18
            
            try {
                // We now know the exact Input name is "on/off"
                riveAnimation.setBooleanState("Start", "on/off", isNight)
                // Adding a couple of safe fallbacks just in case it meant "on" or "off"
                riveAnimation.setBooleanState("Start", "on", isNight)
                riveAnimation.setBooleanState("Start", "off", isNight)
            } catch (e: Exception) {
                // Hide this from the UI terminal to preserve serenity, 
                // but log to Android Logcat in case we need to debug.
                android.util.Log.e("WA_RECEPTIONIST", "Rive state switch error: ${e.message}")
            }
        }
    }
    
    private fun updateLogs() {
        logText.text = AppLogger.getRecentLogs(this)
    }

    private fun hasCallPermissions(): Boolean {
        val hasCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasReadCal = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val hasWriteCal = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        return hasCallLog && hasPhoneState && hasReadCal && hasWriteCal
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = android.content.ComponentName(this, WhatsAppNotificationService::class.java)
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun updatePermissionsUI() {
        val btnGrant = findViewById<Button>(R.id.btnGrantCallPerms)
        val btnAccess = findViewById<Button>(R.id.btnGrantAccessibility)
        val btnNotify = findViewById<Button>(R.id.btnGrantNotification)
        
        val callOk = hasCallPermissions()
        val accessOk = isAccessibilityEnabled()
        val notifyOk = isNotificationListenerEnabled()
        
        if (callOk && notifyOk) {
            permCall.text = "[ OK ] SYSTEM INTEGRITY VERIFIED"
            permCall.setTextColor(android.graphics.Color.parseColor("#00FF00")) // Neon Green
        } else {
            permCall.text = "[ !! ] CRITICAL PERMISSIONS MISSING"
            permCall.setTextColor(android.graphics.Color.parseColor("#FF8A80")) // Warning Red
        }
        
        if (callOk) {
            btnGrant.isEnabled = false
            btnGrant.text = "CALL PERMS OPTIMAL"
            btnGrant.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#333333"))
            btnGrant.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            btnGrant.isEnabled = true
            btnGrant.text = "GRANT CALL PERMS"
            btnGrant.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
            btnGrant.setTextColor(android.graphics.Color.parseColor("#000000"))
        }

        if (accessOk) {
            btnAccess.isEnabled = false
            btnAccess.text = "GHOST SENDER OPTIMAL"
            btnAccess.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#333333"))
            btnAccess.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            btnAccess.isEnabled = true
            btnAccess.text = "ENABLE GHOST SENDER"
            btnAccess.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
            btnAccess.setTextColor(android.graphics.Color.parseColor("#000000"))
        }

        if (notifyOk) {
            btnNotify.isEnabled = false
            btnNotify.text = "AI LISTENER OPTIMAL"
            btnNotify.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#333333"))
            btnNotify.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            btnNotify.isEnabled = true
            btnNotify.text = "ENABLE AI LISTENER"
            btnNotify.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
            btnNotify.setTextColor(android.graphics.Color.parseColor("#000000"))
        }
    }

    private fun requestCallPermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionsNeeded.add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        }
        ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionsUI()
            if (hasCallPermissions()) {
                AppLogger.log(this, "Permissions were just granted.")
            } else {
                AppLogger.log(this, "Permissions were denied by user.")
            }
            updateLogs()
        }
    }

    private fun setupToggles() {
        val switchCallCapture = findViewById<SwitchMaterial>(R.id.switchCallCapture)
        val switchWaAi = findViewById<SwitchMaterial>(R.id.switchWaAi)
        val switchBatteryAlert = findViewById<SwitchMaterial>(R.id.switchBatteryAlert)

        setupToggleWithConfirm(switchCallCapture, "CALL CAPTURE", "enable_call_capture")
        setupToggleWithConfirm(switchWaAi, "WHATSAPP AI", "enable_wa_ai")
        setupToggleWithConfirm(switchBatteryAlert, "BATTERY AUTO-CALL ALERT", "battery_alert_enabled")
    }

    private fun setupToggleWithConfirm(switchView: SwitchMaterial, moduleName: String, prefKey: String) {
        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        switchView.isChecked = prefs.getBoolean(prefKey, true)

        switchView.setOnClickListener {
            // Revert immediately visually so we can confirm first
            val targetState = switchView.isChecked
            switchView.isChecked = !targetState 
            
            val actionStr = if (targetState) "ENABLE" else "DISABLE"
            val message = "Are you sure you want to $actionStr the $moduleName module?"
            
            showCyberDialog(message) { confirmed ->
                if (confirmed) {
                    switchView.isChecked = targetState
                    prefs.edit().putBoolean(prefKey, targetState).apply()
                    AppLogger.log(this, "Module $moduleName -> $actionStr")
                    updateLogs()
                }
            }
        }
    }

    private fun showCyberDialog(message: String, onResult: (Boolean) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_confirm, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialogMessage).text = message

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
            onResult(false)
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            onResult(true)
        }

        dialog.show()
    }

    private fun showInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_info, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val infoText = """
Welcome to Call Capture Bot!

Here is everything you need to know about what this app does, how it works, and why your data stays safe with us.

----------------------------------------

1. WHAT IS THIS APP & WHAT DOES IT DO?
Think of this app as an automated receptionist for your phone calls!

Whenever a call ends (whether incoming, outgoing, or missed):
1. It automatically grabs basic details like the phone number, date, time, duration, and call status.
2. It immediately syncs this data directly to your designated Google Sheet or backend web dashboard so you never lose a business lead again.
3. You can also view a live log directly inside the app anytime to check what's been synced.

----------------------------------------

2. IS IT SAFE? (YOUR PRIVACY & SECURITY)
Yes, absolutely! We designed this system with security and simplicity in mind:

• No Stored Passwords or Keys: The app holds zero database passwords or private keys. Even if someone inspected the app code, there are no secrets to steal.
• Direct & Private Sync: Data moves securely over HTTPS directly to your configured spreadsheet/webhook. We don't store or read your calls on any third-party servers.
• Essential Permissions Only: We only request permissions needed for the app to function:
  - Phone & Call Log: To identify call events and details when a call completes.
  - Internet Access: To send your call details to your Google Sheet.
• Battery Friendly: The app doesn't run continuously in the background—it only wakes up for a split second when a call finishes, so it won't drain your battery.

----------------------------------------

3. TECHNICAL ARCHITECTURE
• Client: Native Android (Kotlin)
• Transport Protocol: Standard HTTPS POST with JSON payload
• Endpoint Architecture: Serverless Google Apps Script Webhook + Google Sheets / Web API
• Error Handling: In-app diagnostic log viewer with standard HTTP status code tracking (e.g., Code 200 OK)

----------------------------------------

4. CREATED BY & VERSION
• Creator: Vignesh Balakrishnan (Vicki)
• Version: v8.0.26 (Build 8026 - Receptionist Core)
        """.trimIndent()

        dialogView.findViewById<TextView>(R.id.txtInfoContent).text = infoText

        dialogView.findViewById<TextView>(R.id.btnCloseInfoUpper).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCloseInfo).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
