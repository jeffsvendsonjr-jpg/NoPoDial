package com.nopodial.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings plus the tiny bit of transient call state we have to persist,
 * because the OS may kill our process between the OFFHOOK and IDLE broadcasts.
 */
object Prefs {
    private const val FILE = "nopodial_prefs"

    // User settings
    const val KEY_ENABLED = "enabled"
    const val KEY_MESSAGE = "apology_message"
    const val KEY_MAX_DURATION_SEC = "max_duration_sec"

    // Transient call-session state
    const val KEY_LAST_PHONE_STATE = "last_phone_state"
    const val KEY_CALL_IS_INCOMING = "call_is_incoming"
    const val KEY_OUTGOING_START_MS = "outgoing_start_ms"
    const val KEY_SCREEN_WAS_OFF = "screen_was_off"
    const val KEY_DEVICE_WAS_LOCKED = "device_was_locked"
    const val KEY_PROXIMITY_COVERED = "proximity_covered"
    const val KEY_LAST_HANDLED_CALLLOG_ID = "last_handled_calllog_id"

    const val DEFAULT_MAX_DURATION_SEC = 20

    fun get(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_ENABLED, false)

    fun message(context: Context): String =
        get(context).getString(KEY_MESSAGE, null)
            ?: context.getString(R.string.default_apology)

    fun maxDurationSec(context: Context): Int =
        get(context).getInt(KEY_MAX_DURATION_SEC, DEFAULT_MAX_DURATION_SEC)

    fun clearCallSession(context: Context) {
        get(context).edit()
            .remove(KEY_CALL_IS_INCOMING)
            .remove(KEY_OUTGOING_START_MS)
            .remove(KEY_SCREEN_WAS_OFF)
            .remove(KEY_DEVICE_WAS_LOCKED)
            .remove(KEY_PROXIMITY_COVERED)
            .apply()
    }
}
