package com.changegps.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(
    hasLocation: Boolean,
    isMockApp: Boolean,
    hasOverlay: Boolean,
    isIgnoringBattery: Boolean,
    onRequestLocation: () -> Unit,
    onOpenMockSettings: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allDone = hasLocation && isMockApp && isIgnoringBattery
    // Hide the entire card once all required steps are done (overlay is optional).
    AnimatedVisibility(visible = !allDone, modifier = modifier) {
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("設定步驟", fontWeight = FontWeight.Bold)
                SetupRow(
                    done = hasLocation,
                    title = "1. 精確定位權限",
                    button = "授權",
                    onClick = onRequestLocation,
                )
                SetupRow(
                    done = isMockApp,
                    title = "2. 開發者選項 → 選擇模擬位置 App（選本 App）",
                    button = "開啟",
                    onClick = onOpenMockSettings,
                )
                SetupRow(
                    done = hasOverlay,
                    title = "3. 懸浮視窗權限（搖桿模式需要）",
                    button = "開啟",
                    onClick = onOpenOverlay,
                )
                SetupRow(
                    done = isIgnoringBattery,
                    title = "4. 電池最佳化豁免（長時間移動穩定）",
                    button = "開啟",
                    onClick = onRequestBattery,
                )
            }
        }
    }
}

@Composable
private fun SetupRow(done: Boolean, title: String, button: String, onClick: () -> Unit) {
    // Hide rows that are already done to keep the checklist short.
    AnimatedVisibility(visible = !done) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("•")
            Text(title, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}
