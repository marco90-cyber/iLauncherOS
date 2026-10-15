package com.marco.ilauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ---------- Badge delle notifiche ----------

object BadgeHub {
    val counts: SnapshotStateMap<String, Int> = mutableStateMapOf()
    val connected = mutableStateOf(false)
}

class BadgeListener : NotificationListenerService() {
    override fun onListenerConnected() {
        BadgeHub.connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        BadgeHub.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        val list: List<StatusBarNotification> = try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val map = list
            .filter { it.packageName != applicationContext.packageName }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .filter { !it.isOngoing }
            .groupBy { it.packageName }
            .mapValues { it.value.size }
        BadgeHub.counts.keys.toList().filter { it !in map }.forEach { BadgeHub.counts.remove(it) }
        map.forEach { (k, v) -> BadgeHub.counts[k] = v }
    }
}

// ---------- Meteo (Open-Meteo, senza chiave API) ----------

/** days = lista di (min, max) per oggi, domani, dopodomani. */
data class Weather(val temp: Int, val code: Int, val days: List<Pair<Int, Int>>, val updated: Long)

fun Weather.toJson(): String {
    val d = JSONArray()
    days.forEach { d.put(JSONArray().put(it.first).put(it.second)) }
    return JSONObject().put("t", temp).put("c", code).put("u", updated).put("d", d).toString()
}

fun parseWeather(s: String?): Weather? {
    if (s.isNullOrEmpty()) return null
    return try {
        val o = JSONObject(s)
        val a = o.getJSONArray("d")
        val days = (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getInt(0) to p.getInt(1) } }
        Weather(o.getInt("t"), o.getInt("c"), days, o.getLong("u"))
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchWeather(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
    try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3"
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val o = JSONObject(body)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val mx = d.getJSONArray("temperature_2m_max")
        val mn = d.getJSONArray("temperature_2m_min")
        val days = (0 until mx.length()).map {
            Math.round(mn.getDouble(it)).toInt() to Math.round(mx.getDouble(it)).toInt()
        }
        Weather(
            Math.round(cur.getDouble("temperature_2m")).toInt(),
            cur.getInt("weather_code"),
            days,
            System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "❄️"
    in 80..82 -> "🌧️"
    85, 86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    85, 86 -> "Snow"
    in 95..99 -> "Thunderstorm"
    else -> ""
}

fun hasLocationPermission(ctx: Context): Boolean =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
fun lastLocation(ctx: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(ctx)) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val best = lm.getProviders(true)
        .mapNotNull {
            try {
                lm.getLastKnownLocation(it)
            } catch (e: Exception) {
                null
            }
        }
        .maxByOrNull { it.time }
    return best?.let { it.latitude to it.longitude }
}

