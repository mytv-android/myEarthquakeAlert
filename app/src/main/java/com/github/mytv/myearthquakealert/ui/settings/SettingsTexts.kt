package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import com.github.mytv.myearthquakealert.data.model.LocationMode
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.diagnostics.HealthMetrics
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutSource
import com.github.mytv.myearthquakealert.ui.adaptive.Posture
import com.github.mytv.myearthquakealert.ui.adaptive.ResolvedLayout
import java.util.Locale

internal fun requirementIcon(requirement: DeliveryRequirement): ImageVector = when (requirement) {
    DeliveryRequirement.NOTIFICATIONS -> Icons.Filled.Notifications
    DeliveryRequirement.OVERLAY -> Icons.Filled.Layers
    DeliveryRequirement.FULL_SCREEN_INTENT -> Icons.Filled.CrisisAlert
    DeliveryRequirement.LOCATION -> Icons.Filled.MyLocation
    DeliveryRequirement.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryFull
    DeliveryRequirement.DND_ACCESS -> Icons.Filled.DoNotDisturbOn
    DeliveryRequirement.AUTO_START -> Icons.Filled.RocketLaunch
}

/** 开机自动启动 subtitle (prototype bootRow). */
@Composable
internal fun bootSubtitle(settings: UserSettings, delaySeconds: Long?): String = when {
    !settings.serviceEnabled -> stringResource(R.string.boot_start_unavailable)
    !settings.bootStart -> stringResource(R.string.boot_start_off)
    delaySeconds != null -> stringResource(R.string.pg_boot_last_auto, delaySeconds)
    else -> stringResource(R.string.boot_start_on_direct)
}

internal fun magnitudeValue(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

@Composable
internal fun magnitudeThreshold(value: Double): String =
    if (value <= 0.0) stringResource(R.string.pg_mag_any) else stringResource(R.string.pg_mag_min, magnitudeValue(value))

@Composable
internal fun intensityThreshold(value: Int): String =
    if (value <= 0) stringResource(R.string.pg_int_any) else stringResource(R.string.pg_int_min, value)

/** 预警阈值 subtitle: "震级 ≥ 3.0 · 本地烈度 ≥ 2 度". */
@Composable
internal fun alertThresholdSummary(settings: UserSettings): String =
    magnitudeThreshold(settings.actionMinMagnitude) + " · " + intensityThreshold(settings.actionMinIntensity)

@Composable
internal fun layoutKindLabel(kind: LayoutKind): String = when (kind) {
    LayoutKind.PHONE -> stringResource(R.string.pg_layout_phone)
    LayoutKind.MEDIUM -> stringResource(R.string.pg_layout_medium)
    LayoutKind.TABLET -> stringResource(R.string.pg_layout_tablet)
    LayoutKind.TV -> stringResource(R.string.pg_layout_tv)
    LayoutKind.KIOSK -> stringResource(R.string.pg_layout_kiosk)
}

@Composable
internal fun overrideLabel(override: LayoutOverride): String = when (override) {
    LayoutOverride.AUTO -> stringResource(R.string.pg_layout_auto)
    LayoutOverride.PHONE -> stringResource(R.string.pg_layout_phone)
    LayoutOverride.TABLET -> stringResource(R.string.pg_layout_tablet)
    LayoutOverride.TV -> stringResource(R.string.pg_layout_tv)
    LayoutOverride.KIOSK -> stringResource(R.string.pg_layout_kiosk)
}

@Composable
internal fun overrideDescription(override: LayoutOverride): String = when (override) {
    LayoutOverride.AUTO -> stringResource(R.string.pg_layout_auto_desc)
    LayoutOverride.PHONE -> stringResource(R.string.pg_layout_phone_desc)
    LayoutOverride.TABLET -> stringResource(R.string.pg_layout_tablet_desc)
    LayoutOverride.TV -> stringResource(R.string.pg_layout_tv_desc)
    LayoutOverride.KIOSK -> stringResource(R.string.pg_layout_kiosk_desc)
}

/** 界面布局 subtitle: "自动 · 当前为手机", "平板", "电视 · 仅本次会话". */
@Composable
internal fun layoutSummary(settings: UserSettings, layout: ResolvedLayout, session: LayoutOverride?): String = when {
    session != null -> stringResource(R.string.pg_layout_session_summary, overrideLabel(session))
    settings.layoutOverride == LayoutOverride.AUTO -> stringResource(R.string.pg_layout_auto_summary, layoutKindLabel(layout.autoKind))
    else -> overrideLabel(settings.layoutOverride)
}

/** "紧凑宽度 · 390 × 870 dp · 竖屏 · 触摸" under 当前自动判定. */
@Composable
internal fun layoutFacts(layout: ResolvedLayout): String {
    val parts = buildList {
        add(
            stringResource(
                when {
                    layout.widthDp < 600 -> R.string.pg_width_compact
                    layout.widthDp < 840 -> R.string.pg_width_medium
                    else -> R.string.pg_width_expanded
                }
            )
        )
        if (layout.widthDp > 0 && layout.heightDp > 0) add(stringResource(R.string.pg_layout_size, layout.widthDp, layout.heightDp))
        add(
            stringResource(
                when (layout.posture) {
                    Posture.BOOK -> R.string.pg_posture_book
                    Posture.TABLETOP -> R.string.pg_posture_tabletop
                    Posture.DUAL -> R.string.pg_posture_dual
                    Posture.FLAT -> if (layout.heightDp >= layout.widthDp) R.string.pg_posture_portrait else R.string.pg_posture_landscape
                }
            )
        )
        add(
            stringResource(
                when {
                    layout.isTelevision -> R.string.pg_input_remote
                    layout.isTouch -> R.string.pg_input_touch
                    else -> R.string.pg_input_pointer
                }
            )
        )
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun layoutSourceLabel(source: LayoutSource): String = when (source) {
    LayoutSource.AUTO -> stringResource(R.string.pg_layout_source_auto)
    LayoutSource.SETTINGS -> stringResource(R.string.pg_layout_source_settings)
    LayoutSource.SESSION -> stringResource(R.string.pg_layout_source_session)
}

/** "gps" / "network" / "manual" / "ip" / "none" from [HealthMetrics.locationSource]. */
@Composable
internal fun locationSourceLabel(source: String?): String = when (source?.lowercase(Locale.ROOT)) {
    "gps" -> stringResource(R.string.pg_loc_gps)
    "network" -> stringResource(R.string.pg_loc_network)
    "manual" -> stringResource(R.string.pg_loc_manual)
    "ip" -> stringResource(R.string.pg_loc_ip)
    null, "", "none" -> stringResource(R.string.pg_loc_none)
    else -> source.orEmpty()
}

/** 位置来源 subtitle: "自动 · GPS，失败时用 IP" or "手动 · 成都". */
@Composable
internal fun locationSummary(settings: UserSettings, metrics: HealthMetrics): String = when {
    settings.locationMode == LocationMode.MANUAL && settings.hasManualLocation -> {
        val name = settings.manualLocationName.ifBlank {
            coordinateText(settings.manualLatitude ?: 0.0, settings.manualLongitude ?: 0.0)
        }
        stringResource(R.string.pg_loc_summary_manual, name)
    }
    settings.locationMode == LocationMode.MANUAL -> stringResource(R.string.pg_loc_summary_manual_unset)
    metrics.locationSource != null && metrics.locationSource != "none" ->
        stringResource(R.string.pg_loc_summary_auto_now, locationSourceLabel(metrics.locationSource))
    else -> stringResource(R.string.pg_loc_summary_auto)
}

internal fun coordinateText(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude)

/** 工具 › 诊断 subtitle: "服务正常" or the phase in short. */
@Composable
internal fun diagnosticsSummary(phase: MonitorPhase, short: String): String =
    if (phase == MonitorPhase.MONITORING) stringResource(R.string.pg_diag_ok) else short
