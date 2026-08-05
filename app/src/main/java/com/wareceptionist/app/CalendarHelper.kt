package com.wareceptionist.app

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

object CalendarHelper {
    
    fun bookAppointment(context: Context, startTimeMillis: Long, durationMinutes: Int, clientName: String, description: String): Boolean {
        return try {
            val endTimeMillis = startTimeMillis + (durationMinutes * 60 * 1000)
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTimeMillis)
                put(CalendarContract.Events.DTEND, endTimeMillis)
                put(CalendarContract.Events.TITLE, "Booking: $clientName")
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default local/synced calendar ID
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                AppLogger.log(context, "📅 Appointment booked successfully for $clientName at ID: ${uri.lastPathSegment}")
                true
            } else {
                AppLogger.log(context, "❌ Failed to insert appointment for $clientName")
                false
            }
        } catch (e: SecurityException) {
            AppLogger.log(context, "❌ Missing Calendar permissions")
            false
        } catch (e: Exception) {
            AppLogger.log(context, "❌ Calendar insertion error: ${e.message}")
            false
        }
    }
}
