package com.nopodial.app.journal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nopodial.app.Prefs
import com.nopodial.app.R

/**
 * The journal's heartbeat: right after a call ends, offer a one-tap path
 * into the note editor while the conversation is still in the user's head.
 *
 * Like ApologySender, this runs in the receiver's goAsync window and polls
 * the call log briefly, because the system writes the row shortly *after*
 * the call ends.
 */
object CallEndPrompt {
    private const val TAG = "NoPoDial"
    const val CHANNEL_ID = "call_notes"
    private const val LOG_POLL_ATTEMPTS = 5
    private const val LOG_POLL_INTERVAL_MS = 1200L

    fun showForEndedCall(context: Context, callStartMs: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val entry = pollCallLog(context, callStartMs) ?: run {
            Log.w(TAG, "Call log entry never appeared; no note prompt")
            return
        }

        val prefs = Prefs.get(context)
        if (prefs.getLong(Prefs.KEY_LAST_PROMPTED_CALLLOG_ID, -1L) == entry.id) return
        prefs.edit().putLong(Prefs.KEY_LAST_PROMPTED_CALLLOG_ID, entry.id).apply()

        notify(context, entry)
    }

    private data class LogEntry(
        val id: Long,
        val number: String,
        val name: String?,
        val dateMs: Long
    )

    private fun pollCallLog(context: Context, callStartMs: Long): LogEntry? {
        repeat(LOG_POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(LOG_POLL_INTERVAL_MS)
            val entry = queryLatestCall(context)
            if (entry != null && entry.dateMs >= callStartMs - 2000) return entry
        }
        return null
    }

    private fun queryLatestCall(context: Context): LogEntry? {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            if (c.moveToFirst()) {
                val number = c.getString(1) ?: return null
                if (number.isBlank()) return null
                return LogEntry(c.getLong(0), number, c.getString(2), c.getLong(3))
            }
        }
        return null
    }

    private fun notify(context: Context, entry: LogEntry) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.note_prompt_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val who = entry.name?.takeIf { it.isNotBlank() } ?: entry.number
        val notifId = entry.id.toInt()
        val editorIntent = NoteEditorActivity.intent(
            context, entry.number, entry.name, entry.dateMs
        ).apply {
            putExtra(NoteEditorActivity.EXTRA_NOTIFICATION_ID, notifId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context, notifId, editorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.note_prompt_title))
            .setContentText(context.getString(R.string.note_prompt_text, who))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(PROMPT_TIMEOUT_MS)
            .build()
        try {
            nm.notify(notifId, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission not granted", se)
        }
    }

    /** If a note wasn't started within 30 minutes, the moment has passed. */
    private const val PROMPT_TIMEOUT_MS = 30 * 60 * 1000L
}
