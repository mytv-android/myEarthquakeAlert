package com.github.mytv.myearthquakealert.widget

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.domain.ReportTime
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.EewRecord
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.ui.components.formatKm
import com.github.mytv.myearthquakealert.ui.components.formatMagnitude
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import com.github.mytv.myearthquakealert.ui.settings.components.displayPhase
import com.github.mytv.myearthquakealert.ui.settings.components.pauseEnd
import com.github.mytv.myearthquakealert.ui.theme.contentColorOn
import com.github.mytv.myearthquakealert.ui.theme.csisColor

/** The one action the widget offers for the current phase. */
internal enum class WidgetAction { PAUSE_HOUR, RESUME, TURN_ON }

/** One early-warning record as the widget shows it. */
internal data class WidgetQuake(
    val magnitude: String,
    val place: String,
    /** "3 分钟前 · 距你 86 km". */
    val meta: String,
    val badge: Color,
    val onBadge: Color,
    val description: String,
)

/** Everything the widget draws, built from the service state, settings and EewHistory. */
internal data class WidgetModel(
    val phase: MonitorPhase,
    /** "后台监测中" / "已暂停至 11:12". */
    val title: String,
    /** "监测中" / "暂停至 11:12". */
    val short: String,
    /** "4 路数据源已连接". */
    val subtitle: String,
    val quakes: List<WidgetQuake>,
    val action: WidgetAction,
) {
    companion object {
        const val MAX_QUAKES = 3

        fun build(
            res: Resources,
            formats: Formats,
            state: MonitorState,
            settings: UserSettings,
            records: List<EewRecord>,
            now: Long,
        ): WidgetModel {
            val phase = displayPhase(state, settings, now)
            val pauseUntil = pauseEnd(state, settings)
            val title = when (phase) {
                MonitorPhase.MONITORING -> res.getString(R.string.pg_phase_monitoring)
                MonitorPhase.CONNECTING -> res.getString(R.string.pg_phase_connecting)
                MonitorPhase.RECONNECTING -> res.getString(R.string.pg_phase_reconnecting)
                MonitorPhase.DEGRADED -> res.getString(R.string.pg_phase_degraded)
                MonitorPhase.LIMITED -> res.getString(R.string.pg_phase_limited)
                MonitorPhase.PAUSED -> if (pauseUntil > now) {
                    res.getString(R.string.pg_phase_paused_until, formats.clockWithDay(pauseUntil, now))
                } else {
                    res.getString(R.string.pg_phase_paused)
                }
                MonitorPhase.OFF -> res.getString(R.string.pg_phase_off)
            }
            // No running clocks on a widget: no uptime, no retry countdown.
            val subtitle = when (phase) {
                MonitorPhase.MONITORING -> res.getString(
                    R.string.pg_sub_monitoring,
                    state.connectedCount.takeIf { it > 0 } ?: settings.enabledSources.size,
                )
                MonitorPhase.RECONNECTING -> res.getString(R.string.pg_sub_reconnecting_generic)
                else -> formats.phaseSubtitle(phase, state, settings, now)
            }
            val action = when (phase) {
                MonitorPhase.OFF -> WidgetAction.TURN_ON
                MonitorPhase.PAUSED -> WidgetAction.RESUME
                else -> WidgetAction.PAUSE_HOUR
            }
            return WidgetModel(
                phase = phase,
                title = title,
                short = formats.phaseShort(phase, pauseUntil, now),
                subtitle = subtitle,
                quakes = records.take(MAX_QUAKES).map { quake(res, it, now) },
                action = action,
            )
        }

        private fun quake(res: Resources, record: EewRecord, now: Long): WidgetQuake {
            val event = record.event
            val time = ReportTime.parseCst(event.originTime) ?: record.firstReceivedAt
            val ago = ago(res, now - time)
            val meta = if (record.distanceKm > 0.0) res.getString(R.string.wg_quake_meta, ago, formatKm(record.distanceKm)) else ago
            // Coloured by the epicentral intensity, as the app's quake rows and map dots.
            val intensity = event.maxIntensity.takeIf { it > 0.0 }
                ?: SeismicCalculator.calcLocalIntensity(event.magnitude, event.depth ?: DEFAULT_DEPTH_KM, 0.0)
            val badge = csisColor(intensity)
            val magnitude = formatMagnitude(event.magnitude)
            return WidgetQuake(
                magnitude = res.getString(R.string.wg_magnitude, magnitude),
                place = event.hypocenter,
                meta = meta,
                badge = badge,
                onBadge = contentColorOn(badge),
                description = res.getString(R.string.wg_quake_cd, magnitude, event.hypocenter, meta),
            )
        }

        fun ago(res: Resources, ageMs: Long): String {
            val age = RelativeTime.of(ageMs)
            return when (age.unit) {
                RelativeTime.Unit.NOW -> res.getString(R.string.wg_ago_now)
                RelativeTime.Unit.MINUTES -> res.getString(R.string.wg_ago_minutes, age.value.toInt())
                RelativeTime.Unit.HOURS -> res.getString(R.string.wg_ago_hours, age.value.toInt())
                RelativeTime.Unit.DAYS -> res.getString(R.string.wg_ago_days, age.value.toInt())
            }
        }

        private const val DEFAULT_DEPTH_KM = 10.0
    }
}
