package com.changegps.mock

import com.changegps.util.GeoUtils
import com.changegps.util.LatLng

data class RoutePoint(val pos: LatLng, val bearingDeg: Double, val finished: Boolean)

/**
 * Walks a polyline of waypoints at a caller-driven pace. Call [advance] with the
 * metres travelled this tick; it returns the interpolated position and heading.
 * Supports run-once, loop, and ping-pong traversal.
 */
class RouteSimulator(
    private val waypoints: List<LatLng>,
    private val loop: RouteLoop,
) {
    private var cur: LatLng = waypoints.firstOrNull() ?: LatLng(0.0, 0.0)
    private var targetIdx: Int = if (waypoints.size > 1) 1 else 0
    private var forward = true
    private var finished = waypoints.size < 2
    private var lastBearing = 0.0

    val startPoint: LatLng get() = waypoints.first()

    fun advance(meters: Double): RoutePoint {
        if (finished || waypoints.size < 2) {
            return RoutePoint(cur, lastBearing, finished)
        }
        var remaining = meters
        while (remaining > 0 && !finished) {
            val target = waypoints[targetIdx]
            val d = GeoUtils.haversine(cur, target)
            lastBearing = GeoUtils.bearing(cur, target)
            if (d <= remaining) {
                cur = target
                remaining -= d
                stepIndex()
            } else {
                cur = GeoUtils.destinationPoint(cur, remaining, lastBearing)
                remaining = 0.0
            }
        }
        return RoutePoint(cur, lastBearing, finished)
    }

    /** Choose the next waypoint to head toward after arriving at [targetIdx]. */
    private fun stepIndex() {
        val last = waypoints.lastIndex
        when (loop) {
            RouteLoop.ONCE -> {
                if (targetIdx >= last) finished = true else targetIdx++
            }
            RouteLoop.LOOP -> {
                targetIdx = (targetIdx + 1) % waypoints.size
            }
            RouteLoop.PINGPONG -> {
                if (forward && targetIdx >= last) {
                    forward = false
                    targetIdx = last - 1
                } else if (!forward && targetIdx <= 0) {
                    forward = true
                    targetIdx = 1
                } else {
                    targetIdx += if (forward) 1 else -1
                }
            }
        }
    }
}
