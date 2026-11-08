package com.github.mytv.myearthquakealert.ui.kiosk

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.ui.adaptive.findActivity
import com.github.mytv.myearthquakealert.ui.components.StatusShape
import com.github.mytv.myearthquakealert.ui.components.ageText
import com.github.mytv.myearthquakealert.ui.components.formatClock
import com.github.mytv.myearthquakealert.ui.components.monitorTexts
import com.github.mytv.myearthquakealert.ui.components.rememberNow
import com.github.mytv.myearthquakealert.ui.components.toMarkers
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.overview.collectOverviewModel
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel
import com.github.mytv.myearthquakealert.ui.theme.EeqNumberStyles
import com.github.mytv.myearthquakealert.ui.tv.TvPalette
import com.github.mytv.myearthquakealert.ui.tv.TvQuakeCard
import com.github.mytv.myearthquakealert.ui.tv.forTenFoot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Holding the screen, or the back key, this long leaves 看板. */
private const val EXIT_HOLD_MS = 3_000L

/** The catalogue is refreshed this often while the board is up. */
private const val REFRESH_PERIOD_MS = 5 * 60_000L

private val KioskCard = Color(0xD10E1217)
private val KioskTicker = Color(0xD60E1217)

/**
 * 看板 (design scrKiosk): full-screen satellite map, clock, status, a hint and the newest
 * quakes along the bottom. The screen stays on and the system bars are hidden. Back asks
 * before leaving; holding back — or the screen — for 3 s leaves at once.
 */
@Composable
fun KioskScreen(vm: QuakesViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val model = collectOverviewModel(vm)
    val clock = rememberNow(1_000L)
    val texts = monitorTexts(model.phase, model.monitor, model.settings, model.now)
    val markers = remember(model.quakes.all) { model.quakes.all.toMarkers() }
    val exit by rememberUpdatedState(onExit)
    var confirm by rememberSaveable { mutableStateOf(false) }

    KeepScreenOnImmersive()

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshIfStale()
            delay(REFRESH_PERIOD_MS)
        }
    }

    BackHandler { confirm = true }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var backDownAt by remember { mutableLongStateOf(-1L) }
    var backExited by remember { mutableStateOf(false) }
    val touchSlop = LocalViewConfiguration.current.touchSlop * 4

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (native.repeatCount == 0) {
                            backDownAt = native.eventTime
                            backExited = false
                        } else if (!backExited && backDownAt >= 0 && native.eventTime - backDownAt >= EXIT_HOLD_MS) {
                            backExited = true
                            exit()
                        }
                        // Repeats are ours; the first down goes on so the back dispatcher sees the press.
                        native.repeatCount > 0
                    }
                    KeyEventType.KeyUp -> {
                        val swallow = backExited
                        backDownAt = -1L
                        backExited = false
                        // After a long-press exit the release must not also open the dialog.
                        swallow
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val released = withTimeoutOrNull(EXIT_HOLD_MS) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            if ((change.position - down.position).getDistance() > touchSlop) break
                        }
                        true
                    }
                    if (released == null) exit()
                }
            },
    ) {
        EarthquakeMap(
            style = model.settings.mapStyle.forTenFoot(),
            mode = EarthquakeMapMode.BROWSE,
            modifier = Modifier.fillMaxSize(),
            userPoint = model.quakes.user?.point,
            interactive = false,
            quakes = markers,
            maxFitDistanceKm = 2_200.0,
            fitPaddingDp = 96,
            darkTiles = true,
            touchEnabled = false,
            attributionAlignment = Alignment.BottomEnd,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 24.dp, end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            KioskCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(formatClock(clock), style = EeqNumberStyles.clock.copy(fontSize = 54.sp, lineHeight = 56.sp), color = TvPalette.OnCard)
                    Text(
                        listOfNotNull(dateLine(clock), model.quakes.user?.name?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        fontSize = 14.sp,
                        color = TvPalette.OnCard.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
            KioskCard {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusShape(model.phase, size = 48.dp)
                    Column {
                        Text(texts.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TvPalette.OnCard, maxLines = 1)
                        Text(
                            if (model.phase == MonitorPhase.MONITORING && model.metrics.heartbeatAgeMs != null) {
                                stringResource(R.string.ui_kiosk_on_sub, model.monitor.sources.size.coerceAtLeast(1), ageText(model.metrics.heartbeatAgeMs!!))
                            } else {
                                texts.subtitle
                            },
                            fontSize = 13.sp,
                            color = TvPalette.OnCard.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            KioskCard {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Fullscreen, contentDescription = null, tint = TvPalette.OnCard, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ui_kiosk_hint), fontSize = 13.sp, color = TvPalette.OnCard)
                }
            }
        }

        val ticker = model.quakes.all.take(4)
        if (ticker.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ticker.forEach { q ->
                    TvQuakeCard(quake = q, now = model.now, onClick = null, container = KioskTicker, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Outlined.Fullscreen, contentDescription = null) },
            title = { Text(stringResource(R.string.ui_kiosk_exit_title)) },
            text = { Text(stringResource(R.string.ui_kiosk_exit_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    exit()
                }) { Text(stringResource(R.string.ui_kiosk_exit)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun KioskCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = KioskCard, contentColor = TvPalette.OnCard) { content() }
}

/** "11月8日 星期日" / "Sunday, Nov 8" in the device locale. */
private fun dateLine(epochMillis: Long): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMdEEEE")
    return SimpleDateFormat(pattern, locale).format(Date(epochMillis))
}

/** FLAG_KEEP_SCREEN_ON and hidden system bars while the board is shown. */
@Composable
private fun KeepScreenOnImmersive() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
