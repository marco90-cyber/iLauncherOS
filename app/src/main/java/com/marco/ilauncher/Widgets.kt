@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.marco.ilauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val REQ_BIND = 4241
const val REQ_CONFIG = 4242

class WidgetController(private val activity: Activity) {
    val host = AppWidgetHost(activity, 1024)
    val awm: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private var callback: ((Boolean) -> Unit)? = null

    fun providers(): List<AppWidgetProviderInfo> =
        awm.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    /** Alloca, collega (chiedendo il permesso se serve) e configura un widget. done(null) = annullato. */
    fun begin(provider: AppWidgetProviderInfo, done: (Int?) -> Unit) {
        val id = host.allocateAppWidgetId()

        fun configureOrFinish() {
            val info = awm.getAppWidgetInfo(id)
            if (info?.configure != null) {
                callback = { ok ->
                    if (ok) {
                        done(id)
                    } else {
                        remove(id)
                        done(null)
                    }
                }
                try {
                    host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIG, null)
                } catch (e: Exception) {
                    callback = null
                    remove(id)
                    done(null)
                }
            } else {
                done(id)
            }
        }

        if (awm.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            configureOrFinish()
        } else {
            callback = { ok ->
                if (ok) {
                    configureOrFinish()
                } else {
                    remove(id)
                    done(null)
                }
            }
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                callback = null
                remove(id)
                done(null)
            }
        }
    }

    fun handleResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQ_BIND || requestCode == REQ_CONFIG) {
            val cb = callback
            callback = null
            cb?.invoke(resultCode == Activity.RESULT_OK)
        }
    }

    fun remove(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun start() {
        try {
            host.startListening()
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
    }
}

/** Dimensione iniziale (colonne, righe) in base alla dimensione minima richiesta dal widget. */
fun sizeForProvider(info: AppWidgetProviderInfo, density: Float): Pair<Int, Int> {
    val wDp = info.minWidth / density
    val hDp = info.minHeight / density
    val cw = ((wDp + 20) / 80).toInt().coerceIn(1, 4)
    val ch = ((hDp + 20) / 90).toInt().coerceIn(1, 6)
    return cw to ch
}

/** Contenitore che intercetta la pressione lunga sul widget, così si può selezionare la tile. */
class LongPressFrame(ctx: Context) : FrameLayout(ctx) {
    var onLong: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false
    private var sx = 0f
    private var sy = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val fire = Runnable {
        fired = true
        onLong?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                sx = ev.x
                sy = ev.y
                handler.postDelayed(fire, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (!fired && (abs(ev.x - sx) > slop || abs(ev.y - sy) > slop)) handler.removeCallbacks(fire)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(fire)
        }
        return fired
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(fire)
            val was = fired
            fired = false
            return was
        }
        return fired
    }
}

@Composable
fun AndroidWidgetView(ctl: WidgetController, id: Int, widthDp: Int, heightDp: Int, onLongPress: () -> Unit) {
    val info = remember(id) { ctl.awm.getAppWidgetInfo(id) }
    val lp by rememberUpdatedState(onLongPress)
    if (info == null) {
        Text("Widget", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
            factory = { ctx ->
                LongPressFrame(ctx).also { f ->
                    f.addView(ctl.host.createView(ctx, id, info))
                    f.onLong = { lp() }
                }
            },
            update = { f ->
                (f.getChildAt(0) as? AppWidgetHostView)
                    ?.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            }
        )
    }
}

// ---------- Widget interni ----------

data class BuiltinWidget(val key: String, val title: String, val subtitle: String, val w: Int, val h: Int)

val BUILTINS = listOf(
    BuiltinWidget("clock", "Clock", "Clock", 2, 2),
    BuiltinWidget("clock_digital", "Clock", "Clock Digital", 2, 2),
    BuiltinWidget("calendar", "Calendar", "Month", 2, 2),
    BuiltinWidget("weather", "Weather", "Forecast", 2, 2),
    BuiltinWidget("battery", "Battery", "Status", 2, 2)
)

fun builtinTitle(key: String): String = BUILTINS.firstOrNull { it.key == key }?.let {
    if (it.key == "clock_digital") "Clock" else it.title
}.orEmpty()

private val CARD_DARK = Color(0xFF1C1C1E)

@Composable
fun BuiltinWidgetView(key: String, st: LauncherState) {
    when (key) {
        "clock" -> ClockAnalogWidget()
        "clock_digital" -> ClockDigitalWidget()
        "calendar" -> CalendarWidget()
        "weather" -> WeatherWidget(st)
        "battery" -> BatteryWidget()
        else -> Box(Modifier.fillMaxSize())
    }
}

@Composable
fun ClockAnalogWidget() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color(0xFF111111))) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val h = cal.get(Calendar.HOUR)
            val m = cal.get(Calendar.MINUTE)
            val s = cal.get(Calendar.SECOND)
            val r = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White, r, c)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val big = i % 5 == 0
                val l = if (big) r * 0.07f else r * 0.035f
                val p1 = Offset(c.x + (r * 0.93f) * sin(a).toFloat(), c.y - (r * 0.93f) * cos(a).toFloat())
                val p2 = Offset(c.x + (r * 0.93f - l) * sin(a).toFloat(), c.y - (r * 0.93f - l) * cos(a).toFloat())
                drawLine(Color.Black, p1, p2, strokeWidth = if (big) r * 0.025f else r * 0.012f)
            }
            paint.textSize = r * 0.24f
            for (n in 1..12) {
                val a = Math.toRadians(n * 30.0)
                val x = c.x + (r * 0.70f) * sin(a).toFloat()
                val y = c.y - (r * 0.70f) * cos(a).toFloat() + paint.textSize * 0.35f
                drawContext.canvas.nativeCanvas.drawText(n.toString(), x, y, paint)
            }
            fun hand(angleDeg: Double, len: Float, w: Float, col: Color) {
                val a = Math.toRadians(angleDeg)
                drawLine(
                    col, c, Offset(c.x + len * sin(a).toFloat(), c.y - len * cos(a).toFloat()),
                    strokeWidth = w, cap = StrokeCap.Round
                )
            }
            hand((h + m / 60.0) * 30.0, r * 0.45f, r * 0.06f, Color.Black)
            hand((m + s / 60.0) * 6.0, r * 0.68f, r * 0.045f, Color.Black)
            hand(s * 6.0, r * 0.78f, r * 0.02f, Color(0xFFFF9500))
            drawCircle(Color(0xFFFF9500), r * 0.045f, c)
        }
    }
}

@Composable
fun ClockDigitalWidget() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5000)
        }
    }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val txt = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        val fs = with(LocalDensity.current) { (maxWidth / 3.2f).toSp() }
        Text(txt, color = Color.White, fontSize = fs, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CalendarWidget() {
    val today = LocalDate.now()
    val ym = YearMonth.of(today.year, today.month)
    val loc = if (Lang.it) Locale.ITALIAN else Locale.ENGLISH
    val first = ym.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = ym.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val dow = listOf("M", "T", "W", "T", "F", "S", "S")
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(CARD_DARK)) {
        val fs = with(LocalDensity.current) { (maxHeight / 11f).toSp() }
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                today.month.getDisplayName(TextStyle.FULL, loc).uppercase(loc),
                color = Color(0xFFFF453A), fontSize = fs, fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth().weight(1f)) {
                dow.forEach {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(it, color = Color.White, fontSize = fs, fontWeight = FontWeight.Bold)
                    }
                }
            }
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (cIdx in 0 until 7) {
                        val d = r * 7 + cIdx - offset + 1
                        val isToday = d == today.dayOfMonth
                        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (d in 1..days) {
                                Box(
                                    if (isToday) Modifier.fillMaxSize().padding(1.dp).clip(CircleShape).background(Color(0xFFFF453A))
                                    else Modifier,
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.toString(), color = if (cIdx >= 5 && !isToday) Color(0xFF8E8E93) else Color.White,
                                        fontSize = fs, textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryWidget() {
    val ctx = LocalContext.current
    var pct by remember { mutableStateOf(100) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                pct = if (s > 0) l * 100 / s else l
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(20000)
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(CARD_DARK),
        contentAlignment = Alignment.Center
    ) {
        val fs = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) / 3.4f).toSp() }
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = min(size.width, size.height) * 0.07f
            val d = min(size.width, size.height) - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(Color(0xFF3A3A3C), 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
            drawArc(
                if (pct <= 20) Color(0xFFFF453A) else Color(0xFF30D158), -90f, 360f * pct / 100f, false,
                tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            (if (charging) "⚡" else "") + "$pct%", color = Color.White, fontSize = fs, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun WeatherWidget(st: LauncherState) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("weather", Context.MODE_PRIVATE) }
    var w by remember { mutableStateOf(parseWeather(sp.getString("w", null))) }
    var hasLoc by remember { mutableStateOf(hasLocationPermission(ctx)) }
    LaunchedEffect(st.prefs.weatherRefreshHours, st.locationTick) {
        hasLoc = hasLocationPermission(ctx)
        while (true) {
            val cur = w
            val maxAge = st.prefs.weatherRefreshHours * 3600_000L
            if (hasLoc && (cur == null || System.currentTimeMillis() - cur.updated > maxAge)) {
                lastLocation(ctx)?.let { (lat, lon) ->
                    fetchWeather(lat, lon)?.let {
                        w = it
                        sp.edit().putString("w", it.toJson()).apply()
                    }
                }
            }
            delay(15 * 60_000L)
        }
    }
    fun temp(t: Int): String = if (st.prefs.celsius) "$t°" else "${Math.round(t * 9f / 5f + 32f)}°"
    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3F8CF0), Color(0xFF1F5FBF))))
            .clickable { if (!hasLoc) st.requestLocation() }
    ) {
        val fs = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) / 7f).toSp() }
        val cur = w
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            if (cur == null) {
                Text(
                    if (hasLoc) "Weather…" else tr("Allow location for the weather"),
                    color = Color.White, fontSize = fs, fontWeight = FontWeight.Medium
                )
            } else {
                Text(weatherEmoji(cur.code) + " " + temp(cur.temp), color = Color.White, fontSize = fs * 1.5f, fontWeight = FontWeight.SemiBold)
                Column {
                    Text(weatherText(cur.code), color = Color.White, fontSize = fs, fontWeight = FontWeight.Medium)
                    val d0 = cur.days.firstOrNull()
                    if (d0 != null) Text("H:${temp(d0.second)} L:${temp(d0.first)}", color = Color(0xCCFFFFFF), fontSize = fs * 0.9f)
                }
            }
        }
    }
}
