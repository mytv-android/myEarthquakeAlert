package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.theme.BroadcastBlue
import com.github.mytv.myearthquakealert.ui.theme.BroadcastInk

/**
 * TV reminder (prototype scrTvAlert): the programme keeps playing; a broadcast-style
 * subtitle bar sits at the bottom and a small alert map in the top-right corner. The bar
 * holds focus, so OK closes the alert; Back only closes it when 返回键关闭预警 is on.
 */
@Composable
internal fun AlertTicker(
    alert: AlertData,
    timeline: AlertTimeline,
    mapStyle: EewMapStyle,
    intenseThreshold: Int,
    allowBackDismiss: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The bar is composed by now; focusing it routes OK / Enter to onDismiss.
        runCatching { focus.requestFocus() }
    }
    val level = csisLevel(alert.localCsis)
    val barShape = RoundedCornerShape(28.dp)
    val mapShape = RoundedCornerShape(24.dp)

    Box(modifier.fillMaxSize()) {
        AlertMap(
            alert = alert,
            timeline = timeline,
            style = mapStyle,
            intenseThreshold = intenseThreshold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 40.dp)
                .shadow(12.dp, mapShape)
                .size(width = 300.dp, height = 206.dp),
            shape = mapShape,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
            legend = false,
            showAttribution = false,
            fitPaddingDp = 28,
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 40.dp, end = 40.dp, bottom = 26.dp)
                .fillMaxWidth()
                .shadow(16.dp, barShape)
                .clip(barShape)
                .focusRequester(focus)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.pg_alert_close), onClick = onDismiss),
        ) {
            // ---- head
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AlertColors.Red)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.CrisisAlert, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(R.string.alert_title),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.04.em,
                    maxLines = 1,
                )
                if (alert.isSimulation) DrillTag(Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    sourceReportLabel(alert.event),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            // ---- countdown · information · burst
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(Color.White),
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(min = 170.dp)
                        .background(BroadcastBlue)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        countdownLabel(timeline),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (timeline.sArrived) {
                        val size = fixedSp(30.dp)
                        Text(
                            stringResource(R.string.pg_alert_arrived),
                            color = Color.White,
                            fontSize = size,
                            lineHeight = size * 1.2f,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    } else {
                        CountdownNumber(timeline.sCountdown, 62.dp, 18.dp, Color.White)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        listOf(alert.event.hypocenter, magnitudeText(alert.event.magnitude)).filter { it.isNotBlank() }.joinToString(" "),
                        color = BroadcastInk,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val intensity = stringResource(R.string.pg_alert_intensity_value, level)
                    val levelText = level.toString()
                    val description = intensityDescription(level)
                    val distance = stringResource(R.string.pg_alert_distance_epicenter, kmText(alert.distanceKm))
                    Text(
                        buildAnnotatedString {
                            val at = intensity.lastIndexOf(levelText)
                            if (at >= 0) {
                                append(intensity.substring(0, at))
                                withStyle(SpanStyle(color = BroadcastInk, fontWeight = FontWeight.Bold)) { append(levelText) }
                                append(intensity.substring(at + levelText.length))
                            } else {
                                append(intensity)
                            }
                            if (description.isNotBlank()) {
                                append(" · ")
                                append(description)
                            }
                            append(" · ")
                            append(distance)
                        },
                        color = AlertColors.TvInkSoft,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IntensityBurst(level, 70.dp)
                }
            }

            // ---- footer
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BroadcastInk)
                    .padding(horizontal = 20.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.pg_alert_tv_tip),
                    color = AlertColors.TvFooter,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (allowBackDismiss) R.string.pg_alert_tv_keys_back else R.string.pg_alert_tv_keys_keep),
                    color = AlertColors.TvFooter,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
