package com.changegps.mock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.changegps.MainActivity
import com.changegps.util.GeoUtils
import com.changegps.util.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the mock-location engine and drives the per-mode
 * tick loop. It is the single long-lived component pushing fixes; the UI and the
 * overlay only mutate [MockController] state.
 *
 * Lifecycle guarantees (see plan):
 *  - Started only from the foreground UI (API 34 background-start limit).
 *  - PARTIAL_WAKE_LOCK keeps the CPU alive through screen-off route walks.
 *  - onDestroy cancels the scope (no zombie tick loop) and always clears the
 *    engine (real GPS restored).
 */
class MockLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: MockLocationEngine
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        engine = MockLocationEngine(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        val result = engine.start()
        if (result.isFailure) {
            MockController.setError(
                "無法啟動模擬定位。請到「開發者選項 → 選擇模擬位置應用程式」選擇本 App。"
            )
            MockController.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        MockController.setError(null)
        MockController.setRunning(true)
        startTickLoop()
        return START_NOT_STICKY
    }

    private fun startTickLoop() = scope.launch {
        var pos: LatLng? = MockController.current.let { it.emitted ?: it.teleportTarget }
        var bearing = 0.0
        var routeSim: RouteSimulator? = null
        var routeKey = 0

        while (isActive) {
            val s = MockController.current
            // Read the interval live so preset/manual changes take effect immediately.
            val dtSec = s.tickMs / 1000.0
            var speedMs = 0.0

            when (s.mode) {
                Mode.TELEPORT -> {
                    routeSim = null
                    s.teleportTarget?.let { pos = it }
                }

                Mode.ROUTE -> {
                    if (s.waypoints.size >= 2) {
                        val key = s.waypoints.hashCode() * 31 + s.routeLoop.ordinal
                        if (routeSim == null || key != routeKey) {
                            routeSim = RouteSimulator(s.waypoints, s.routeLoop)
                            routeKey = key
                            pos = routeSim!!.startPoint
                        }
                        val sp = GeoUtils.kmhToMs(s.speedKmh)
                        val rp = routeSim!!.advance(sp * dtSec)
                        pos = rp.pos
                        bearing = rp.bearingDeg
                        speedMs = if (rp.finished) 0.0 else sp
                    }
                }

                Mode.JOYSTICK -> {
                    routeSim = null
                    val base = pos
                    val joy = s.joystick
                    if (base != null && joy != null && joy.strength > 0.01) {
                        val sp = GeoUtils.kmhToMs(s.joystickSpeedKmh) * joy.strength
                        pos = GeoUtils.destinationPoint(base, sp * dtSec, joy.angleDeg)
                        bearing = joy.angleDeg
                        speedMs = sp
                    }
                }
            }

            pos?.let { p ->
                engine.push(p, s.altitude, speedMs, bearing, s.accuracyM)
                MockController.setEmitted(p, speedMs)
            }
            delay(s.tickMs)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChangeGps::mock").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定位模擬",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "模擬定位執行中的常駐通知" }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Trampoline-safe: the content tap targets MainActivity directly.
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Stop action targets this service directly (getService) so a tap can
        // immediately clear the mock without bouncing through the Activity.
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("定位模擬執行中")
            .setContentText("點擊開啟 App，或按「停止模擬」立即還原真實定位")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "停止模擬",
                    stopIntent,
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        engine.clear()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        MockController.setRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIF_ID = 1001
        private const val MAX_WAKELOCK_MS = 6 * 60 * 60 * 1000L // safety cap
        const val ACTION_STOP = "com.changegps.action.STOP"

        /** MUST be called while an Activity is foreground (API 34 limit). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, MockLocationService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
