package com.wareceptionist.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BatteryMonitorService : Service() {

    private var milestone30Called = false
    private var milestone20Called = false
    private var milestone10Called = false
    private var lastCalledLevel = -1
    private var lastCallTime: Long = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    AppLogger.log(context, "🔌 Power connected. Battery alerts paused.")
                    resetMilestones()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    AppLogger.log(context, "🔋 Power disconnected. Monitoring battery levels...")
                    checkBatteryState(context, intent)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    checkBatteryState(context, intent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "BATTERY_MONITOR_CHANNEL")
            .setContentTitle("WA Receptionist")
            .setContentText("Battery reminder service running...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1002, notification)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun checkBatteryState(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("battery_alert_enabled", true)
        if (!isEnabled) return

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            resetMilestones()
            return
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level == -1 || scale == -1) return

        val pct = (level * 100 / scale.toFloat()).toInt()
        val now = System.currentTimeMillis()
        val fiveMinutesMs = 5 * 60 * 1000L

        var shouldCall = false
        var reason = ""

        if (pct <= 30 && pct > 20 && !milestone30Called) {
            shouldCall = true
            milestone30Called = true
            reason = "30% Battery Milestone"
        } else if (pct <= 20 && pct > 10 && !milestone20Called) {
            shouldCall = true
            milestone20Called = true
            reason = "20% Battery Milestone"
        } else if (pct <= 10 && pct >= 5 && !milestone10Called) {
            shouldCall = true
            milestone10Called = true
            reason = "10% Battery Milestone"
        } else if (pct < 5) {
            if (lastCalledLevel == -1 || (lastCalledLevel - pct) >= 2) {
                shouldCall = true
                reason = "Critical Battery <5% ($pct%)"
            }
        }

        // 5-Minute Repeating Rule if still unplugged under 30%
        if (!shouldCall && pct <= 30 && lastCallTime > 0 && (now - lastCallTime) >= fiveMinutesMs) {
            shouldCall = true
            reason = "5-Minute Unplugged Reminder ($pct%)"
        }

        if (shouldCall) {
            lastCallTime = now
            lastCalledLevel = pct
            triggerCall(context, pct, reason)
        }
    }

    private fun triggerCall(context: Context, pct: Int, reason: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val targetPhone = prefs.getString("battery_alert_phone", "6380066280") ?: "6380066280"

        val cleanPhone = targetPhone.filter { it.isDigit() || it == '+' }
        if (cleanPhone.length < 5) return

        val formattedPhone = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            AppLogger.log(context, "📞 Low Battery Alert Triggered ($reason - $pct%)! Calling $formattedPhone...")
            try {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$formattedPhone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
            } catch (e: Exception) {
                AppLogger.log(context, "❌ Call failed: ${e.message}")
            }
        } else {
            AppLogger.log(context, "⚠️ CALL_PHONE permission not granted for Battery Alert ($pct%).")
        }
    }

    private fun resetMilestones() {
        milestone30Called = false
        milestone20Called = false
        milestone10Called = false
        lastCalledLevel = -1
        lastCallTime = 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "BATTERY_MONITOR_CHANNEL",
                "Battery Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors battery level to place call reminders when uncharged"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
