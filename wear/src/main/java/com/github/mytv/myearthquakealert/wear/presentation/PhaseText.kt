package com.github.mytv.myearthquakealert.wear.presentation

import android.content.Context
import android.text.format.DateUtils
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.data.StandaloneLink
import com.github.mytv.myearthquakealert.wear.data.StandaloneState
import com.github.mytv.myearthquakealert.wear.data.WatchPhase
import com.github.mytv.myearthquakealert.wear.data.WireQuake
import com.github.mytv.myearthquakealert.wear.data.WireStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Texts and icons of a [WatchPhase], shared by the app, the tile and the complication. */
object PhaseText {

    /** The short state (design: 监测中 / 连接中 / 重连中 / 备用通道 / 受限 / 暂停至 HH:mm / 已关闭). */
    fun short(context: Context, phase: WatchPhase, status: WireStatus?): String = when (phase) {
        WatchPhase.MONITORING -> context.getString(R.string.phase_monitoring)
        WatchPhase.CONNECTING -> context.getString(R.string.phase_connecting)
        WatchPhase.RECONNECTING -> context.getString(R.string.phase_reconnecting)
        WatchPhase.DEGRADED -> context.getString(R.string.phase_degraded)
        WatchPhase.LIMITED -> context.getString(R.string.phase_limited)
        WatchPhase.PAUSED -> context.getString(R.string.phase_paused_until, time(context, status?.pauseUntil ?: 0L))
        WatchPhase.OFF -> context.getString(R.string.phase_off)
        WatchPhase.DISCONNECTED -> context.getString(R.string.phase_disconnected)
        WatchPhase.STANDALONE -> context.getString(R.string.phase_standalone)
        WatchPhase.WAITING -> context.getString(R.string.phase_waiting)
    }

    /** A complication-sized state (≤ 4 CJK characters / one short word). */
    fun tiny(context: Context, phase: WatchPhase): String = when (phase) {
        WatchPhase.MONITORING -> context.getString(R.string.tiny_monitoring)
        WatchPhase.CONNECTING, WatchPhase.RECONNECTING, WatchPhase.WAITING -> context.getString(R.string.tiny_connecting)
        WatchPhase.DEGRADED -> context.getString(R.string.tiny_degraded)
        WatchPhase.LIMITED -> context.getString(R.string.tiny_limited)
        WatchPhase.PAUSED -> context.getString(R.string.tiny_paused)
        WatchPhase.OFF -> context.getString(R.string.tiny_off)
        WatchPhase.DISCONNECTED -> context.getString(R.string.tiny_disconnected)
        WatchPhase.STANDALONE -> context.getString(R.string.tiny_standalone)
    }

    /** The line under the state (design: 4 路数据源 · 手机已连接). */
    fun sub(context: Context, phase: WatchPhase, status: WireStatus?, standalone: StandaloneState): String = when (phase) {
        WatchPhase.MONITORING -> context.getString(R.string.sub_monitoring, status?.connectedCount ?: 0)
        WatchPhase.CONNECTING -> context.getString(R.string.sub_connecting)
        WatchPhase.RECONNECTING -> context.getString(R.string.sub_reconnecting)
        WatchPhase.DEGRADED -> context.getString(R.string.sub_degraded)
        WatchPhase.LIMITED -> context.getString(R.string.sub_limited)
        WatchPhase.PAUSED -> context.getString(R.string.sub_paused)
        WatchPhase.OFF -> context.getString(R.string.sub_off)
        WatchPhase.DISCONNECTED -> when {
            standalone.running && standalone.link != StandaloneLink.IDLE -> context.getString(R.string.sub_standalone_connecting, standalone.source.label)
            standalone.enabled -> context.getString(R.string.sub_disconnected_standalone)
            else -> context.getString(R.string.sub_disconnected)
        }
        WatchPhase.STANDALONE -> if (standalone.hasLocation) {
            context.getString(R.string.sub_standalone, standalone.source.label)
        } else {
            context.getString(R.string.sub_standalone_no_location, standalone.source.label)
        }
        WatchPhase.WAITING -> context.getString(R.string.sub_waiting)
    }

    fun icon(phase: WatchPhase): Int = when (phase) {
        WatchPhase.MONITORING -> R.drawable.ic_sensors
        WatchPhase.STANDALONE -> R.drawable.ic_watch
        WatchPhase.CONNECTING, WatchPhase.RECONNECTING, WatchPhase.WAITING -> R.drawable.ic_sync
        WatchPhase.DEGRADED -> R.drawable.ic_sync_problem
        WatchPhase.LIMITED -> R.drawable.ic_warning
        WatchPhase.PAUSED -> R.drawable.ic_pause
        WatchPhase.OFF -> R.drawable.ic_power
        WatchPhase.DISCONNECTED -> R.drawable.ic_phone_off
    }

    /** 「芦山 M6.0」: the place shortened to fit a chip. */
    fun quakeChip(quake: WireQuake): String = "${shortPlace(quake.place)} M${magnitude(quake.magnitude)}"

    /**
     * The most specific part of a Chinese place name (四川雅安市芦山县 → 芦山), else the place
     * cut to [max] characters.
     */
    fun shortPlace(place: String, max: Int = 6): String {
        val trimmed = place.trim()
        val county = Regex("([^省市区州盟]{2,4})[县区市旗]$").find(trimmed)?.groupValues?.get(1)
        val candidate = county?.takeIf { trimmed.length > it.length + 1 } ?: trimmed
        return if (candidate.length <= max) candidate else candidate.take(max - 1) + "…"
    }

    fun magnitude(m: Double): String = String.format(Locale.ROOT, "%.1f", m)

    fun distance(context: Context, km: Double): String = context.getString(R.string.distance_km, km.roundToInt())

    fun time(context: Context, epochMs: Long): String =
        if (epochMs <= 0L) "--:--" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))

    /** 「3 分钟前」 / 「昨天」 style time of a quake. */
    fun ago(quake: WireQuake, now: Long): CharSequence {
        val at = if (quake.originTime > 0) quake.originTime else quake.receivedAt
        if (at <= 0L) return ""
        return DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    }
}
