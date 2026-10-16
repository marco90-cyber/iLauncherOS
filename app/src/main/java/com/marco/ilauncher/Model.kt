package com.marco.ilauncher

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

enum class Kind { APP, SYS, WIDGET_B, WIDGET_A, FOLDER }

/**
 * Elemento della home.
 * APP: key = "pkg/class"; SYS: key = "settings"; WIDGET_B: widget interno (clock, calendar...);
 * WIDGET_A: widget Android, key = id del widget.
 */
data class Item(
    val id: String,
    val kind: Kind,
    val key: String,
    val w: Int = 1,
    val h: Int = 1,
    /** Posizione fissa (solo con "spazi vuoti" attivo). Nel dock: col = slot. -1 = automatica. */
    val col: Int = -1,
    val row: Int = -1,
    /** Cartelle: nome e chiavi delle app contenute. */
    val name: String = "",
    val apps: List<String> = emptyList()
) {
    val isWidget: Boolean get() = kind == Kind.WIDGET_B || kind == Kind.WIDGET_A
}

fun newId(): String = java.lang.Long.toString(System.nanoTime(), 36) + (0..999).random()

fun Item.toJson(): JSONObject =
    JSONObject().put("id", id).put("k", kind.name).put("key", key).put("w", w).put("h", h).put("c", col).put("r", row).put("n", name).put("a", JSONArray(apps))

fun itemFromJson(o: JSONObject): Item? = try {
    Item(o.getString("id"), Kind.valueOf(o.getString("k")), o.getString("key"), o.optInt("w", 1), o.optInt("h", 1), o.optInt("c", -1), o.optInt("r", -1),
        o.optString("n", ""),
        o.optJSONArray("a")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList())
} catch (e: Exception) {
    null
}

fun pagesToJson(pages: List<List<Item>>, dock: List<Item>): String {
    val p = JSONArray()
    pages.forEach { pg ->
        val a = JSONArray()
        pg.forEach { a.put(it.toJson()) }
        p.put(a)
    }
    val d = JSONArray()
    dock.forEach { d.put(it.toJson()) }
    return JSONObject().put("pages", p).put("dock", d).toString()
}

fun pagesFromJson(s: String): Pair<List<List<Item>>, List<Item>>? = try {
    val o = JSONObject(s)
    val p = o.getJSONArray("pages")
    val pages = (0 until p.length()).map { i ->
        val a = p.getJSONArray(i)
        (0 until a.length()).mapNotNull { itemFromJson(a.getJSONObject(it)) }
    }
    val d = o.getJSONArray("dock")
    val dock = (0 until d.length()).mapNotNull { itemFromJson(d.getJSONObject(it)) }
    pages to dock
} catch (e: Exception) {
    null
}

data class Placed(val item: Item, val col: Int, val row: Int)

/** Dispone gli elementi nell'ordine, ognuno nel primo spazio libero. Ciò che non entra va in overflow. */
fun layoutPage(items: List<Item>, cols: Int, rows: Int, free: Boolean = false): Pair<List<Placed>, List<Item>> {
    if (!free) return layoutSeq(items, cols, rows)
    val occ = Array(rows) { BooleanArray(cols) }
    val pos = HashMap<String, Placed>()
    val over = ArrayList<Item>()
    fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
        if (c < 0 || r < 0 || c + w > cols || r + h > rows) return false
        for (yy in r until r + h) for (xx in c until c + w) if (occ[yy][xx]) return false
        return true
    }
    fun mark(c: Int, r: Int, w: Int, h: Int) {
        for (yy in r until r + h) for (xx in c until c + w) occ[yy][xx] = true
    }
    for (it in items) {
        if (it.col >= 0 && it.row >= 0) {
            val w = min(it.w, cols)
            val h = min(it.h, rows)
            if (fits(it.col, it.row, w, h)) {
                mark(it.col, it.row, w, h)
                pos[it.id] = Placed(it, it.col, it.row)
            }
        }
    }
    for (it in items) {
        if (pos.containsKey(it.id)) continue
        val w = min(it.w, cols)
        val h = min(it.h, rows)
        var done = false
        var r = 0
        while (r + h <= rows && !done) {
            var c = 0
            while (c + w <= cols && !done) {
                if (fits(c, r, w, h)) {
                    mark(c, r, w, h)
                    pos[it.id] = Placed(it, c, r)
                    done = true
                }
                c++
            }
            r++
        }
        if (!done) over.add(it)
    }
    return items.mapNotNull { pos[it.id] } to over
}

private fun layoutSeq(items: List<Item>, cols: Int, rows: Int): Pair<List<Placed>, List<Item>> {
    val occ = Array(rows) { BooleanArray(cols) }
    val out = ArrayList<Placed>()
    val over = ArrayList<Item>()
    var full = false
    for (it in items) {
        if (full) {
            over.add(it)
            continue
        }
        val w = min(it.w, cols)
        val h = min(it.h, rows)
        var done = false
        var r = 0
        while (r + h <= rows && !done) {
            var c = 0
            while (c + w <= cols && !done) {
                var free = true
                for (yy in r until r + h) for (xx in c until c + w) if (occ[yy][xx]) free = false
                if (free) {
                    for (yy in r until r + h) for (xx in c until c + w) occ[yy][xx] = true
                    out.add(Placed(it, c, r))
                    done = true
                }
                c++
            }
            r++
        }
        if (!done) {
            full = true
            over.add(it)
        }
    }
    return out to over
}

/** Ridistribuisce gli elementi sulle pagine: ciò che non entra scivola nella pagina successiva. */
fun repack(pages: List<List<Item>>, cols: Int, rows: Int, free: Boolean = false): List<List<Item>> {
    val out = ArrayList<List<Item>>()
    var carry: List<Item> = emptyList()
    var i = 0
    while (i < pages.size || carry.isNotEmpty()) {
        val items = carry + (if (i < pages.size) pages[i] else emptyList())
        val (placed, over) = layoutPage(items, cols, rows, free)
        out.add(placed.map { it.item })
        carry = over.map { it.copy(col = -1, row = -1) }
        i++
    }
    val nonEmpty = out.filter { it.isNotEmpty() }
    return if (nonEmpty.isEmpty()) listOf(emptyList()) else nonEmpty
}

// ---------- Traduzioni (inglese come nel video, italiano opzionale) ----------

object Lang {
    var it: Boolean = false
}

private val IT = mapOf(
    "Edit" to "Modifica", "Done" to "Fine", "Search" to "Cerca", "Add Widget" to "Aggiungi widget",
    "Customize Theme" to "Personalizza tema", "Edit Wallpaper" to "Modifica sfondo",
    "Launcher Settings" to "Impostazioni launcher", "Settings" to "Impostazioni", "Functions" to "Funzioni",
    "Go To Launcher" to "Vai al launcher", "Explore your OS interface" to "Esplora la tua interfaccia OS",
    "Set Launcher OS As Default" to "Imposta Launcher OS come predefinito",
    "To access all the features of Launcher OS, you need to set it as the default application." to
        "Per usare tutte le funzioni di Launcher OS devi impostarlo come app predefinita.",
    "Set As Default" to "Imposta come predefinito", "General" to "Generali",
    "Select Default Launcher" to "Scegli launcher predefinito", "Change App Icon" to "Cambia icona app",
    "Homescreen Style" to "Stile schermata Home", "Screen Grid" to "Griglia schermo",
    "Page Transition" to "Transizione pagina", "Hidden Apps" to "App nascoste", "App Library" to "Libreria app",
    "Change App Name" to "Cambia nome app", "Badge Notifications" to "Badge notifiche", "Language" to "Lingua",
    "Appearance" to "Aspetto", "App Functions Settings" to "Impostazioni funzioni", "Weather" to "Meteo",
    "Options" to "Opzioni", "Hide Navigation" to "Nascondi navigazione",
    "Auto arrange home screen" to "Aggiungi nuove app alla Home", "Blur effect" to "Effetto sfocatura",
    "Change App Size" to "Dimensione app", "Hide app name" to "Nascondi nome app",
    "Flexible Text Size" to "Dimensione testo flessibile", "Light" to "Chiaro", "Dark" to "Scuro", "Auto" to "Auto",
    "System" to "Sistema",
    "Your interface will automatically change according to the time of day." to
        "L'interfaccia cambia automaticamente in base all'ora del giorno.",
    "Your interface will be adjusted according to system settings." to
        "L'interfaccia segue le impostazioni di sistema.",
    "Temperature Unit" to "Unità di temperatura", "Celsius (°C)" to "Celsius (°C)", "Fahrenheit (°F)" to "Fahrenheit (°F)",
    "Weather Auto Refresh" to "Aggiornamento automatico meteo", "Every day" to "Ogni giorno",
    "Every 6 hours" to "Ogni 6 ore", "Every hour" to "Ogni ora",
    "Choose how temperature should be displayed in the Weather app and widgets." to
        "Scegli come mostrare la temperatura nell'app Meteo e nei widget.",
    "Search Widgets" to "Cerca widget", "Widgets" to "Widget", "App Suggestions" to "App suggerite",
    "Remove App" to "Rimuovi app", "Remove from Home Screen" to "Rimuovi dalla Home", "Edit Home Screen" to "Modifica schermata Home",
    "App Info" to "Info app", "Uninstall" to "Disinstalla", "Rename" to "Rinomina", "Cancel" to "Annulla", "OK" to "OK",
    "Recently Added" to "Aggiunte di recente", "Social" to "Social", "Entertainment" to "Intrattenimento",
    "Creativity" to "Creatività", "Productivity" to "Produttività", "Games" to "Giochi", "Utilities" to "Utilità",
    "Other" to "Altro", "Suggestions" to "Suggerimenti", "Search the web" to "Cerca sul web", "Apps" to "App",
    "Show Search Pill" to "Mostra barra Cerca", "Show Page Dots" to "Mostra indicatori pagina", "Show Dock" to "Mostra Dock",
    "Widget Captions" to "Nomi dei widget", "Dim wallpaper in dark mode" to "Scurisci sfondo in modalità scura",
    "Show App Library" to "Mostra libreria app", "Show badges" to "Mostra badge",
    "Grant notification access" to "Concedi accesso alle notifiche",
    "Badges need notification access. Tap to open the system settings." to
        "I badge richiedono l'accesso alle notifiche. Tocca per aprire le impostazioni di sistema.",
    "Icon shape" to "Forma icone", "Squircle" to "Squircle", "Circle" to "Cerchio", "Rounded square" to "Quadrato arrotondato",
    "Icon size" to "Dimensione icone", "Slide" to "Scorrimento", "Cube" to "Cubo", "Zoom" to "Zoom", "Fade" to "Dissolvenza",
    "Hidden apps are not shown on the Home Screen, App Library or search." to
        "Le app nascoste non compaiono in Home, Libreria app e ricerca.",
    "Tap an app to rename it." to "Tocca un'app per rinominarla.", "App name" to "Nome app",
    "System default" to "Predefinita di sistema", "Open system language settings" to "Apri le impostazioni lingua di sistema",
    "Widget" to "Widget", "Clock" to "Orologio", "Clock Digital" to "Orologio digitale", "Calendar" to "Calendario",
    "Battery" to "Batteria", "Forecast" to "Previsioni", "Status" to "Stato", "Month" to "Mese",
    "Small" to "Piccolo", "Medium" to "Medio", "Large" to "Grande", "Resize widget" to "Ridimensiona widget",
    "Launcher" to "Launcher", "Message" to "Messaggio", "Back" to "Indietro",
    "Built-in widgets" to "Widget integrati", "Widgets from apps" to "Widget delle app",
    "Allow location for the weather" to "Consenti la posizione per il meteo",
    "No results" to "Nessun risultato", "Select Default Launcher " to "Scegli launcher predefinito",
    "Choose photo" to "Scegli una foto", "Use system wallpaper" to "Usa lo sfondo di sistema",
    "Create folder" to "Crea cartella", "Style" to "Stile", "Dock" to "Dock", "Glass Dock" to "Dock Glass", "Classic Dock" to "Dock Classico", "Folder" to "Cartella", "Folder name" to "Nome cartella", "Last page" to "Ultima pagina",
    "Screen rotation" to "Rotazione schermo", "Follow system" to "Segui il sistema", "Lock portrait" to "Blocca in verticale", "Landscape" to "Orizzontale", "Allow empty spaces" to "Consenti spazi vuoti",
    "Icons stay where you drop them, leaving empty spaces on the Home Screen and in the Dock." to "Le icone restano dove le lasci, con spazi vuoti nella Home e nel Dock.",
    "Hide the navigation bar. Swipe from the edge to show it." to "Nasconde la barra di navigazione. Scorri dal bordo per mostrarla.",
    "Add new apps to the Home Screen as they are installed." to "Aggiunge le nuove app alla Home appena installate."
)

fun tr(s: String): String = if (Lang.it) (IT[s] ?: s) else s
