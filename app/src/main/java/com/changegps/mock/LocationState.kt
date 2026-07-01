package com.changegps.mock

import com.changegps.util.GeoUtils
import com.changegps.util.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Mode { TELEPORT, ROUTE, JOYSTICK }

enum class RouteLoop { ONCE, LOOP, PINGPONG }

/** Joystick output: [angleDeg] is a compass bearing (0 = north), [strength] in 0..1. */
data class JoyVector(val angleDeg: Double, val strength: Double)

/**
 * Single source of truth shared across the UI, the floating overlay and the
 * foreground service. All three live in the same process, so a singleton flow
 * is the simplest correct bus.
 *
 * Concurrency rule (see plan): the overlay produces high-frequency joystick
 * updates, so every mutation MUST go through [MutableStateFlow.update] for
 * atomicity — never assign `.value =` directly.
 */
data class MockState(
    val running: Boolean = false,
    val mode: Mode = Mode.TELEPORT,
    val teleportTarget: LatLng? = null,
    val waypoints: List<LatLng> = emptyList(),
    val speedKmh: Double = 4.5,
    val routeLoop: RouteLoop = RouteLoop.ONCE,
    val joystick: JoyVector? = null,
    val joystickSpeedKmh: Double = 4.5,
    val altitude: Double = 25.0,
    val accuracyM: Float = 5f,
    val tickMs: Long = 250L,
    // ---- output / observed ----
    val emitted: LatLng? = null,
    val currentSpeedKmh: Double = 0.0,
    val lastTeleportAt: Long = 0L,
    val lastTeleportDistanceM: Double = 0.0,
    val error: String? = null,
)

object MockController {

    private val _state = MutableStateFlow(MockState())
    val state: StateFlow<MockState> = _state.asStateFlow()

    val current: MockState get() = _state.value

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode) }

    fun setAltitude(alt: Double) = _state.update { it.copy(altitude = alt) }

    /** Teleport target. Records jump distance/time from the last emitted point for cooldown. */
    fun setTeleportTarget(target: LatLng) = _state.update { s ->
        val from = s.emitted
        val dist = if (from != null) GeoUtils.haversine(from, target) else 0.0
        s.copy(
            teleportTarget = target,
            lastTeleportAt = System.currentTimeMillis(),
            lastTeleportDistanceM = dist,
        )
    }

    fun addWaypoint(p: LatLng) = _state.update { it.copy(waypoints = it.waypoints + p) }

    fun removeLastWaypoint() = _state.update {
        it.copy(waypoints = if (it.waypoints.isEmpty()) it.waypoints else it.waypoints.dropLast(1))
    }

    fun clearWaypoints() = _state.update { it.copy(waypoints = emptyList()) }

    fun setSpeedKmh(kmh: Double) = _state.update { it.copy(speedKmh = kmh.coerceIn(0.5, 120.0)) }

    fun setRouteLoop(loop: RouteLoop) = _state.update { it.copy(routeLoop = loop) }

    fun setJoystickSpeedKmh(kmh: Double) =
        _state.update { it.copy(joystickSpeedKmh = kmh.coerceIn(0.5, 30.0)) }

    fun setJoystick(v: JoyVector?) = _state.update { it.copy(joystick = v) }

    /** Push interval in ms. Clamped to a sane range; the tick loop reads it live. */
    fun setTickMs(ms: Long) = _state.update { it.copy(tickMs = ms.coerceIn(50L, 5000L)) }

    fun setRunning(running: Boolean) = _state.update { it.copy(running = running) }

    fun setError(msg: String?) = _state.update { it.copy(error = msg) }

    /** Called by the service loop after each emitted fix, with the speed used (m/s). */
    fun setEmitted(p: LatLng, speedMs: Double = 0.0) =
        _state.update { it.copy(emitted = p, currentSpeedKmh = speedMs * 3.6) }

    /**
     * Suggested remaining cooldown (ms) before it's "safe" to move in-game after a
     * teleport, based on a rough distance→wait heuristic. 0 once elapsed.
     */
    fun cooldownRemainingMs(now: Long = System.currentTimeMillis()): Long {
        val s = _state.value
        if (s.lastTeleportAt == 0L) return 0L
        val needed = suggestedCooldownMs(s.lastTeleportDistanceM)
        return (s.lastTeleportAt + needed - now).coerceAtLeast(0L)
    }

    /** Distance-based cooldown heuristic (conservative, ~Niantic soft-ban style). */
    fun suggestedCooldownMs(distanceM: Double): Long = when {
        distanceM < 1_000 -> 0L
        distanceM < 5_000 -> 2 * 60_000L
        distanceM < 25_000 -> 10 * 60_000L
        distanceM < 100_000 -> 30 * 60_000L
        distanceM < 500_000 -> 60 * 60_000L
        else -> 120 * 60_000L
    }
}
