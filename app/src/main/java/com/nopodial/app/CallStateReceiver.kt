package com.nopodial.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.nopodial.app.journal.CallEndPrompt
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
                // Journal: every connected call gets a start timestamp so we
                // can offer a note prompt at hang-up, incoming or outgoing.
                prefs.edit().putLong(Prefs.KEY_CALL_START_MS, System.currentTimeMillis()).apply()
                if (!incoming && Prefs.isEnabled(context)) {
                    onOutgoingCallStarted(context)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                onCallEnded(context)
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

    /**
     * Call is over. Two independent consumers of this moment:
     * pocket-dial cleanup (outgoing + short + pocket evidence) and the
     * journal's "add a note?" prompt (any connected call). A pocket dial
     * gets the apology, not a note prompt — there's nothing to remember
     * about a call your pocket made.
     */
    private fun onCallEnded(context: Context) {
        val prefs = Prefs.get(context)
        val outgoingStartMs = prefs.getLong(Prefs.KEY_OUTGOING_START_MS, 0L)
        val callStartMs = prefs.getLong(Prefs.KEY_CALL_START_MS, 0L)
        val evidence = PocketEvidence(
            screenWasOff = prefs.getBoolean(Prefs.KEY_SCREEN_WAS_OFF, false),
            deviceWasLocked = prefs.getBoolean(Prefs.KEY_DEVICE_WAS_LOCKED, false),
            proximityCovered = prefs.getBoolean(Prefs.KEY_PROXIMITY_COVERED, false)
        )
        Prefs.clearCallSession(context)

        var pocketDial = false
        if (outgoingStartMs > 0L && Prefs.isEnabled(context)) {
            val durationSec = (System.currentTimeMillis() - outgoingStartMs) / 1000
            pocketDial = durationSec <= Prefs.maxDurationSec(context) &&
                evidence.looksLikePocketDial()
            Log.d(TAG, "Outgoing call ended after ${durationSec}s, evidence=$evidence")
        }

        when {
            pocketDial -> {
                val pending = goAsync()
                thread(name = "nopodial-apology") {
                    try {
                        ApologySender.sendForLastOutgoingCall(context, outgoingStartMs)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to send apology", t)
                    } finally {
                        pending.finish()
                    }
                }
            }

            callStartMs > 0L && Prefs.notePromptEnabled(context) -> {
                val pending = goAsync()
                thread(name = "nopodial-note-prompt") {
                    try {
                        CallEndPrompt.showForEndedCall(context, callStartMs)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to show note prompt", t)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NoPoDial"
    }
}
