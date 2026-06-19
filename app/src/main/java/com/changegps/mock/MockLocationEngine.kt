package com.changegps.mock

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import android.util.Log
import com.changegps.util.GeoUtils
import com.changegps.util.LatLng
import java.util.Random
import kotlin.math.sqrt

/**
 * Wraps Android's test-provider API to publish a fake position.
 *
 * Design priorities (see plan):
 *  - [clear] / [stop] must ALWAYS succeed and fully remove the test providers,
 *    otherwise the device's real GPS stays broken until reboot. Everything else
 *    is built around guaranteeing that teardown runs.
 *  - We feed GPS, NETWORK *and* FUSED providers so Play Services' real Wi-Fi/BT
 *    fix cannot rubber-band the location back.
 *  - Timestamps are generated fresh every push and kept mutually consistent.
 *  - Altitude is held stable; horizontal jitter is gaussian + inertial so the
 *    track looks like real GPS multipath rather than uniform-random sawtooth.
 */
class MockLocationEngine(context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val providers = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.FUSED_PROVIDER) // API 31+
    }

    private val rng = Random()
    // Smoothed jitter offset (metres) carried between ticks for inertial drift.
    private var jitterNorth = 0.0
    private var jitterEast = 0.0

    @Volatile
    private var started = false

    /**
     * Register and enable the test providers. Returns failure (without throwing)
     * if this app is not selected as the mock-location app in Developer options.
     */
    fun start(): Result<Unit> = runCatching {
        for (p in providers) {
            // Remove a stale provider first so re-start is idempotent.
            runCatching { lm.removeTestProvider(p) }
            lm.addTestProvider(
                p,
                ProviderProperties.Builder()
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()
            )
            lm.setTestProviderEnabled(p, true)
        }
        started = true
    }.onFailure {
        Log.w(TAG, "start() failed — is this app selected as the mock location app?", it)
        // Best-effort cleanup so we never leave half-registered providers behind.
        clear()
    }

    /**
     * Push one fake fix to every provider. [accuracyM] small (3–8m) = "precise".
     * [bearingDeg]/[speedMs] should reflect actual motion for route/joystick modes.
     */
    fun push(
        pos: LatLng,
        altitude: Double,
        speedMs: Double,
        bearingDeg: Double,
        accuracyM: Float,
    ) {
        if (!started) return
        val jittered = applyJitter(pos, accuracyM.toDouble())
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        for (p in providers) {
            val loc = Location(p).apply {
                latitude = jittered.lat
                longitude = jittered.lon
                this.altitude = altitude
                accuracy = accuracyM
                speed = speedMs.toFloat()
                bearing = bearingDeg.toFloat()
                time = now
                elapsedRealtimeNanos = elapsed
                bearingAccuracyDegrees = 1.0f
                speedAccuracyMetersPerSecond = 0.5f
                verticalAccuracyMeters = 1.0f
            }
            runCatching { lm.setTestProviderLocation(p, loc) }
        }
    }

    /**
     * Inertial gaussian jitter: each tick the offset is mostly carried over from
     * the previous tick plus a small gaussian nudge, producing a smooth drift.
     */
    private fun applyJitter(pos: LatLng, accuracyM: Double): LatLng {
        val sigma = (accuracyM / 3.0).coerceIn(0.3, 3.0)
        val inertia = 0.85
        val noise = sqrt(1 - inertia * inertia)
        jitterNorth = inertia * jitterNorth + noise * rng.nextGaussian() * sigma
        jitterEast = inertia * jitterEast + noise * rng.nextGaussian() * sigma
        val north = GeoUtils.destinationPoint(pos, kotlin.math.abs(jitterNorth), if (jitterNorth >= 0) 0.0 else 180.0)
        return GeoUtils.destinationPoint(north, kotlin.math.abs(jitterEast), if (jitterEast >= 0) 90.0 else 270.0)
    }

    /** Disable + remove every test provider. Safe to call repeatedly / when not started. */
    fun clear() {
        started = false
        jitterNorth = 0.0
        jitterEast = 0.0
        for (p in providers) {
            runCatching { lm.setTestProviderEnabled(p, false) }
            runCatching { lm.removeTestProvider(p) }
        }
    }

    companion object {
        private const val TAG = "MockLocationEngine"
    }
}
