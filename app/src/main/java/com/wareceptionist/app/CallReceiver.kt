package com.wareceptionist.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!appPrefs.getBoolean("enable_call_capture", true)) return

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.extras?.getString(TelephonyManager.EXTRA_STATE)
            
            val prefs = context.getSharedPreferences("CallCaptureLogs", Context.MODE_PRIVATE)
            val lastState = prefs.getString("last_phone_state", TelephonyManager.EXTRA_STATE_IDLE)
            
            // If the state goes to IDLE, a call has just ended or was missed
            if (stateStr == TelephonyManager.EXTRA_STATE_IDLE && lastState != TelephonyManager.EXTRA_STATE_IDLE) {
                AppLogger.log(context, "Call ended (State IDLE). Triggering background worker...")
                
                try {
                    val workRequest = OneTimeWorkRequestBuilder<CallLogWorker>().build()
                    WorkManager.getInstance(context).enqueue(workRequest)
                } catch (e: Exception) {
                    AppLogger.log(context, "Error starting worker: ${e.message}")
                }
            } else if (stateStr == TelephonyManager.EXTRA_STATE_RINGING && lastState != TelephonyManager.EXTRA_STATE_RINGING) {
                 AppLogger.log(context, "Phone is ringing...")
            } else if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK && lastState != TelephonyManager.EXTRA_STATE_OFFHOOK) {
                 AppLogger.log(context, "Call answered (Offhook)...")
            }
            
            // Save the new state so the next broadcast remembers it
            prefs.edit().putString("last_phone_state", stateStr ?: TelephonyManager.EXTRA_STATE_IDLE).apply()
        }
    }
}
