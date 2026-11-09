package com.github.mytv.myearthquakealert.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.alert.AlertActivity
import com.github.mytv.myearthquakealert.wear.data.WatchPhase
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.domain.Countdown
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import com.github.mytv.myearthquakealert.wear.ui.MainActivity

/**
 * Watch-face complication: SHORT_TEXT (state + latest magnitude), RANGED_VALUE (latest
 * magnitude on a 0–9 arc) and MONOCHROMATIC_IMAGE (the phase icon). During an alert all
 * three show the alert and open the countdown screen.
 */
class StatusComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        WatchStore.init(this)
        return build(this, request.complicationType, preview = false)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(this, type, preview = true)

    private data class View(
        val label: String,
        val magnitude: Double?,
        val icon: Int,
        val description: String,
        val alert: Boolean,
    )

    private fun current(context: Context, preview: Boolean): View {
        if (preview) {
            return View(context.getString(R.string.tiny_monitoring), 6.0, R.drawable.ic_sensors, context.getString(R.string.phase_monitoring), false)
        }
        val now = System.currentTimeMillis()
        val alert = WatchStore.alert.value?.takeIf { Countdown.snapshot(it, now).stage != Countdown.Stage.EXPIRED }
        if (alert != null) {
            return View(
                label = context.getString(R.string.tiny_alert),
                magnitude = alert.magnitude,
                icon = R.drawable.ic_earthquake,
                description = context.getString(R.string.alert_title, alert.place, PhaseText.magnitude(alert.magnitude)),
                alert = true,
            )
        }
        val phase: WatchPhase = WatchStore.phase(now)
        val quake = WatchStore.quakes.value.firstOrNull()
        val stateText = PhaseText.short(context, phase, WatchStore.status.value)
        return View(
            label = PhaseText.tiny(context, phase),
            magnitude = quake?.magnitude,
            icon = PhaseText.icon(phase),
            description = quake?.let { "$stateText · ${PhaseText.quakeChip(it)}" } ?: stateText,
            alert = false,
        )
    }

    private fun build(context: Context, type: ComplicationType, preview: Boolean): ComplicationData? {
        val v = current(context, preview)
        val description = text(v.description)
        val image = MonochromaticImage.Builder(Icon.createWithResource(context, v.icon)).build()
        val tap = if (preview) null else tapAction(context, v.alert)
        val magnitudeText = v.magnitude?.let { "M" + PhaseText.magnitude(it) } ?: "M--"
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(v.label), description)
                .setTitle(text(magnitudeText))
                .setMonochromaticImage(image)
                .setTapAction(tap)
                .build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = (v.magnitude ?: 0.0).toFloat().coerceIn(0f, MAX_MAGNITUDE),
                min = 0f,
                max = MAX_MAGNITUDE,
                contentDescription = description,
            )
                .setText(text(magnitudeText))
                .setTitle(text(v.label))
                .setMonochromaticImage(image)
                .setTapAction(tap)
                .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(image, description)
                .setTapAction(tap)
                .build()
            else -> null
        }
    }

    private fun tapAction(context: Context, alert: Boolean): PendingIntent {
        val intent = if (alert) AlertActivity.intent(context) else Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, if (alert) 11 else 10, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun text(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

    private companion object {
        const val MAX_MAGNITUDE = 9f
    }
}
