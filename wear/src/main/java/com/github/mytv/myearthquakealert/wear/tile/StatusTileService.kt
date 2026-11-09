package com.github.mytv.myearthquakealert.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.CardColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.data.WatchPhase
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.domain.Countdown
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import com.github.mytv.myearthquakealert.wear.ui.MainActivity
import com.github.mytv.myearthquakealert.wear.alert.AlertActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers

/**
 * The 地震预警 tile: the monitoring phase (from the phone, or standalone) and the latest
 * quake; during an alert, the alert. Tapping opens the app (or the alert screen). The tile
 * is refreshed by [com.github.mytv.myearthquakealert.wear.WearSurfaces] whenever the store
 * changes, and every 15 minutes otherwise (relative times, pause expiry).
 */
class StatusTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            WatchStore.init(this@StatusTileService)
            val layout = layout(this@StatusTileService, requestParams.deviceConfiguration)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MS = 15 * 60_000L

        internal fun layout(context: Context, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
            val now = System.currentTimeMillis()
            val alert = WatchStore.alert.value?.takeIf { Countdown.snapshot(it, now).stage != Countdown.Stage.EXPIRED }
            val phase = WatchStore.phase(now)
            val status = WatchStore.status.value
            val quake = WatchStore.quakes.value.firstOrNull()
            val openApp = openAction(context, if (alert != null) AlertActivity::class.java else MainActivity::class.java, "open")
            val tone = TileTone.of(if (alert != null) null else phase)

            return materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = SCHEME) {
                primaryLayout(
                    titleSlot = { text(context.getString(R.string.app_name).layoutString) },
                    mainSlot = {
                        titleCard(
                            onClick = openApp,
                            colors = CardColors(
                                backgroundColor = tone.container,
                                titleColor = tone.onContainer,
                                contentColor = tone.onContainer,
                            ),
                            title = {
                                text(
                                    (if (alert != null) alertTitle(context, alert.drill, alert.magnitude) else PhaseText.short(context, phase, status)).layoutString,
                                    typography = Typography.TITLE_MEDIUM,
                                    maxLines = 1,
                                )
                            },
                            content = {
                                val line = when {
                                    alert != null -> PhaseText.shortPlace(alert.place, 10)
                                    quake != null -> context.getString(R.string.tile_latest, PhaseText.quakeChip(quake), PhaseText.ago(quake, now))
                                    else -> PhaseText.sub(context, phase, status, WatchStore.standalone.value)
                                }
                                text(line.layoutString, typography = Typography.BODY_SMALL, maxLines = 2)
                            },
                        )
                    },
                    bottomSlot = {
                        textEdgeButton(
                            onClick = openApp,
                            colors = ButtonColors(
                                containerColor = if (alert != null) 0xFFFFB4AB.toInt().argb else 0xFFA8C8F0.toInt().argb,
                                labelColor = if (alert != null) 0xFF5C0008.toInt().argb else 0xFF0B2440.toInt().argb,
                            ),
                        ) {
                            text(context.getString(if (alert != null) R.string.tile_view_alert else R.string.tile_open).layoutString)
                        }
                    },
                )
            }
        }

        private fun alertTitle(context: Context, drill: Boolean, magnitude: Double): String =
            buildString {
                if (drill) append(context.getString(R.string.alert_drill_prefix))
                append(context.getString(R.string.tile_alert, PhaseText.magnitude(magnitude)))
            }

        private fun openAction(context: Context, activity: Class<*>, id: String): Clickable =
            clickable(ActionBuilders.launchAction(ComponentName(context, activity)), id)

        private val SCHEME = ColorScheme(
            primary = 0xFF9BCBF9.toInt().argb,
            onPrimary = 0xFF00325A.toInt().argb,
            primaryContainer = 0xFF174A73.toInt().argb,
            onPrimaryContainer = 0xFFD2E4F7.toInt().argb,
            surfaceContainer = 0xFF1F2429.toInt().argb,
            onSurface = 0xFFE8EAED.toInt().argb,
            onSurfaceVariant = 0xFFAEB4BB.toInt().argb,
            background = 0xFF000000.toInt().argb,
            onBackground = 0xFFE8EAED.toInt().argb,
            error = 0xFFF2B8B5.toInt().argb,
            onError = 0xFF601410.toInt().argb,
            errorContainer = 0xFF8C1D18.toInt().argb,
            onErrorContainer = 0xFFF9DEDC.toInt().argb,
        )
    }
}

/** Tile colours per phase (status tokens of the watch design); null phase = alert. */
private data class TileTone(val container: LayoutColor, val onContainer: LayoutColor) {
    companion object {
        fun of(phase: WatchPhase?): TileTone = when (phase) {
            null -> TileTone(0xFF5C0008.toInt().argb, 0xFFFFC9C4.toInt().argb)
            WatchPhase.MONITORING, WatchPhase.STANDALONE -> TileTone(0xFF00391D.toInt().argb, 0xFF86D8A8.toInt().argb)
            WatchPhase.CONNECTING, WatchPhase.RECONNECTING, WatchPhase.DEGRADED, WatchPhase.LIMITED, WatchPhase.WAITING ->
                TileTone(0xFF452B00.toInt().argb, 0xFFF5C25B.toInt().argb)
            WatchPhase.PAUSED -> TileTone(0xFF174A73.toInt().argb, 0xFFD2E4F7.toInt().argb)
            WatchPhase.DISCONNECTED -> TileTone(0xFF601410.toInt().argb, 0xFFF2B8B5.toInt().argb)
            WatchPhase.OFF -> TileTone(0xFF2F363E.toInt().argb, 0xFFC1C7CE.toInt().argb)
        }
    }
}
