package com.changegps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.changegps.util.LatLng
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * osmdroid [MapView] hosted in Compose. Taps are forwarded as [onTap]; the
 * emitted position is shown as a marker and route waypoints as a polyline.
 *
 * Lifecycle/leak handling (see plan): onResume/onPause are mirrored from the
 * host lifecycle and onDetach() is called when the node leaves the tree.
 */
@Composable
fun OsmMap(
    emitted: LatLng?,
    waypoints: List<LatLng>,
    onTap: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
    centerOn: LatLng? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTap = rememberUpdatedState(onTap)
    val lastCenter = remember { androidx.compose.runtime.mutableStateOf<LatLng?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(CARTO_POSITRON)
            setMultiTouchControls(true)
            isTilesScaledToDpi = false   // skip per-tile DPI scaling — faster draw
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(25.0330, 121.5654)) // Taipei default
        }
    }
    val marker = remember { Marker(mapView) }
    val polyline = remember { Polyline(mapView) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            val receiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    currentOnTap.value(LatLng(p.latitude, p.longitude))
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }
            mapView.overlays.add(MapEventsOverlay(receiver))
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(polyline)
            mapView.overlays.add(marker)
            mapView
        },
        update = { mv ->
            // Route polyline.
            polyline.setPoints(waypoints.map { GeoPoint(it.lat, it.lon) })
            // Current emitted position marker.
            if (emitted != null) {
                marker.position = GeoPoint(emitted.lat, emitted.lon)
                marker.title = "目前模擬位置"
            }
            // Recenter only when a new center is requested.
            if (centerOn != null && centerOn != lastCenter.value) {
                mv.controller.animateTo(GeoPoint(centerOn.lat, centerOn.lon))
                lastCenter.value = centerOn
            }
            mv.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

/**
 * CartoDB Positron — light-grey base, roads + labels only, no terrain/buildings.
 * Tiles are significantly smaller than Mapnik (~60–70 % size reduction) and render
 * faster because there is no colour fill for land/water areas.
 */
private val CARTO_POSITRON = XYTileSource(
    "CartoDB.Positron",
    1, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
    "© OpenStreetMap contributors © CARTO",
)
