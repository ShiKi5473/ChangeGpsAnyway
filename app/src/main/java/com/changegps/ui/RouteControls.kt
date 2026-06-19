package com.changegps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.changegps.mock.RouteLoop

/**
 * Route mode controls: tap the map to add waypoints, set speed and loop mode.
 */
@Composable
fun RouteControls(
    waypointCount: Int,
    speedKmh: Double,
    loop: RouteLoop,
    onSpeedChange: (Double) -> Unit,
    onLoopChange: (RouteLoop) -> Unit,
    onRemoveLast: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("點地圖新增路徑點（已有 $waypointCount 點，需 ≥2 點）")

        Text("時速：%.1f km/h".format(speedKmh), modifier = Modifier.padding(top = 6.dp))
        Slider(
            value = speedKmh.toFloat(),
            onValueChange = { onSpeedChange(it.toDouble()) },
            valueRange = 1f..30f,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteLoop.entries.forEach { mode ->
                FilterChip(
                    selected = loop == mode,
                    onClick = { onLoopChange(mode) },
                    label = {
                        Text(
                            when (mode) {
                                RouteLoop.ONCE -> "單次"
                                RouteLoop.LOOP -> "循環"
                                RouteLoop.PINGPONG -> "來回"
                            }
                        )
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRemoveLast, modifier = Modifier.weight(1f)) {
                Text("移除最後一點")
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("清除全部")
            }
        }
    }
}
