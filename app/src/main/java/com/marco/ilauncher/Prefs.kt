package com.marco.ilauncher

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.reflect.KProperty

class BoolPref(private val sp: SharedPreferences, private val key: String, def: Boolean) {
    private var state by mutableStateOf(sp.getBoolean(key, def))
    operator fun getValue(t: Any?, p: KProperty<*>): Boolean = state
    operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) {
        state = v
        sp.edit().putBoolean(key, v).apply()
    }
}

class IntPref(private val sp: SharedPreferences, private val key: String, def: Int) {
    private var state by mutableStateOf(sp.getInt(key, def))
    operator fun getValue(t: Any?, p: KProperty<*>): Int = state
    operator fun setValue(t: Any?, p: KProperty<*>, v: Int) {
        state = v
        sp.edit().putInt(key, v).apply()
    }
}

class FloatPref(private val sp: SharedPreferences, private val key: String, def: Float) {
    private var state by mutableStateOf(sp.getFloat(key, def))
    operator fun getValue(t: Any?, p: KProperty<*>): Float = state
    operator fun setValue(t: Any?, p: KProperty<*>, v: Float) {
        state = v
        sp.edit().putFloat(key, v).apply()
    }
}

class StrPref(private val sp: SharedPreferences, private val key: String, def: String) {
    private var state by mutableStateOf(sp.getString(key, def) ?: def)
    operator fun getValue(t: Any?, p: KProperty<*>): String = state
    operator fun setValue(t: Any?, p: KProperty<*>, v: String) {
        state = v
        sp.edit().putString(key, v).apply()
    }
}

class SetPref(private val sp: SharedPreferences, private val key: String) {
    private var state by mutableStateOf(read())

    private fun read(): Set<String> {
        val s = sp.getString(key, null) ?: return emptySet()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    operator fun getValue(t: Any?, p: KProperty<*>): Set<String> = state
    operator fun setValue(t: Any?, p: KProperty<*>, v: Set<String>) {
        state = v
        val a = JSONArray()
        v.forEach { a.put(it) }
        sp.edit().putString(key, a.toString()).apply()
    }
}

class MapPref(private val sp: SharedPreferences, private val key: String) {
    private var state by mutableStateOf(read())

    private fun read(): Map<String, String> {
        val s = sp.getString(key, null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    operator fun getValue(t: Any?, p: KProperty<*>): Map<String, String> = state
    operator fun setValue(t: Any?, p: KProperty<*>, v: Map<String, String>) {
        state = v
        val o = JSONObject()
        v.forEach { (k, x) -> o.put(k, x) }
        sp.edit().putString(key, o.toString()).apply()
    }
}

/** Tutte le impostazioni del launcher; ogni valore è uno stato Compose salvato su disco. */
class LauncherPrefs(sp: SharedPreferences) {
    val cols = 4
    var rows by IntPref(sp, "rows", 6)

    // Appearance
    var theme by StrPref(sp, "theme", "dark")
    var autoTheme by BoolPref(sp, "autoTheme", false)
    var systemTheme by BoolPref(sp, "systemTheme", true)

    // General
    var hideNav by BoolPref(sp, "hideNav", false)
    var freeLayout by BoolPref(sp, "freeLayout", false)
    var autoArrange by BoolPref(sp, "autoArrange", true)
    var blur by BoolPref(sp, "blur", true)
    var hideNames by BoolPref(sp, "hideNames", false)
    var flexText by BoolPref(sp, "flexText", true)
    var textScale by FloatPref(sp, "textScale", 1f)

    // Icons
    var iconShape by StrPref(sp, "iconShape", "squircle")
    var iconScale by FloatPref(sp, "iconScale", 1f)

    // Home screen style
    var showSearch by BoolPref(sp, "showSearch", true)
    var showDots by BoolPref(sp, "showDots", true)
    var showDock by BoolPref(sp, "showDock", true)
    var dockStyle by StrPref(sp, "dockStyle", "glass")
    var glassTrans by FloatPref(sp, "glassTrans", 0.08f)
    var classicTrans by FloatPref(sp, "classicTrans", 0f)
    val dockTrans: Float get() = if (dockStyle == "glass") glassTrans else classicTrans
    var widgetCaptions by BoolPref(sp, "widgetCaptions", true)
    var dimWallpaper by BoolPref(sp, "dimWallpaper", true)
    var transition by StrPref(sp, "transition", "slide")

    // App Library & badges
    var lastPage by StrPref(sp, "lastPage", "library")
    var showLibrary by BoolPref(sp, "showLibrary", true)
    var badges by BoolPref(sp, "badges", true)

    // Weather
    var celsius by BoolPref(sp, "celsius", true)
    var weatherRefreshHours by IntPref(sp, "weatherRefresh", 24)

    var lang by StrPref(sp, "lang", "auto")

    var hidden by SetPref(sp, "hidden")
    var names by MapPref(sp, "names")

    fun isDark(systemDark: Boolean): Boolean = when {
        systemTheme -> systemDark
        autoTheme -> {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            h >= 19 || h < 7
        }
        else -> theme == "dark"
    }
}
