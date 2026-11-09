package com.github.mytv.myearthquakealert.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.EewHistory
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * 桌面小部件「监测状态」(design §10: Glance GlanceAppWidget, 状态 + 最近地震). Responsive from
 * 1×1 (status shape and short text) to 4×2+ (status, up to three recent early warnings and
 * 暂停 1 小时 / 恢复 / 开启). Shapes, colours and words are the ones of every other surface.
 * A tap opens the overview; an earthquake row opens 地震. Kept current by [WidgetUpdater].
 */
class StatusWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_status_error) {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MyEarthQuakeAlertApp
        val initialSettings = app.settingsRepository.settings.first()
        provideContent {
            val settings by app.settingsRepository.settings.collectAsState(initialSettings)
            val monitor by MonitorStatus.state.collectAsState()
            val records by EewHistory.records.collectAsState()
            val now by produceState(System.currentTimeMillis()) {
                while (true) {
                    delay(MINUTE_MS - value % MINUTE_MS)
                    value = System.currentTimeMillis()
                }
            }
            val glanceContext = LocalContext.current
            val formats = remember(glanceContext) { Formats(glanceContext.resources) }
            val palette = remember(settings.themeMode) { WidgetPalette(settings.themeMode) }
            val model = WidgetModel.build(glanceContext.resources, formats, monitor, settings, records, now)
            GlanceTheme(colors = palette.providers) {
                WidgetContent(model, palette)
            }
        }
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Log.e(TAG, "widget $appWidgetId failed", throwable)
        val views = RemoteViews(context.packageName, R.layout.widget_status_error)
        val pending = PendingIntent.getActivity(
            context, REQUEST_ERROR, openAppIntent(context, Destination.OVERVIEW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_status_error_root, pending)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    private companion object {
        const val TAG = "StatusWidget"
        const val MINUTE_MS = 60_000L
        const val REQUEST_ERROR = 501
    }
}

/** Receiver of [StatusWidget], declared with res/xml/widget_status_info.xml. */
class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

internal fun openAppIntent(context: Context, destination: Destination): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(Destination.EXTRA, destination.route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

// ── Layouts ──────────────────────────────────────────────────────────────────

@Composable
private fun WidgetContent(model: WidgetModel, palette: WidgetPalette) {
    val context = LocalContext.current
    val size = LocalSize.current
    val bucket = WidgetSizes.bucket(size.width.value, size.height.value)
    val options = LocalAppWidgetOptions.current
    // On the lock screen nobody has authenticated: show the status, never offer to pause it.
    val onKeyguard = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1) ==
        AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    val action = model.action.takeUnless { onKeyguard && it == WidgetAction.PAUSE_HOUR }
    val padding = when (bucket) {
        WidgetBucket.COMPACT -> 4.dp
        WidgetBucket.MEDIUM -> 10.dp
        else -> 12.dp
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(backgroundModifier(palette))
            .clickable(actionStartActivity(openAppIntent(context, Destination.OVERVIEW)))
            .padding(padding),
        contentAlignment = if (bucket == WidgetBucket.COMPACT) Alignment.Center else Alignment.CenterStart,
    ) {
        when (bucket) {
            WidgetBucket.COMPACT -> CompactLayout(model, palette)
            WidgetBucket.SMALL -> SmallLayout(model, palette)
            WidgetBucket.WIDE -> WideLayout(model, palette)
            WidgetBucket.MEDIUM -> MediumLayout(model, palette)
            WidgetBucket.LARGE -> LargeLayout(model, palette, action, WidgetSizes.largeRows(size.height.value))
        }
    }
}

private fun backgroundModifier(palette: WidgetPalette): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.background(palette.background).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.background(ImageProvider(R.drawable.ic_wg_bg), colorFilter = ColorFilter.tint(palette.background))
    }

@Composable
private fun CompactLayout(model: WidgetModel, palette: WidgetPalette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        PhaseMark(model, palette, 30.dp)
        Spacer(GlanceModifier.height(2.dp))
        Text(
            model.short,
            style = TextStyle(color = palette.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

@Composable
private fun SmallLayout(model: WidgetModel, palette: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PhaseMark(model, palette, 36.dp)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            model.short,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = palette.phaseText(model.phase), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
    }
}

@Composable
private fun WideLayout(model: WidgetModel, palette: WidgetPalette) {
    val latest = model.quakes.firstOrNull()
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PhaseMark(model, palette, 36.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                model.short,
                style = TextStyle(color = palette.phaseText(model.phase), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                latest?.let { "${it.magnitude} ${it.place} · ${it.meta}" } ?: model.subtitle,
                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MediumLayout(model: WidgetModel, palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(model, palette, 40.dp)
        Spacer(GlanceModifier.height(6.dp))
        val latest = model.quakes.firstOrNull()
        if (latest != null) QuakeRow(latest, palette) else EmptyRecords(palette)
    }
}

@Composable
private fun LargeLayout(model: WidgetModel, palette: WidgetPalette, action: WidgetAction?, rows: Int) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(model, palette, 44.dp)
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                LocalContext.current.getString(R.string.wg_recent),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (action != null) ActionPill(action, palette)
        }
        Spacer(GlanceModifier.height(4.dp))
        if (model.quakes.isEmpty()) {
            EmptyRecords(palette)
        } else {
            model.quakes.take(rows).forEach { QuakeRow(it, palette) }
        }
    }
}

// ── Parts ────────────────────────────────────────────────────────────────────

/** The phase's status shape with its icon, as StatusShape draws it in the app (static). */
@Composable
private fun PhaseMark(model: WidgetModel, palette: WidgetPalette, size: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val phase = model.phase
    val description = context.getString(R.string.wg_status_cd, model.title)
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(PhaseArt.shape(phase, (size.value * density).roundToInt())),
            contentDescription = description,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(palette.phaseContainer(phase)),
        )
        PhaseArt.phaseIcon(phase)?.let { icon ->
            val iconSize = size * 0.42f
            Image(
                provider = ImageProvider(PhaseArt.icon(icon, (iconSize.value * density).roundToInt())),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(palette.phaseContent(phase)),
            )
        }
    }
}

@Composable
private fun Header(model: WidgetModel, palette: WidgetPalette, shapeSize: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PhaseMark(model, palette, shapeSize)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                model.title,
                style = TextStyle(color = palette.phaseText(model.phase), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                model.subtitle,
                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuakeRow(quake: WidgetQuake, palette: WidgetPalette) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(WidgetSizes.ROW_DP.dp)
            .clickable(actionStartActivity(openAppIntent(context, Destination.QUAKES)))
            .semantics { contentDescription = quake.description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(46.dp)
                .height(26.dp)
                .background(ImageProvider(R.drawable.ic_wg_badge), colorFilter = ColorFilter.tint(ColorProvider(quake.badge))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                quake.magnitude,
                style = TextStyle(color = ColorProvider(quake.onBadge), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(quake.place, style = TextStyle(color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(quake.meta, style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun EmptyRecords(palette: WidgetPalette) {
    Text(
        LocalContext.current.getString(R.string.wg_no_records),
        style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
        maxLines = 2,
    )
}

/** 暂停 1 小时 / 恢复 / 开启 as a tonal pill. */
@Composable
private fun ActionPill(action: WidgetAction, palette: WidgetPalette) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val (label, icon, run) = when (action) {
        WidgetAction.PAUSE_HOUR -> Triple(R.string.wg_pause_hour, WidgetIcon.PAUSE, actionRunCallback<PauseHourAction>())
        WidgetAction.RESUME -> Triple(R.string.wg_resume, WidgetIcon.RESUME, actionRunCallback<ResumeAction>())
        WidgetAction.TURN_ON -> Triple(R.string.wg_turn_on, WidgetIcon.POWER, actionRunCallback<TurnOnAction>())
    }
    PillButton(context.getString(label), icon, run, palette, density, 13.sp)
}

@Composable
private fun PillButton(text: String, icon: WidgetIcon, action: Action, palette: WidgetPalette, density: Float, fontSize: TextUnit) {
    val iconSize = 16.dp
    Row(
        modifier = GlanceModifier
            .height(32.dp)
            .background(ImageProvider(R.drawable.ic_wg_pill), colorFilter = ColorFilter.tint(palette.tonal))
            .cornerRadius(16.dp)
            .clickable(action)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(PhaseArt.icon(icon.vector, (iconSize.value * density).roundToInt())),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(palette.onTonal),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(text, style = TextStyle(color = palette.onTonal, fontSize = fontSize, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
