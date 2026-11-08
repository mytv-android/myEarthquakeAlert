package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.components.rememberAnimationsEnabled
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle

/**
 * Floating alert card (prototype fcardHTML): shown over whatever is in use for the REMIND
 * tier and in narrow overlay windows. Red head, countdown, intensity burst, epicenter, a
 * mini map, a depleting wavy line and 详情 / 我已安全.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AlertCard(
    alert: AlertData,
    timeline: AlertTimeline,
    muted: Boolean,
    showMute: Boolean,
    mapStyle: EewMapStyle,
    intenseThreshold: Int,
    onSafe: () -> Unit,
    onClose: () -> Unit,
    onDetails: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(30.dp)
    val level = csisLevel(alert.localCsis)
    val animations = rememberAnimationsEnabled()
    val density = LocalDensity.current
    val lineStroke = remember(density) { Stroke(width = with(density) { 4.dp.toPx() }, cap = StrokeCap.Round) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AlertColors.Deep)
            .border(1.dp, AlertColors.Line, shape),
    ) {
        // ---- head
        Row(
            Modifier
                .fillMaxWidth()
                .background(AlertColors.Red)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.CrisisAlert, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.pg_alert_card_title),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (alert.isSimulation) DrillTag(height = 20.dp)
            Spacer(Modifier.weight(1f))
            Text(
                reportLabel(alert.event),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp).alertFocus(CircleShape)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.pg_alert_close),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ---- countdown · intensity and epicenter · mini map
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val countdownDescription = if (timeline.sArrived) {
                stringResource(R.string.pg_alert_arrived_desc)
            } else {
                stringResource(R.string.pg_alert_countdown_desc, timeline.sCountdown)
            }
            Column(
                Modifier
                    .widthIn(min = 88.dp)
                    .clearAndSetSemantics { contentDescription = countdownDescription },
            ) {
                Text(countdownLabel(timeline), color = AlertColors.CardLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (timeline.sArrived) {
                    val size = fixedSp(24.dp)
                    Text(
                        stringResource(R.string.pg_alert_arrived),
                        color = Color.White,
                        fontSize = size,
                        lineHeight = size * 1.25f,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                } else {
                    CountdownNumber(timeline.sCountdown, 58.dp, 15.dp, Color.White)
                }
                Text(
                    pWaveLabel(timeline),
                    color = AlertColors.CardLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntensityBurst(level, 42.dp)
                    Column {
                        Text(
                            stringResource(R.string.pg_alert_intensity_value, level),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            intensityDescription(level),
                            color = AlertColors.CardText,
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val depth = depthLabel(alert.event)
                val distance = stringResource(R.string.pg_alert_distance_you, kmText(alert.distanceKm))
                Text(
                    buildAnnotatedString {
                        if (alert.event.hypocenter.isNotBlank()) {
                            append(alert.event.hypocenter)
                            append(' ')
                        }
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                            append(magnitudeText(alert.event.magnitude))
                        }
                        depth?.let { append(" · "); append(it) }
                        append('\n')
                        append(distance)
                    },
                    color = AlertColors.CardText,
                    fontSize = 12.5.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AlertMap(
                alert = alert,
                timeline = timeline,
                style = mapStyle,
                intenseThreshold = intenseThreshold,
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(20.dp),
                border = null,
                legend = false,
                showAttribution = false,
                fitPaddingDp = 10,
            )
        }

        // ---- depleting wavy line (prototype wline(.., 'al'))
        LinearWavyProgressIndicator(
            progress = { timeline.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp)
                .height(14.dp),
            color = AlertColors.Yellow,
            trackColor = AlertColors.LineTrack,
            stroke = lineStroke,
            trackStroke = lineStroke,
            gapSize = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
            stopSize = 0.dp,
            amplitude = if (animations) WavyProgressIndicatorDefaults.indicatorAmplitude else { _ -> 0f },
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            waveSpeed = if (animations) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
        )

        // ---- actions
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlineAlertButton(
                label = stringResource(R.string.pg_alert_details),
                onClick = onDetails,
                modifier = Modifier.weight(1f),
                height = 44.dp,
                fontSize = 14.sp,
            )
            if (showMute) MuteButton(muted = muted, onClick = onMute, size = 44.dp)
            SafeButton(
                onClick = onSafe,
                modifier = Modifier.weight(1f),
                height = 44.dp,
                fontSize = 14.sp,
                showIcon = false,
                focusRequester = focusRequester,
            )
        }
    }
}
