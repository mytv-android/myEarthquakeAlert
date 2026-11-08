package com.github.mytv.myearthquakealert.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CrisisAlert
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.ui.theme.status
import kotlin.math.ceil

/**
 * The phase every surface renders. The service is authoritative while it runs; around a
 * command (switching on, pausing, resuming) the stored settings answer first, so the
 * overview reacts immediately instead of waiting for the service to report.
 */
fun effectivePhase(state: MonitorState, settings: UserSettings, now: Long): MonitorPhase = when {
    !settings.serviceEnabled -> MonitorPhase.OFF
    settings.isPaused(now) -> MonitorPhase.PAUSED
    state.phase == MonitorPhase.OFF || state.phase == MonitorPhase.PAUSED -> MonitorPhase.CONNECTING
    else -> state.phase
}

/** Title, subtitle and short label of a phase (design STATES table). */
@Immutable
data class MonitorTexts(val title: String, val subtitle: String, val short: String)

@Composable
fun monitorTexts(phase: MonitorPhase, state: MonitorState, settings: UserSettings, now: Long): MonitorTexts {
    val sourceCount = state.sources.size.takeIf { it > 0 } ?: settings.enabledSources.size
    return when (phase) {
        MonitorPhase.MONITORING -> MonitorTexts(
            title = stringResource(R.string.ui_state_on),
            subtitle = state.runningSince?.let {
                stringResource(R.string.ui_state_on_sub_running, sourceCount, durationText(now - it))
            } ?: stringResource(R.string.ui_state_on_sub, sourceCount),
            short = stringResource(R.string.ui_state_on_short),
        )
        MonitorPhase.CONNECTING -> MonitorTexts(
            title = stringResource(R.string.ui_state_connecting),
            subtitle = stringResource(R.string.ui_state_connecting_sub, sourceCount),
            short = stringResource(R.string.ui_state_connecting_short),
        )
        MonitorPhase.RECONNECTING -> {
            val attempt = state.sources.maxOfOrNull { it.reconnectAttempt } ?: 0
            val next = state.sources.mapNotNull { it.nextRetryAt }.minOrNull()
            MonitorTexts(
                title = stringResource(R.string.ui_state_retry),
                subtitle = if (attempt > 0 && next != null && next > now) {
                    stringResource(R.string.ui_state_retry_sub, attempt, ceil((next - now) / 1000.0).toInt())
                } else {
                    stringResource(R.string.ui_state_retry_sub_generic)
                },
                short = stringResource(R.string.ui_state_retry_short),
            )
        }
        MonitorPhase.DEGRADED -> MonitorTexts(
            title = stringResource(R.string.ui_state_degraded),
            subtitle = stringResource(R.string.ui_state_degraded_sub),
            short = stringResource(R.string.ui_state_degraded),
        )
        MonitorPhase.LIMITED -> {
            val required = state.missing.filter { it.required }.sortedBy { it.ordinal }
            MonitorTexts(
                title = stringResource(R.string.ui_state_limited),
                subtitle = if (required.isEmpty()) {
                    stringResource(R.string.ui_state_limited_sub_generic)
                } else {
                    stringResource(R.string.ui_state_limited_sub, required.map { requirementShort(it) }.joinToString(stringResource(R.string.ui_list_separator)))
                },
                short = stringResource(R.string.ui_state_limited),
            )
        }
        MonitorPhase.PAUSED -> {
            val until = untilText(settings.pauseUntil.takeIf { it > 0 } ?: state.pauseUntil, now)
            MonitorTexts(
                title = stringResource(R.string.ui_state_paused, until),
                subtitle = stringResource(R.string.ui_state_paused_sub),
                short = stringResource(R.string.ui_state_paused_short, until),
            )
        }
        MonitorPhase.OFF -> MonitorTexts(
            title = stringResource(R.string.ui_state_off),
            subtitle = stringResource(R.string.ui_state_off_sub),
            short = stringResource(R.string.ui_state_off_short),
        )
    }
}

/** Container / content colours of the status card for [phase] (design .mon.*). */
@Immutable
data class PhaseCardColors(val container: Color, val content: Color)

@Composable
fun phaseCardColors(phase: MonitorPhase): PhaseCardColors {
    val c = MaterialTheme.colorScheme
    val s = MaterialTheme.status
    return when (phase) {
        MonitorPhase.MONITORING -> PhaseCardColors(s.okContainer, s.onOkContainer)
        MonitorPhase.CONNECTING -> PhaseCardColors(c.primaryContainer, c.onPrimaryContainer)
        MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED, MonitorPhase.PAUSED -> PhaseCardColors(s.warnContainer, s.onWarnContainer)
        MonitorPhase.LIMITED -> PhaseCardColors(c.errorContainer, c.onErrorContainer)
        MonitorPhase.OFF -> PhaseCardColors(c.surfaceContainerHighest, c.onSurface)
    }
}

/** Dot colour of a source link. */
@Composable
fun linkColor(link: LinkState): Color = when (link) {
    LinkState.CONNECTED -> MaterialTheme.status.ok
    LinkState.POLLING, LinkState.RECONNECTING, LinkState.CONNECTING -> MaterialTheme.status.warn
    LinkState.DISCONNECTED -> MaterialTheme.colorScheme.error
}

/** "cenc_eew" — the feed name of a source, as the connecting pill lists them. */
val EewSource.feedName: String get() = wsUrl.substringAfterLast('/')

@Composable
fun requirementTitle(requirement: DeliveryRequirement): String = stringResource(
    when (requirement) {
        DeliveryRequirement.NOTIFICATIONS -> R.string.ui_req_notifications
        DeliveryRequirement.OVERLAY -> R.string.ui_req_overlay
        DeliveryRequirement.FULL_SCREEN_INTENT -> R.string.ui_req_full_screen
        DeliveryRequirement.LOCATION -> R.string.ui_req_location
        DeliveryRequirement.BATTERY_OPTIMIZATION -> R.string.ui_req_battery
        DeliveryRequirement.DND_ACCESS -> R.string.ui_req_dnd
        DeliveryRequirement.AUTO_START -> R.string.ui_req_auto_start
    },
)

@Composable
fun requirementDescription(requirement: DeliveryRequirement): String = stringResource(
    when (requirement) {
        DeliveryRequirement.NOTIFICATIONS -> R.string.ui_req_notifications_desc
        DeliveryRequirement.OVERLAY -> R.string.ui_req_overlay_desc
        DeliveryRequirement.FULL_SCREEN_INTENT -> R.string.ui_req_full_screen_desc
        DeliveryRequirement.LOCATION -> R.string.ui_req_location_desc
        DeliveryRequirement.BATTERY_OPTIMIZATION -> R.string.ui_req_battery_desc
        DeliveryRequirement.DND_ACCESS -> R.string.ui_req_dnd_desc
        DeliveryRequirement.AUTO_START -> R.string.ui_req_auto_start_desc
    },
)

@Composable
fun requirementShort(requirement: DeliveryRequirement): String = stringResource(
    when (requirement) {
        DeliveryRequirement.NOTIFICATIONS -> R.string.ui_req_notifications_short
        DeliveryRequirement.OVERLAY -> R.string.ui_req_overlay_short
        DeliveryRequirement.FULL_SCREEN_INTENT -> R.string.ui_req_full_screen_short
        DeliveryRequirement.LOCATION -> R.string.ui_req_location_short
        DeliveryRequirement.BATTERY_OPTIMIZATION -> R.string.ui_req_battery_short
        DeliveryRequirement.DND_ACCESS -> R.string.ui_req_dnd_short
        DeliveryRequirement.AUTO_START -> R.string.ui_req_auto_start_short
    },
)

fun requirementIcon(requirement: DeliveryRequirement): ImageVector = when (requirement) {
    DeliveryRequirement.NOTIFICATIONS -> Icons.Outlined.Notifications
    DeliveryRequirement.OVERLAY -> Icons.Outlined.Layers
    DeliveryRequirement.FULL_SCREEN_INTENT -> Icons.Outlined.CrisisAlert
    DeliveryRequirement.LOCATION -> Icons.Outlined.MyLocation
    DeliveryRequirement.BATTERY_OPTIMIZATION -> Icons.Outlined.BatteryFull
    DeliveryRequirement.DND_ACCESS -> Icons.Outlined.DoNotDisturbOn
    DeliveryRequirement.AUTO_START -> Icons.Outlined.RocketLaunch
}
