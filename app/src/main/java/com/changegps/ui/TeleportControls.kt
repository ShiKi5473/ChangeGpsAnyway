package com.changegps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.changegps.util.LatLng

/**
 * Teleport mode controls: tap the map, or type a lat/lon and press 前往.
 */
@Composable
fun TeleportControls(
    emitted: LatLng?,
    onGo: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("點地圖任一點即傳送，或輸入座標：")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                label = { Text("緯度 lat") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                label = { Text("經度 lon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                val la = lat.trim().toDoubleOrNull()
                val lo = lon.trim().toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    err = "座標格式不正確"
                } else {
                    err = null
                    onGo(LatLng(la, lo))
                }
            }) { Text("前往") }
        }
        err?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
        emitted?.let {
            Text(
                "目前：%.6f, %.6f".format(it.lat, it.lon),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
