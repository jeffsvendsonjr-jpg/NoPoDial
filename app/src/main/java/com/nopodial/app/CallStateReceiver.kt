package com.nopodial.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * The only entry point of the whole app at runtime. The system delivers
 * PHONE_STATE broadcasts on call transitions, so NoPoDial consumes zero
 * battery while the phone sits in your pocket — it literally cannot run
 * until a call happens.
 *
 * Outgoing call signature (no RINGING first):
 *   IDLE -> OFFHOOK -> IDLE
 *
 * On OFFHOOK we snapshot pocket evidence; on IDLE we decide and, if it was
 * a pocket dial, text the callee so they don't call back.
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        val prefs = Prefs.get(context)
        val lastState = prefs.getString(Prefs.KEY_LAST_PHONE_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        if (state == lastState) return // dual-SIM devices deliver duplicates
        prefs.edit().putString(Prefs.KEY_LAST_PHONE_STATE, state).apply()

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call — not our problem. Flag it so the following
                // OFFHOOK isn't mistaken for an outgoing dial.
                prefs.edit().putBoolean(Prefs.KEY_CALL_IS_INCOMING, true).apply()
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val incoming = prefs.getBoolean(Prefs.KEY_CALL_IS_INCOMING, false)
                if (!incoming && Prefs.isEnabled(context)) {
                    onOutgoingCallStarted(context)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val startMs = prefs.getLong(Prefs.KEY_OUTGOING_START_MS, 0L)
                if (startMs > 0L && Prefs.isEnabled(context)) {
                    onOutgoingCallEnded(context, startMs)
                } else {
                    Prefs.clearCallSession(context)
                }
            }
        }
    }

    /** Grab the one-shot pocket snapshot the instant dialing starts. */
    private fun onOutgoingCallStarted(context: Context) {
        val pending = goAsync()
        thread(name = "nopodial-evidence") {
            try {
                val evidence = PocketEvidence.capture(context)
                Prefs.get(context).edit()
                    .putLong(Prefs.KEY_OUTGOING_START_MS, System.currentTimeMillis())
                    .putBoolean(Prefs.KEY_SCREEN_WAS_OFF, evidence.screenWasOff)
                    .putBoolean(Prefs.KEY_DEVICE_WAS_LOCKED, evidence.deviceWasLocked)
                    .putBoolean(Prefs.KEY_PROXIMITY_COVERED, evidence.proximityCovered)
                    .apply()
                Log.d(TAG, "Outgoing call started, evidence=$evidence")
            } finally {
                pending.finish()
            }
        }
    }

    /** Call is over — decide whether it was a pocket dial and apologize if so. */
    private fun onOutgoingCallEnded(context: Context, startMs: Long) {
        val prefs = Prefs.get(context)
        val evidence = PocketEvidence(
            screenWasOff = prefs.getBoolean(Prefs.KEY_SCREEN_WAS_OFF, false),
            deviceWasLocked = prefs.getBoolean(Prefs.KEY_DEVICE_WAS_LOCKED, false),
            proximityCovered = prefs.getBoolean(Prefs.KEY_PROXIMITY_COVERED, false)
        )
        Prefs.clearCallSession(context)

        val durationSec = (System.currentTimeMillis() - startMs) / 1000
        val shortEnough = durationSec <= Prefs.maxDurationSec(context)

        Log.d(TAG, "Outgoing call ended after ${durationSec}s, evidence=$evidence")
        if (!shortEnough || !evidence.looksLikePocketDial()) return

        val pending = goAsync()
        thread(name = "nopodial-apology") {
            try {
                ApologySender.sendForLastOutgoingCall(context, startMs)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send apology", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NoPoDial"
    }
}
