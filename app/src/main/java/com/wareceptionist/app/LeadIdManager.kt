package com.wareceptionist.app

import android.content.Context

object LeadIdManager {

    fun sanitizePhone(rawPhone: String): String {
        val digitsOnly = rawPhone.filter { it.isDigit() }
        return when {
            rawPhone.trim().startsWith("+") -> "+" + digitsOnly
            digitsOnly.length == 10 -> "+91$digitsOnly"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            digitsOnly.isNotEmpty() -> "+$digitsOnly"
            else -> rawPhone.replace("\\s+".toRegex(), "")
        }
    }

    /**
     * Retrieves existing Lead ID for a phone number or creates a persistent new one.
     * Returns Pair(leadId, isNewLead)
     */
    fun getOrCreateLeadId(context: Context, rawPhone: String): Pair<String, Boolean> {
        val cleanPhone = sanitizePhone(rawPhone)
        if (cleanPhone.length < 5) {
            return Pair("L-" + System.currentTimeMillis(), true)
        }
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val key = "lead_id_$cleanPhone"
        val existing = prefs.getString(key, null)

        return if (!existing.isNullOrEmpty()) {
            Pair(existing, false)
        } else {
            val newId = "L-" + System.currentTimeMillis()
            prefs.edit().putString(key, newId).apply()
            Pair(newId, true)
        }
    }
}
