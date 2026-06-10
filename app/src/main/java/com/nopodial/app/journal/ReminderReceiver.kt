package com.nopodial.app.journal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nopodial.app.R
import com.nopodial.app.data.JournalDb
import kotlin.concurrent.thread

/**
 * Fires when a callback reminder is due, and handles the notification's
 * Done / Snooze actions. Tapping the notification body opens the dialer
 * with the number ready (ACTION_DIAL — no permission needed, user confirms
 * the call themselves).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id == -1L) return

        val pending = goAsync()
        thread(name = "nopodial-reminder") {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, id)
                    ACTION_DONE -> done(context, id)
                    ACTION_SNOOZE -> snooze(context, id)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Reminder action failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun fire(context: Context, id: Long) {
        val reminder = JournalDb.get(context).reminder(id) ?: return
        if (reminder.done) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val who = reminder.name?.takeIf { it.isNotBlank() } ?: reminder.number
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${reminder.number}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notifId = id.toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_title, who))
            .setContentText(reminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, notifId, dialIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(0, context.getString(R.string.action_done), actionIntent(context, ACTION_DONE, id))
            .addAction(0, context.getString(R.string.action_snooze), actionIntent(context, ACTION_SNOOZE, id))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(notifId, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission not granted", se)
        }
    }

    private fun done(context: Context, id: Long) {
        JournalDb.get(context).markReminderDone(id)
        cancelNotification(context, id)
    }

    private fun snooze(context: Context, id: Long) {
        val due = System.currentTimeMillis() + SNOOZE_MS
        JournalDb.get(context).updateReminderDue(id, due)
        Reminders.schedule(context, id, due)
        cancelNotification(context, id)
    }

    private fun cancelNotification(context: Context, id: Long) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(id.toInt())
    }

    private fun actionIntent(context: Context, action: String, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("nopodial://reminder/$id/$action"))
            .putExtra(EXTRA_REMINDER_ID, id)
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "NoPoDial"
        const val CHANNEL_ID = "follow_ups"
        const val ACTION_FIRE = "com.nopodial.app.REMINDER_FIRE"
        const val ACTION_DONE = "com.nopodial.app.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.nopodial.app.REMINDER_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val SNOOZE_MS = 60 * 60 * 1000L
    }
}
