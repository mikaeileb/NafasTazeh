package com.nafas.tazeh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("nafas_reminder", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return
        val hour = prefs.getInt("hour", 20).coerceIn(0, 23)
        val minute = prefs.getInt("minute", 30).coerceIn(0, 59)
        val message = prefs.getString("message", "امروز هم برای نفس تازه‌ات یک قدم بردار.")
        val reminder = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("message", message)
        }
        val pending = PendingIntent.getBroadcast(
            context, 712, reminder,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, next.timeInMillis,
            AlarmManager.INTERVAL_DAY, pending
        )
    }
}
