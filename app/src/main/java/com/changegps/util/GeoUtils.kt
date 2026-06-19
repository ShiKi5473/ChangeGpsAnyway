package com.changegps.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Simple immutable WGS84 coordinate. */
data class LatLng(val lat: Double, val lon: Double)

/**
 * Spherical-earth geodesy helpers. Accurate enough for the short distances a
 * location-spoofer deals with (metres to a few km), and cheap to run every tick.
 */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two points, in metres. */
    fun haversine(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing from [a] to [b] in degrees, normalised to 0..360. */
    fun bearing(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Point reached by travelling [distanceMeters] from [start] along [bearingDeg]. */
    fun destinationPoint(start: LatLng, distanceMeters: Double, bearingDeg: Double): LatLng {
        val angular = distanceMeters / EARTH_RADIUS_M
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(start.lat)
        val lon1 = Math.toRadians(start.lon)

        val lat2 = Math.asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(brng)
        )
        val lon2 = lon1 + atan2(
            sin(brng) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2)
        )
        return LatLng(Math.toDegrees(lat2), ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0)
    }

    /** Convert km/h to m/s. */
    fun kmhToMs(kmh: Double): Double = kmh / 3.6
}
