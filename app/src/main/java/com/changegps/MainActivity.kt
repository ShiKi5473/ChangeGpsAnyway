package com.changegps

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.changegps.mock.MockController
import com.changegps.mock.MockLocationService
import com.changegps.overlay.OverlayService
import com.changegps.ui.AppActions
import com.changegps.ui.AppScreen
import com.changegps.util.LatLng
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private val hasLocation = mutableStateOf(false)
    private val hasOverlay = mutableStateOf(false)
    private val ignoringBattery = mutableStateOf(false)
    private val isMockApp = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid()
        refreshPermissions()

        val actions = AppActions(
            onStart = ::startMock,
            onStop = { MockLocationService.stop(this) },
            onStartOverlay = ::startOverlay,
            onStopOverlay = { stopService(Intent(this, OverlayService::class.java)) },
            onOpenMockSettings = ::openDeveloperSettings,
            onOpenOverlaySettings = ::openOverlaySettings,
            onRequestBattery = ::requestIgnoreBattery,
            onRequestLocation = ::requestLocationPermission,
            onGetRealLocation = ::getRealLocation,
        )

        setContent {
            MaterialTheme {
                Surface {
                    val state by MockController.state.collectAsState()
                    AppScreen(
                        state = state,
                        hasLocation = hasLocation.value,
                        hasOverlay = hasOverlay.value,
                        isIgnoringBattery = ignoringBattery.value,
                        isMockApp = isMockApp.value,
                        actions = actions,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    /** osmdroid must use internal storage to avoid Android 13+ external-storage rules. */
    private fun configureOsmdroid() {
        val ctx = applicationContext
        val config = Configuration.getInstance()
        config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        config.userAgentValue = packageName
        val base = File(filesDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
        config.tileFileSystemCacheMaxBytes = 50L * 1024 * 1024  // 50 MB 磁碟快取上限
        config.tileFileSystemCacheTrimBytes = 40L * 1024 * 1024 // 清到 40 MB 為止
    }

    private fun refreshPermissions() {
        hasLocation.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasOverlay.value = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        ignoringBattery.value = pm.isIgnoringBatteryOptimizations(packageName)
        isMockApp.value = checkMockLocationAppOp()
    }

    private fun checkMockLocationAppOp(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startMock() {
        if (!hasLocation.value) {
            requestLocationPermission()
            return
        }
        // Started while this Activity is foreground — satisfies the API 34 limit.
        MockLocationService.start(this)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (!MockController.current.running) startMock()
        startService(Intent(this, OverlayService::class.java))
    }

    private fun requestLocationPermission() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    private fun openDeveloperSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun requestIgnoreBattery() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /**
     * Returns the last known real device position, trying GPS then NETWORK providers.
     * When mock location is active the providers return the mock fix, so this is most
     * useful before the service starts (e.g. to set a sensible initial map center).
     */
    @Suppress("MissingPermission")
    private fun getRealLocation(): LatLng? {
        if (!hasLocation.value) return null
        val lm = getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return LatLng(loc.latitude, loc.longitude)
        }
        return null
    }
}
