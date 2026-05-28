package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.PWaveBlue
import com.github.mytv.myearthquakealert.ui.theme.SWaveRed
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun AlertMap(
    alertData: AlertData,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val timer = Thread {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(1000)
                elapsedSeconds += 1f
            }
        }
        timer.start()
        onDispose { timer.interrupt() }
    }

    AndroidView(
        factory = { context ->
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
            MapView(context).apply {
                setMultiTouchControls(false)
                controller.setZoom(6.0)
            }
        },
        update = { mapView ->
            val epicenter = GeoPoint(alertData.event.latitude, alertData.event.longitude)
            val device = GeoPoint(alertData.userLatitude, alertData.userLongitude)

            mapView.controller.setCenter(epicenter)

            mapView.overlays.clear()

            val epicenterMarker = Marker(mapView).apply {
                position = epicenter
                title = "震心"
            }
            mapView.overlays.add(epicenterMarker)

            val deviceMarker = Marker(mapView).apply {
                position = device
                title = "设备"
            }
            mapView.overlays.add(deviceMarker)

            val depthKm = alertData.event.depth ?: 10.0
            val pWaveKm = com.github.mytv.myearthquakealert.domain.SeismicCalculator.calcWaveRadius(
                depthKm, elapsedSeconds.toDouble(), true
            )
            val sWaveKm = com.github.mytv.myearthquakealert.domain.SeismicCalculator.calcWaveRadius(
                depthKm, elapsedSeconds.toDouble(), false
            )

            if (pWaveKm > 0) {
                val pCircle = Polygon(mapView).apply {
                    points = Polygon.pointsAsCircle(epicenter, pWaveKm * 1000.0)
                    fillPaint.color = PWaveBlue.copy(alpha = 0.2f).hashCode()
                    outlinePaint.color = PWaveBlue.hashCode()
                    outlinePaint.strokeWidth = 2f
                }
                mapView.overlays.add(pCircle)
            }

            if (sWaveKm > 0) {
                val sCircle = Polygon(mapView).apply {
                    points = Polygon.pointsAsCircle(epicenter, sWaveKm * 1000.0)
                    fillPaint.color = SWaveRed.copy(alpha = 0.2f).hashCode()
                    outlinePaint.color = SWaveRed.hashCode()
                    outlinePaint.strokeWidth = 2f
                }
                mapView.overlays.add(sCircle)
            }

            mapView.invalidate()
        },
        modifier = modifier,
    )
}
