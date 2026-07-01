package com.changegps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.changegps.mock.MockController
import com.changegps.mock.MockState
import com.changegps.mock.Mode
import com.changegps.util.LatLng
import kotlinx.coroutines.delay

/** Distinct green for the "start" action so it contrasts with the red "stop". */
private val StartGreen = Color(0xFF2E7D32)

/** Lambdas the UI needs from the host Activity (permissions, service control). */
data class AppActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onStartOverlay: () -> Unit,
    val onStopOverlay: () -> Unit,
    val onOpenMockSettings: () -> Unit,
    val onOpenOverlaySettings: () -> Unit,
    val onRequestBattery: () -> Unit,
    val onRequestLocation: () -> Unit,
    /** Returns the real device GPS position, or null if unavailable. */
    val onGetRealLocation: () -> LatLng?,
)

@Composable
fun AppScreen(
    state: MockState,
    hasLocation: Boolean,
    hasOverlay: Boolean,
    isIgnoringBattery: Boolean,
    isMockApp: Boolean,
    actions: AppActions,
) {
    var centerRequest by remember { mutableStateOf<LatLng?>(null) }

    // systemBarsPadding: keep zones clear of the status/navigation bars.
    // imePadding: lift the bottom controls above the soft keyboard while typing.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        // ============ TOP ZONE — mode tabs (fixed height) ============
        // zIndex keeps the tabs drawn ABOVE the native map view, which can briefly
        // paint outside its bounds while loading tiles.
        TabRow(
            selectedTabIndex = state.mode.ordinal,
            modifier = Modifier.zIndex(1f),
        ) {
            ModeTab("傳送/飛人", state.mode == Mode.TELEPORT) { MockController.setMode(Mode.TELEPORT) }
            ModeTab("路線/種花", state.mode == Mode.ROUTE) { MockController.setMode(Mode.ROUTE) }
            ModeTab("搖桿", state.mode == Mode.JOYSTICK) { MockController.setMode(Mode.JOYSTICK) }
        }

        // ============ MIDDLE ZONE — map (flexes to fill remaining space) ============
        // clipToBounds prevents the heavyweight osmdroid view from overdrawing the
        // tabs above it during measurement / tile rendering.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            OsmMap(
                emitted = state.emitted,
                waypoints = state.waypoints,
                centerOn = centerRequest,
                target = if (state.mode == Mode.TELEPORT) state.teleportTarget else null,
                onTap = { p ->
                    when (state.mode) {
                        Mode.TELEPORT -> {
                            MockController.setTeleportTarget(p)
                            centerRequest = p
                        }
                        Mode.ROUTE -> MockController.addWaypoint(p)
                        Mode.JOYSTICK -> {
                            MockController.setTeleportTarget(p)
                            MockController.setEmitted(p)
                            centerRequest = p
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Setup checklist / error float OVER the map so they never push the
            // tabs or controls out of their zones. Auto-hides when setup is done.
            Column(modifier = Modifier.align(Alignment.TopCenter)) {
                state.error?.let { msg ->
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(msg, modifier = Modifier.padding(12.dp))
                    }
                }
                SetupScreen(
                    hasLocation = hasLocation,
                    isMockApp = isMockApp,
                    hasOverlay = hasOverlay,
                    isIgnoringBattery = isIgnoringBattery,
                    onRequestLocation = actions.onRequestLocation,
                    onOpenMockSettings = actions.onOpenMockSettings,
                    onOpenOverlay = actions.onOpenOverlaySettings,
                    onRequestBattery = actions.onRequestBattery,
                )
            }

            FloatingActionButton(
                onClick = {
                    // If mock is running, jump to the current simulated position;
                    // otherwise get the real device GPS.
                    val target = state.emitted ?: actions.onGetRealLocation()
                    if (target != null) centerRequest = target
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(48.dp),
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "定位到目前位置")
            }
        }

        // ============ BOTTOM ZONE — mode controls (bounded + scrollable) ============
        Surface(tonalElevation = 3.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                when (state.mode) {
                    Mode.TELEPORT -> TeleportControls(
                        emitted = state.emitted,
                        onGo = { p ->
                            MockController.setTeleportTarget(p)
                            centerRequest = p
                        },
                    )
                    Mode.ROUTE -> RouteControls(
                        waypointCount = state.waypoints.size,
                        speedKmh = state.speedKmh,
                        loop = state.routeLoop,
                        onSpeedChange = { MockController.setSpeedKmh(it) },
                        onLoopChange = { MockController.setRouteLoop(it) },
                        onRemoveLast = { MockController.removeLastWaypoint() },
                        onClear = { MockController.clearWaypoints() },
                    )
                    Mode.JOYSTICK -> JoystickControls(
                        maxSpeedKmh = state.joystickSpeedKmh,
                        currentSpeedKmh = state.currentSpeedKmh,
                        onMaxSpeedChange = { MockController.setJoystickSpeedKmh(it) },
                    )
                }

                PushIntervalControls(
                    tickMs = state.tickMs,
                    onSelect = { MockController.setTickMs(it) },
                )

                CooldownBar(state)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.running) {
                        Button(
                            onClick = actions.onStop,
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("停止模擬", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Button(
                            onClick = actions.onStart,
                            enabled = hasLocation,
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StartGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("開始模擬", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (state.mode == Mode.JOYSTICK) {
                        OutlinedButton(
                            onClick = if (hasOverlay) actions.onStartOverlay else actions.onOpenOverlaySettings,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(if (hasOverlay) "開啟搖桿" else "授權懸浮窗") }
                    }
                }
            }
        }
    }
}

/**
 * Push-interval selector: three presets (1000 / 250 / 100 ms) plus a manual field.
 * Lower = smoother motion & faster teleport; higher = more natural / battery-friendly.
 */
@Composable
private fun PushIntervalControls(tickMs: Long, onSelect: (Long) -> Unit) {
    var manual by remember(tickMs) { mutableStateOf(tickMs.toString()) }
    val presets = listOf(1000L, 250L, 100L)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("推送間隔（越小越即時，越大越省電/自然）")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                FilterChip(
                    selected = tickMs == p,
                    onClick = { onSelect(p) },
                    label = { Text("${p}ms") },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it.filter(Char::isDigit).take(4) },
                label = { Text("手動 (50–5000 ms)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { manual.toLongOrNull()?.let(onSelect) }) { Text("套用") }
        }
    }
}

@Composable
private fun JoystickControls(
    maxSpeedKmh: Double,
    currentSpeedKmh: Double,
    onMaxSpeedChange: (Double) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("先點地圖設定起點，再開啟懸浮搖桿，即可在其他 App 上移動定位。")
        Text(
            "目前速度：%.1f km/h".format(currentSpeedKmh),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("最高速度（搖桿推到底）：%.1f km/h".format(maxSpeedKmh))
        Slider(
            value = maxSpeedKmh.toFloat(),
            onValueChange = { onMaxSpeedChange(it.toDouble()) },
            valueRange = 1f..30f,
        )
        Text("種花時請讓手機保持輕微晃動以產生步數。")
    }
}

/** Tab whose label stays on one line so it never wraps/clips on narrow screens. */
@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun CooldownBar(state: MockState) {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = remember(now, state.lastTeleportAt, state.lastTeleportDistanceM) {
        MockController.cooldownRemainingMs(now)
    }
    if (remaining > 0) {
        val totalSec = remaining / 1000
        val text = "建議冷卻：%02d:%02d（剛傳送 %.1f km，太快移動易觸發軟封鎖）"
            .format(totalSec / 60, totalSec % 60, state.lastTeleportDistanceM / 1000.0)
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(text, modifier = Modifier.padding(12.dp))
        }
    }
}
