package com.nopodial.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Looks up the number that was just pocket-dialed and texts the apology.
 * Runs inside the receiver's goAsync window (~10s), so call-log polling
 * is bounded.
 */
object ApologySender {
    private const val TAG = "NoPoDial"
    private const val CHANNEL_ID = "apologies"
    private const val LOG_POLL_ATTEMPTS = 5
    private const val LOG_POLL_INTERVAL_MS = 1200L

    fun sendForLastOutgoingCall(context: Context, callStartMs: Long) {
        val entry = pollCallLog(context, callStartMs) ?: run {
            Log.w(TAG, "Call log entry never appeared; no apology sent")
            return
        }

        val prefs = Prefs.get(context)
        if (prefs.getLong(Prefs.KEY_LAST_HANDLED_CALLLOG_ID, -1L) == entry.id) {
            return // already apologized for this exact call
        }

        if (isEmergencyNumber(context, entry.number)) {
            Log.w(TAG, "Pocket-dialed an emergency number; never texting those")
            return
        }

        val message = Prefs.message(context)
        val sms = smsManager(context)
        val parts = sms.divideMessage(message)
        sms.sendMultipartTextMessage(entry.number, null, parts, null, null)

        prefs.edit().putLong(Prefs.KEY_LAST_HANDLED_CALLLOG_ID, entry.id).apply()
        notifyUser(context, entry.number)
        Log.i(TAG, "Apology SMS sent")
    }

    private data class LogEntry(val id: Long, val number: String)

    /**
     * The call log row is written by the system shortly *after* the call
     * ends, so retry briefly. We only accept an OUTGOING row whose date is
     * at/after our recorded dial time, which keeps us from texting some
     * older call's number.
     */
    private fun pollCallLog(context: Context, callStartMs: Long): LogEntry? {
        repeat(LOG_POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(LOG_POLL_INTERVAL_MS)
            val entry = queryLatestOutgoing(context)
            if (entry != null && entry.dateMs >= callStartMs - 2000) {
                return LogEntry(entry.id, entry.number)
            }
        }
        return null
    }

    private data class RawEntry(val id: Long, val number: String, val dateMs: Long)

    private fun queryLatestOutgoing(context: Context): RawEntry? {
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE)
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.TYPE} = ?",
            arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(1) ?: return null
                if (number.isBlank()) return null
                return RawEntry(cursor.getLong(0), number, cursor.getLong(2))
            }
        }
        return null
    }

    private fun isEmergencyNumber(context: Context, number: String): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tm.isEmergencyNumber(number)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.PhoneNumberUtils.isEmergencyNumber(number)
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    @SuppressLint("MissingPermission")
    private fun notifyUser(context: Context, number: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_text, number))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(number.hashCode(), notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission not granted", se)
        }
    }
}
