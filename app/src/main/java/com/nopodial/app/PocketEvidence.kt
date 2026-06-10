package com.nopodial.app

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Snapshot of "was this phone in a pocket?" signals, captured once at the
 * instant an outgoing call starts. The proximity sensor is registered for a
 * single reading and immediately unregistered, so there is no standing
 * sensor cost — the whole point of NoPoDial's design.
 */
data class PocketEvidence(
    val screenWasOff: Boolean,
    val deviceWasLocked: Boolean,
    val proximityCovered: Boolean
) {
    /**
     * A deliberate dial happens on an unlocked, visible screen held away
     * from the face. We call it a pocket dial when at least two signals
     * agree, so a single flaky sensor can't trigger (or suppress) us alone.
     */
    fun looksLikePocketDial(): Boolean {
        var score = 0
        if (screenWasOff) score++
        if (deviceWasLocked) score++
        if (proximityCovered) score++
        return score >= 2
    }

    companion object {
        private const val PROXIMITY_TIMEOUT_MS = 700L

        fun capture(context: Context): PocketEvidence {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return PocketEvidence(
                screenWasOff = !pm.isInteractive,
                deviceWasLocked = km.isKeyguardLocked,
                proximityCovered = sampleProximityOnce(context)
            )
        }

        /** One reading, then unregister. Falls back to false if no sensor or no data in time. */
        private fun sampleProximityOnce(context: Context): Boolean {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return false
            val latch = CountDownLatch(1)
            var covered = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    covered = event.values[0] < sensor.maximumRange
                    latch.countDown()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            try {
                latch.await(PROXIMITY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            } finally {
                sm.unregisterListener(listener)
            }
            return covered
        }
    }
}
