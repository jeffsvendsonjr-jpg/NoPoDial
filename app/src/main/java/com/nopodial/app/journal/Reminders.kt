package com.nopodial.app.journal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.nopodial.app.data.JournalDb

/**
 * Callback-reminder scheduling. Same battery philosophy as the rest of the
 * app: no service, no polling — AlarmManager wakes us exactly when a
 * reminder is due, ReminderReceiver posts the notification, done.
 */
object Reminders {

    fun create(context: Context, number: String, name: String?, dueTs: Long, message: String): Long {
        val id = JournalDb.get(context).insertReminder(number, name, dueTs, message)
        schedule(context, id, dueTs)
        return id
    }

    fun schedule(context: Context, reminderId: Long, dueTs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(context, reminderId)
        // Exact when allowed; a few minutes late on denial beats crashing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueTs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueTs, pi)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(firePendingIntent(context, reminderId))
    }

    /** Alarms don't survive reboot; reschedule everything still pending. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        for (reminder in JournalDb.get(context).pendingReminders()) {
            // Overdue reminders fire right away rather than silently dying.
            schedule(context, reminder.id, maxOf(reminder.dueTs, now + 5_000))
        }
    }

    private fun firePendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData(Uri.parse("nopodial://reminder/$reminderId"))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
