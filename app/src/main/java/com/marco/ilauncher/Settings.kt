@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.marco.ilauncher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GREEN = Color(0xFF34C759)

@Composable
fun SetRow(pal: Pal, icon: ImageVector, color: Color, title: String, last: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(title, color = pal.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = pal.sub, modifier = Modifier.size(20.dp))
        }
        if (!last) Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(pal.sep))
    }
}

@Composable
fun SwitchRow(pal: Pal, title: String, value: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = pal.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = value, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = GREEN, checkedThumbColor = Color.White)
            )
        }
        if (!last) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(pal.sep))
    }
}

@Composable
fun CheckRow(pal: Pal, title: String, selected: Boolean, last: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = pal.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, null, tint = pal.accent)
        }
        if (!last) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(pal.sep))
    }
}

@Composable
fun Card(pal: Pal, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card)) {
        content()
    }
}

@Composable
fun SectionLabel(pal: Pal, text: String) {
    Text(text, color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
fun Footnote(pal: Pal, text: String) {
    Text(text, color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
}

fun isDefaultHome(ctx: android.content.Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val r = ctx.packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return r?.activityInfo?.packageName == ctx.packageName
}

fun chooseDefaultLauncher(ctx: android.content.Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
        }
    }
}

@Composable
fun SettingsHost(st: LauncherState, pal: Pal) {
    val route = st.settingsStack.lastOrNull() ?: return
    val ctx = LocalContext.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val p = st.prefs
    val titles = mapOf(
        "general" to "General", "icon" to "Change App Icon", "style" to "Homescreen Style", "grid" to "Screen Grid",
        "transition" to "Page Transition", "hidden" to "Hidden Apps", "library" to "App Library",
        "names" to "Change App Name", "badges" to "Badge Notifications", "language" to "Language",
        "appearance" to "Appearance", "weather" to "Weather", "dockstyle" to "Style"
    )
    fun push(r: String) { st.settingsStack = st.settingsStack + r }
    fun pop() { st.settingsStack = st.settingsStack.dropLast(1) }

    Box(Modifier.fillMaxSize().background(pal.bg).clickable(enabled = false) {}) {
        if (route == "main") {
            var tab by remember { mutableStateOf(0) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = top + 8.dp, bottom = bottom + 90.dp)) {
                Text(tr("Settings"), color = pal.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                if (tab == 0) {
                    Card(pal) {
                        Row(
                            Modifier.fillMaxWidth().clickable { st.settingsStack = emptyList() }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(st.sysIconBitmap(), null, Modifier.size(44.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr("Go To Launcher"), color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                Text(tr("Explore your OS interface"), color = pal.sub, fontSize = 12.sp)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = pal.sub)
                        }
                    }
                    if (!isDefaultHome(ctx)) Card(pal) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("Set Launcher OS As Default"), color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                tr("To access all the features of Launcher OS, you need to set it as the default application."),
                                color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                            )
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.accent)
                                    .clickable { chooseDefaultLauncher(ctx) }.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(tr("Set As Default"), color = Color.White, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    SectionLabel(pal, tr("General"))
                    Card(pal) {
                        SetRow(pal, Icons.Filled.Home, Color(0xFF0A84FF), tr("Select Default Launcher")) { chooseDefaultLauncher(ctx) }
                        SetRow(pal, Icons.Filled.Tune, Color(0xFF8E8E93), tr("General")) { push("general") }
                        SetRow(pal, Icons.Filled.Apps, Color(0xFF34C759), tr("Change App Icon")) { push("icon") }
                        SetRow(pal, Icons.Filled.Palette, Color(0xFF30B94D), tr("Homescreen Style")) { push("style") }
                        SetRow(pal, Icons.Filled.Dashboard, Color(0xFFAF52DE), tr("Style")) { push("dockstyle") }
                        SetRow(pal, Icons.Filled.GridView, Color(0xFF3478F6), tr("Screen Grid")) { push("grid") }
                        SetRow(pal, Icons.Filled.SwapHoriz, Color(0xFF4C8DF6), tr("Page Transition")) { push("transition") }
                        SetRow(pal, Icons.Filled.VisibilityOff, Color(0xFF3B82F6), tr("Hidden Apps")) { push("hidden") }
                        SetRow(pal, Icons.Filled.Dashboard, Color(0xFF3A7BEA), tr("App Library")) { push("library") }
                        SetRow(pal, Icons.Filled.Edit, Color(0xFF34C759), tr("Change App Name")) { push("names") }
                        SetRow(pal, Icons.Filled.Notifications, Color(0xFFFF453A), tr("Badge Notifications")) { push("badges") }
                        SetRow(pal, Icons.Filled.Language, Color(0xFF4C8DF6), tr("Language")) { push("language") }
                        SetRow(pal, Icons.Filled.Brightness6, Color(0xFF30B94D), tr("Appearance"), last = true) { push("appearance") }
                    }
                } else {
                    SectionLabel(pal, tr("App Functions Settings"))
                    Card(pal) {
                        SetRow(pal, Icons.Filled.WbSunny, Color(0xFF3B82F6), tr("Weather"), last = true) { push("weather") }
                    }
                }
            }
            // barra Settings / Functions
            Box(Modifier.fillMaxSize().padding(bottom = bottom + 10.dp), contentAlignment = Alignment.BottomCenter) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (pal.dark) Color(0xEE2C2C2E) else Color(0xEEFFFFFF)).padding(6.dp)
                ) {
                    listOf(tr("Settings"), tr("Functions")).forEachIndexed { i, t ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (tab == i) Color(0x33808080) else Color.Transparent)
                                .clickable { tab = i }.padding(horizontal = 28.dp, vertical = 8.dp)
                        ) { Text(t, color = if (tab == i) pal.accent else pal.sub, fontSize = 13.sp) }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = top)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Box(
                        Modifier.padding(start = 12.dp).size(38.dp).clip(CircleShape).background(pal.card).clickable { pop() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.ArrowBack, tr("Back"), tint = pal.text, modifier = Modifier.size(20.dp)) }
                    Text(
                        tr(titles[route] ?: ""), color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (route == "grid") Text(
                        tr("Done"), color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).clip(RoundedCornerShape(50))
                            .background(pal.accent).clickable { pop() }.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom + 24.dp)) {
                    when (route) {
                        "general" -> GeneralScreen(st, pal)
                        "icon" -> IconScreen(st, pal)
                        "style" -> StyleScreen(st, pal)
                        "grid" -> GridScreen(st, pal)
                        "transition" -> {
                            Card(pal) {
                                val opts = listOf("slide" to "Slide", "cube" to "Cube", "zoom" to "Zoom", "fade" to "Fade")
                                opts.forEachIndexed { i, (k, n) ->
                                    CheckRow(pal, tr(n), p.transition == k, i == opts.size - 1) { p.transition = k }
                                }
                            }
                        }
                        "hidden" -> {
                            Footnote(pal, tr("Hidden apps are not shown on the Home Screen, App Library or search."))
                            Card(pal) {
                                st.apps.forEachIndexed { i, a ->
                                    val hid = a.key in p.hidden
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(st.icon(a), null, Modifier.size(36.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(st.label(a), color = pal.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = hid, onCheckedChange = { st.hideApp(a.key, it) },
                                            colors = SwitchDefaults.colors(checkedTrackColor = GREEN, checkedThumbColor = Color.White)
                                        )
                                    }
                                }
                            }
                        }
                        "dockstyle" -> {
                            SectionLabel(pal, tr("Dock"))
                            Card(pal) {
                                CheckRow(pal, tr("Glass Dock"), p.dockStyle == "glass") { p.dockStyle = "glass" }
                                CheckRow(pal, tr("Classic Dock"), p.dockStyle == "classic", true) { p.dockStyle = "classic" }
                            }
                        }
                        "library" -> {
                            Card(pal) {
                                SwitchRow(pal, tr("Show App Library"), p.showLibrary, true) { p.showLibrary = it }
                            }
                            SectionLabel(pal, tr("Last page"))
                            Card(pal) {
                                CheckRow(pal, tr("App Library"), p.lastPage == "library") { p.lastPage = "library"; p.showLibrary = true }
                                CheckRow(pal, tr("Widgets"), p.lastPage == "widgets", true) { p.lastPage = "widgets"; p.showLibrary = true }
                            }
                        }
                        "names" -> {
                            Footnote(pal, tr("Tap an app to rename it."))
                            Card(pal) {
                                st.visibleApps.forEach { a ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { st.renameKey = a.key }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(st.icon(a), null, Modifier.size(36.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(st.label(a), color = pal.text, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        "badges" -> {
                            Card(pal) { SwitchRow(pal, tr("Show badges"), p.badges, true) { p.badges = it } }
                            Footnote(pal, tr("Badges need notification access. Tap to open the system settings."))
                            Card(pal) {
                                SetRow(pal, Icons.Filled.Notifications, Color(0xFFFF453A), tr("Grant notification access"), true) {
                                    try {
                                        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                        }
                        "language" -> {
                            Card(pal) {
                                CheckRow(pal, tr("System default"), p.lang == "auto") { p.lang = "auto" }
                                CheckRow(pal, "English", p.lang == "en") { p.lang = "en" }
                                CheckRow(pal, "Italiano", p.lang == "it", true) { p.lang = "it" }
                            }
                        }
                        "appearance" -> AppearanceScreen(st, pal)
                        "weather" -> WeatherSettings(st, pal)
                    }
                }
            }
        }
    }
}

@Composable
fun GeneralScreen(st: LauncherState, pal: Pal) {
    val p = st.prefs
    SectionLabel(pal, tr("Options"))
    Footnote(pal, tr("Icons stay where you drop them, leaving empty spaces on the Home Screen and in the Dock."))
    Card(pal) {
        SwitchRow(pal, tr("Allow empty spaces"), p.freeLayout) { st.setFree(it) }
        SwitchRow(pal, tr("Hide Navigation"), p.hideNav) { p.hideNav = it }
        SwitchRow(pal, tr("Auto arrange home screen"), p.autoArrange) { p.autoArrange = it }
        SwitchRow(pal, tr("Blur effect"), p.blur, true) { p.blur = it }
    }
    SectionLabel(pal, tr("Screen rotation"))
    Card(pal) {
        CheckRow(pal, tr("Fixed layout, rotate icons"), p.orientation == "rotate") { p.orientation = "rotate" }
        CheckRow(pal, tr("Follow system"), p.orientation == "system") { p.orientation = "system" }
        CheckRow(pal, tr("Lock portrait"), p.orientation == "portrait") { p.orientation = "portrait" }
        CheckRow(pal, tr("Landscape"), p.orientation == "landscape", true) { p.orientation = "landscape" }
    }
    SectionLabel(pal, tr("Change App Size"))
    Card(pal) {
        SwitchRow(pal, tr("Hide app name"), p.hideNames) { p.hideNames = it }
        SwitchRow(pal, tr("Flexible Text Size"), p.flexText, true) { p.flexText = it }
    }
    if (p.flexText) Card(pal) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val bmp = st.sysIconBitmap()
            Image(bmp, null, Modifier.size(64.dp))
            Text(tr("Message"), color = pal.text, fontSize = (11f * p.textScale).sp)
            Text("${(p.textScale * 100).toInt()}%", color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Slider(value = p.textScale, onValueChange = { p.textScale = it }, valueRange = 0.7f..1.5f)
        }
    }
}

@Composable
fun IconScreen(st: LauncherState, pal: Pal) {
    val p = st.prefs
    SectionLabel(pal, tr("Icon shape"))
    Card(pal) {
        val shapes = listOf("squircle" to "Squircle", "circle" to "Circle")
        shapes.forEachIndexed { i, (k, n) ->
            CheckRow(pal, tr(n), p.iconShape == k, i == shapes.size - 1) { st.setIconShape(k) }
        }
    }
    SectionLabel(pal, tr("Icon size"))
    Card(pal) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(st.sysIconBitmap(), null, Modifier.size((56f * p.iconScale).dp))
            Text("${(p.iconScale * 100).toInt()}%", color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Slider(value = p.iconScale, onValueChange = { p.iconScale = it }, valueRange = 0.7f..1.1f)
        }
    }
}

@Composable
fun StyleScreen(st: LauncherState, pal: Pal) {
    val p = st.prefs
    Card(pal) {
        SwitchRow(pal, tr("Show Search Pill"), p.showSearch) { p.showSearch = it }
        SwitchRow(pal, tr("Show Page Dots"), p.showDots) { p.showDots = it }
        SwitchRow(pal, tr("Show Dock"), p.showDock) { p.showDock = it }
        SwitchRow(pal, tr("Widget Captions"), p.widgetCaptions) { p.widgetCaptions = it }
        SwitchRow(pal, tr("Dim wallpaper in dark mode"), p.dimWallpaper, true) { p.dimWallpaper = it }
    }
    Card(pal) {
        SetRow(pal, Icons.Filled.Palette, Color(0xFF8E8E93), tr("Use system wallpaper"), true) { st.clearWallpaper() }
    }
}

@Composable
fun GridScreen(st: LauncherState, pal: Pal) {
    val p = st.prefs
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(210.dp, 420.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD8C7A8), Color(0xFF6E6A80))))
                .padding(14.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until p.rows) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until p.cols) {
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.fillMaxWidth(0.78f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp)).background(Color(0x99FFFFFF))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(pal.card).padding(4.dp)
    ) {
        listOf(5, 6, 7).forEach { r ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (p.rows == r) Color(0x44808080) else Color.Transparent)
                    .clickable { p.rows = r; st.relayout() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("4×$r", color = if (p.rows == r) pal.text else pal.sub, fontSize = 14.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun AppearanceScreen(st: LauncherState, pal: Pal) {
    val p = st.prefs
    Card(pal) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("light" to "Light", "dark" to "Dark").forEach { (k, n) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { p.theme = k; p.systemTheme = false; p.autoTheme = false }
                ) {
                    val light = k == "light"
                    Box(
                        Modifier.size(100.dp, 170.dp).clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    if (light) listOf(Color(0xFF9BE0E8), Color(0xFF3F8CF0)) else listOf(Color(0xFF0B3B3F), Color(0xFF0A1F5C))
                                )
                            ).padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in 0 until 5) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (c in 0 until 4) {
                                        Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(if (light) Color(0xCCFFFFFF) else Color(0x66000000)))
                                    }
                                }
                            }
                        }
                    }
                    Text(tr(n), color = pal.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    val sel = !p.systemTheme && !p.autoTheme && p.theme == k
                    Box(
                        Modifier.padding(top = 6.dp).size(22.dp).clip(CircleShape)
                            .border(1.5.dp, if (sel) pal.accent else pal.sub, CircleShape)
                            .background(if (sel) pal.accent else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
    Card(pal) { SwitchRow(pal, tr("Auto"), p.autoTheme, true) { p.autoTheme = it; if (it) p.systemTheme = false } }
    Footnote(pal, tr("Your interface will automatically change according to the time of day."))
    Card(pal) { SwitchRow(pal, tr("System"), p.systemTheme, true) { p.systemTheme = it; if (it) p.autoTheme = false } }
    Footnote(pal, tr("Your interface will be adjusted according to system settings."))
}

@Composable
fun WeatherSettings(st: LauncherState, pal: Pal) {
    val p = st.prefs
    SectionLabel(pal, tr("Temperature Unit"))
    Card(pal) {
        CheckRow(pal, tr("Celsius (°C)"), p.celsius) { p.celsius = true }
        CheckRow(pal, tr("Fahrenheit (°F)"), !p.celsius, true) { p.celsius = false }
    }
    Footnote(pal, tr("Choose how temperature should be displayed in the Weather app and widgets."))
    SectionLabel(pal, tr("Weather Auto Refresh"))
    Card(pal) {
        CheckRow(pal, tr("Every hour"), p.weatherRefreshHours == 1) { p.weatherRefreshHours = 1 }
        CheckRow(pal, tr("Every 6 hours"), p.weatherRefreshHours == 6) { p.weatherRefreshHours = 6 }
        CheckRow(pal, tr("Every day"), p.weatherRefreshHours == 24, true) { p.weatherRefreshHours = 24 }
    }
    Card(pal) {
        SetRow(pal, Icons.Filled.WbSunny, Color(0xFF3B82F6), tr("Allow location for the weather"), true) { st.requestLocation() }
    }
}
