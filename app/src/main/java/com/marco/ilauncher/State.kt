package com.marco.ilauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

/** Misure della home in pixel, condivise tra disegno e gestione dei tocchi. */
class Geo(
    val density: Float,
    val wPx: Float,
    val hPx: Float,
    val topInset: Float,
    val botInset: Float,
    val cols: Int,
    val rows: Int,
    val showDock: Boolean
) {
    private fun dp(v: Float) = v * density

    val gridLeft = dp(6f)
    val gridWidth = wPx - dp(12f)
    val gridTop = topInset + dp(46f)
    private val land = wPx > hPx
    val dockH = if (showDock) dp(if (land) 66f else 88f) else 0f
    val dockWidth = if (land) minOf(wPx - dp(24f), wPx * 0.5f) else wPx - dp(24f)
    val dockLeft = (wPx - dockWidth) / 2f
    val dockTop = hPx - botInset - dp(10f) - dockH
    private val dotsH = dp(34f)
    val dotsCenterY = (if (showDock) dockTop else hPx - botInset) - dp(8f) - dotsH / 2f
    val gridBottom = (if (showDock) dockTop else hPx - botInset) - dotsH - dp(6f)
    val cellW = gridWidth / cols
    val cellH = (gridBottom - gridTop) / rows
    val dockPad = dp(8f)
    val dockCellW = (dockWidth - 2 * dockPad) / cols

    fun cellLeft(col: Int) = gridLeft + col * cellW
    fun cellTop(row: Int) = gridTop + row * cellH
    fun dockCellLeft(i: Int) = dockLeft + dockPad + i * dockCellW
}

class LauncherState(val ctx: Context, private val sp: SharedPreferences) {
    val prefs = LauncherPrefs(sp)
    private var known by SetPref(sp, "known")

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
    var pages by mutableStateOf<List<List<Item>>>(listOf(emptyList()))
    var dock by mutableStateOf<List<Item>>(emptyList())
    var editing by mutableStateOf(false)
    var iconVersion by mutableIntStateOf(0)
    private val iconCache = HashMap<String, ImageBitmap>()
    private var sysIcon: ImageBitmap? = null
    lateinit var widgets: WidgetController
    var requestLocation: () -> Unit = {}

    // ---- interfaccia ----
    var showPicker by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var settingsStack by mutableStateOf<List<String>>(emptyList())
    var renameKey by mutableStateOf<String?>(null)
    var resizeItem by mutableStateOf<Item?>(null)
    var widgetPage by mutableStateOf<List<Item>>(emptyList())
    var openFolder by mutableStateOf<String?>(null)
    var folderEditing by mutableStateOf(false)
    var mergeTarget by mutableStateOf<String?>(null)
    var hoverId by mutableStateOf<String?>(null)
    /** Rotazione (gradi, continua) da applicare a icone e widget per restare dritti quando il telefono è inclinato. */
    var contentRot by mutableStateOf(0f)
    private var snapQ = 0
    var sysAutoRotate by mutableStateOf(true)
    fun refreshAutoRotate() {
        sysAutoRotate = try {
            android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        } catch (e: Exception) { true }
    }
    /** "Segui il sistema": layout fisso verticale, icone ruotate se la rotazione automatica di sistema è attiva. */
    val rotActive: Boolean get() = (prefs.orientation == "system" || prefs.orientation == "rotate") && sysAutoRotate
    /** Quarti di giro (0..3) del contenuto: 1 = telefono inclinato a destra... usato per i gesti. */
    val tiltQ: Int get() = if (!rotActive) 0 else (((Math.round(contentRot / 90f) % 4) + 4) % 4)
    fun onDeviceAngle(o: Int) {
        if (o < 0) return
        val q = Math.round(o / 90f) % 4
        if (q == snapQ) return
        if (Math.abs(o - q * 90 - (if (q == 0 && o > 180) 360 else 0)) > 30) return
        var d = q - snapQ
        if (d > 2) d -= 4
        if (d < -2) d += 4
        contentRot -= d * 90f
        snapQ = q
    }
    var showCreateFolder by mutableStateOf(false)
    var removeTarget by mutableStateOf<Item?>(null)
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
    var wallpaperDialog by mutableStateOf(false)
    var pickWallpaper: () -> Unit = {}

    fun loadWallpaper() {
        try {
            val f = java.io.File(ctx.filesDir, "wallpaper.jpg")
            if (f.exists()) wallpaper = BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
        }
    }

    fun setWallpaperFromUri(uri: android.net.Uri) {
        try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
            var sample = 1
            while (b.outWidth / sample > 2400 || b.outHeight / sample > 2400) sample *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } ?: return
            java.io.FileOutputStream(java.io.File(ctx.filesDir, "wallpaper.jpg")).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            wallpaper = bmp.asImageBitmap()
        } catch (e: Exception) {
        }
    }

    fun clearWallpaper() {
        try {
            java.io.File(ctx.filesDir, "wallpaper.jpg").delete()
        } catch (e: Exception) {
        }
        wallpaper = null
    }
    var pickerPage = 0
    var homeTick by mutableIntStateOf(0)
    var locationTick by mutableIntStateOf(0)

    // ---- trascinamento ----
    var drag by mutableStateOf<Item?>(null)
    var dragFinger by mutableStateOf(Offset.Zero)
    var dragGrab by mutableStateOf(Offset.Zero)
    var dragSize by mutableStateOf(Size.Zero)
    var dragInDock by mutableStateOf(false)
    var tgtDock by mutableStateOf(false)
    var tgtPage by mutableIntStateOf(0)
    var tgtIndex by mutableIntStateOf(0)

    val visibleApps: List<AppInfo>
        get() = apps.filter { it.key !in prefs.hidden }

    fun appOf(key: String): AppInfo? = apps.firstOrNull { it.key == key }

    fun label(app: AppInfo): String = prefs.names[app.key] ?: app.label

    fun icon(app: AppInfo): ImageBitmap {
        @Suppress("UNUSED_VARIABLE")
        val v = iconVersion
        return iconCache.getOrPut(app.key) { renderIcon(app.drawable, prefs.iconShape) }
    }

    fun sysIconBitmap(): ImageBitmap {
        @Suppress("UNUSED_VARIABLE")
        val v = iconVersion
        return sysIcon ?: renderSysIcon(prefs.iconShape).also { sysIcon = it }
    }

    fun setIconShape(shape: String) {
        prefs.iconShape = shape
        iconCache.clear()
        sysIcon = null
        iconVersion++
    }

    // ---- caricamento e coerenza ----

    /** Disposizione mostrata: durante il trascinamento le cartelle restano ferme e le altre icone si adattano. */
    fun layoutShown(items: List<Item>): Pair<List<Placed>, List<Item>> {
        if (prefs.freeLayout || drag == null) return layoutPage(items, prefs.cols, prefs.rows, prefs.freeLayout)
        val pos = HashMap<String, Pair<Int, Int>>()
        for (pg in pages) for (pl in layoutPage(pg, prefs.cols, prefs.rows, false).first) pos[pl.item.id] = pl.col to pl.row
        val pinned = items.map {
            val p = pos[it.id]
            if (it.kind == Kind.FOLDER && p != null) it.copy(col = p.first, row = p.second) else it.copy(col = -1, row = -1)
        }
        return layoutPage(pinned, prefs.cols, prefs.rows, true)
    }

    fun folderById(id: String): Item? = pages.flatten().firstOrNull { it.id == id }

    fun renameFolder(id: String, name: String) {
        pages = pages.map { pg -> pg.map { if (it.id == id) it.copy(name = name) else it } }
        save()
    }

    fun removeFromFolder(id: String, key: String) {
        val f = folderById(id) ?: return
        val rest = f.apps.filter { it != key }
        if (rest.isEmpty()) {
            pages = rp(pages.map { pg -> pg.filter { it.id != id } })
            openFolder = null
            folderEditing = false
        } else {
            pages = pages.map { pg -> pg.map { if (it.id == id) it.copy(apps = rest) else it } }
        }
        addToHome(Item(newId(), Kind.APP, key))
    }

    fun dissolveFolder(f: Item) {
        pages = rp(pages.map { pg -> pg.filter { it.id != f.id } })
        f.apps.forEach { addToHome(Item(newId(), Kind.APP, it)) }
        openFolder = null
        folderEditing = false
        save()
    }

    fun createFolder(name: String, keys: List<String>, page: Int) {
        if (keys.isEmpty()) return
        val sel = keys.toSet()
        fun strip(l: List<Item>): List<Item> = l.mapNotNull {
            when {
                it.kind == Kind.APP && it.key in sel -> null
                it.kind == Kind.FOLDER -> {
                    val a = it.apps.filter { x -> x !in sel }
                    if (a.isEmpty()) null else it.copy(apps = a)
                }
                else -> it
            }
        }
        val np = pages.map { strip(it) }.toMutableList()
        val nd = strip(dock)
        val folder = Item(newId(), Kind.FOLDER, "", 1, 1, name = name.ifBlank { tr("Folder") }, apps = keys)
        val p = page.coerceIn(0, np.size - 1)
        np[p] = np[p] + folder
        pages = rp(np)
        dock = bakeDock(nd)
        save()
    }

    private fun mergeInto(targetId: String, d: Item) {
        val t = pages.flatten().firstOrNull { it.id == targetId } ?: return
        val nf = if (t.kind == Kind.FOLDER) t.copy(apps = t.apps + d.key)
        else Item(newId(), Kind.FOLDER, "", 1, 1, t.col, t.row, tr("Folder"), listOf(t.key, d.key))
        pages = rp(pages.map { pg ->
            pg.mapNotNull { if (it.id == d.id) null else if (it.id == t.id) nf else it }
        })
        dock = bakeDock(dock.filter { it.id != d.id })
    }

    fun removeW(item: Item) {
        if (item.kind == Kind.WIDGET_A) item.key.toIntOrNull()?.let { widgets.remove(it) }
        widgetPage = widgetPage.filter { it.id != item.id }
        save()
    }

    fun resizeW(item: Item, w: Int, h: Int) {
        widgetPage = widgetPage.map { if (it.id == item.id) it.copy(w = w, h = h) else it }
        save()
    }

    fun reload() {
        pagesFromJson(sp.getString("wpage", null) ?: "")?.first?.firstOrNull()?.let { widgetPage = it }
        if (wallpaper == null) loadWallpaper()
        apps = loadApps(ctx)
        if (apps.isEmpty()) return
        val saved = sp.getString("layout", null)?.let { pagesFromJson(it) }
        if (saved == null) {
            buildDefault()
        } else {
            pages = saved.first
            dock = saved.second
            reconcile()
        }
        known = known + apps.map { it.key }
        save()
    }

    private fun buildDefault() {
        val hidden = prefs.hidden
        val dockApps = ArrayList<AppInfo>()
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        )
        for (i in intents) {
            val pkg = try {
                pm.resolveActivity(i, 0)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
            val a = apps.firstOrNull { it.pkg == pkg && dockApps.none { d -> d.pkg == pkg } }
            if (a != null) dockApps.add(a)
        }
        apps.firstOrNull { it.pkg == "com.android.vending" }?.let { dockApps.add(it) }
        val dockSet = dockApps.take(prefs.cols).map { it.key }.toSet()
        dock = dockApps.take(prefs.cols).map { Item(newId(), Kind.APP, it.key) }
        val list = ArrayList<Item>()
        list.add(Item(newId(), Kind.WIDGET_B, "clock", 2, 2))
        list.add(Item(newId(), Kind.WIDGET_B, "calendar", 2, 2))
        list.add(Item(newId(), Kind.SYS, "settings"))
        apps.filter { it.key !in dockSet && it.key !in hidden }.forEach { list.add(Item(newId(), Kind.APP, it.key)) }
        pages = rp(listOf(list))
    }

    private fun reconcile() {
        val valid = apps.map { it.key }.toSet()
        val hidden = prefs.hidden
        fun fix(i: Item): Item? = when (i.kind) {
            Kind.APP -> if (i.key in valid && i.key !in hidden) i else null
            Kind.FOLDER -> {
                val a = i.apps.filter { it in valid && it !in hidden }
                if (a.isEmpty()) null else i.copy(apps = a)
            }
            else -> i
        }
        var np = pages.map { pg -> pg.mapNotNull { fix(it) } }
        val nd = dock.mapNotNull { fix(it) }
        if (prefs.autoArrange) {
            val fresh = apps.filter { it.key !in known && it.key !in hidden }
            if (fresh.isNotEmpty()) {
                val m = np.toMutableList()
                val extra = fresh.map { Item(newId(), Kind.APP, it.key) }
                if (m.isEmpty()) m.add(extra) else m[m.size - 1] = m.last() + extra
                np = m
            }
        }
        pages = rp(np)
        dock = nd
    }

    private fun rp(l: List<List<Item>>): List<List<Item>> {
        val free = prefs.freeLayout
        val r = repack(l, prefs.cols, prefs.rows, free)
        return if (free) r.map { pg -> bakePage(pg) } else r
    }

    private fun bakePage(pg: List<Item>): List<Item> =
        layoutPage(pg, prefs.cols, prefs.rows, true).first.map { it.item.copy(col = it.col, row = it.row) }

    /** Nel dock con spazi vuoti ogni elemento ha uno slot fisso (col). */
    private fun bakeDock(l: List<Item>): List<Item> {
        if (!prefs.freeLayout) return l.map { it.copy(col = -1, row = -1) }
        val used = HashSet<Int>()
        val out = ArrayList<Item>()
        for (it in l) if (it.col in 0 until prefs.cols && used.add(it.col)) out.add(it)
        for (it in l) {
            if (out.any { o -> o.id == it.id }) continue
            val slot = (0 until prefs.cols).firstOrNull { s -> s !in used } ?: continue
            used.add(slot)
            out.add(it.copy(col = slot))
        }
        return out.sortedBy { it.col }
    }

    fun setFree(v: Boolean) {
        prefs.freeLayout = v
        pages = rp(pages.map { pg -> pg.map { it.copy(col = -1, row = -1) } })
        dock = bakeDock(dock.map { it.copy(col = -1, row = -1) })
        save()
    }

    private var tgtCol = 0
    private var tgtRow = 0

    fun relayout() {
        pages = rp(pages)
        save()
    }

    fun save() {
        sp.edit().putString("layout", pagesToJson(pages, dock))
            .putString("wpage", pagesToJson(listOf(widgetPage), emptyList())).apply()
    }

    // ---- modifiche ----

    fun remove(item: Item) {
        if (item.kind == Kind.WIDGET_A) item.key.toIntOrNull()?.let { widgets.remove(it) }
        pages = rp(pages.map { pg -> pg.filter { it.id != item.id } })
        dock = bakeDock(dock.filter { it.id != item.id })
        save()
    }

    fun addToHome(item: Item) {
        val m = pages.toMutableList()
        m[m.size - 1] = m.last() + item
        pages = rp(m)
        save()
    }

    fun addWidget(kind: Kind, key: String, w: Int, h: Int, page: Int) {
        if (page >= pages.size && prefs.lastPage == "widgets") {
            widgetPage = widgetPage + Item(newId(), kind, key, w, h)
            save()
            return
        }
        val m = pages.toMutableList()
        val p = page.coerceIn(0, m.size - 1)
        m[p] = listOf(Item(newId(), kind, key, w, h)) + m[p]
        pages = rp(m)
        save()
    }

    fun resize(item: Item, w: Int, h: Int) {
        pages = rp(pages.map { pg -> pg.map { if (it.id == item.id) it.copy(w = w, h = h) else it } })
        save()
    }

    fun hideApp(key: String, hide: Boolean) {
        prefs.hidden = if (hide) prefs.hidden + key else prefs.hidden - key
        if (hide) {
            pages = rp(pages.map { pg -> pg.filter { !(it.kind == Kind.APP && it.key == key) } })
            dock = dock.filter { !(it.kind == Kind.APP && it.key == key) }
        } else if (prefs.autoArrange) {
            val onHome = (pages.flatten() + dock).any { it.kind == Kind.APP && it.key == key }
            if (!onHome && appOf(key) != null) addToHome(Item(newId(), Kind.APP, key))
        }
        save()
    }

    fun rename(key: String, name: String) {
        prefs.names = if (name.isBlank()) prefs.names - key else prefs.names + (key to name.trim())
    }

    // ---- trascinamento con anteprima del riordino ----

    fun beginDrag(item: Item, finger: Offset, grab: Offset, size: Size) {
        drag = item
        dragFinger = finger
        dragGrab = grab
        dragSize = size
        dragInDock = dock.any { it.id == item.id }
        mergeTarget = null
        hoverId = null
        tgtDock = dragInDock
        tgtIndex = 0
    }

    fun shownPages(): List<List<Item>> {
        val d = drag ?: return pages
        val base = pages.map { pg -> pg.filter { it.id != d.id } }.toMutableList()
        if (tgtDock || mergeTarget != null || hoverId != null) return rp(base)
        while (base.size <= tgtPage) base.add(emptyList())
        val p = tgtPage.coerceIn(0, base.size - 1)
        if (prefs.freeLayout) {
            val (c, r) = nearestFree(base[p], d, tgtCol, tgtRow)
            val l2 = base[p].toMutableList()
            l2.add(d.copy(col = c, row = r))
            base[p] = l2
            return repackKeepEmpty(base)
        }
        val l = base[p].toMutableList()
        l.add(tgtIndex.coerceIn(0, l.size), d)
        base[p] = l
        return repackKeepEmpty(base)
    }

    private fun nearestFree(page: List<Item>, d: Item, tc: Int, tr: Int): Pair<Int, Int> {
        val cols = prefs.cols
        val rows = prefs.rows
        val w = minOf(d.w, cols)
        val h = minOf(d.h, rows)
        val (placed, _) = layoutPage(page, cols, rows, true)
        var best: Pair<Int, Int>? = null
        var bestD = Int.MAX_VALUE
        for (r in 0..rows - h) for (c in 0..cols - w) {
            val ok = placed.none { p ->
                p.col < c + w && c < p.col + minOf(p.item.w, cols) && p.row < r + h && r < p.row + minOf(p.item.h, rows)
            }
            if (ok) {
                val dist = (c - tc) * (c - tc) + (r - tr) * (r - tr)
                if (dist < bestD) {
                    bestD = dist
                    best = c to r
                }
            }
        }
        return best ?: (-1 to -1)
    }

    private fun repackKeepEmpty(l: List<List<Item>>): List<List<Item>> {
        val out = ArrayList<List<Item>>()
        var carry: List<Item> = emptyList()
        var i = 0
        while (i < l.size || carry.isNotEmpty()) {
            val items = carry + (if (i < l.size) l[i] else emptyList())
            val (placed, over) = layoutPage(items, prefs.cols, prefs.rows, prefs.freeLayout)
            out.add(placed.map { it.item })
            carry = over.map { it.copy(col = -1, row = -1) }
            i++
        }
        return if (out.isEmpty()) listOf(emptyList()) else out
    }

    fun shownDock(): List<Item> {
        val d = drag ?: return dock
        val base = dock.filter { it.id != d.id }
        if (!tgtDock) return base
        if (prefs.freeLayout) {
            val used = base.map { it.col }.toSet()
            val slot = (0 until prefs.cols).filter { it !in used }.minByOrNull { Math.abs(it - tgtIndex) } ?: return base
            return (base + d.copy(col = slot)).sortedBy { it.col }
        }
        val l = base.toMutableList()
        l.add(tgtIndex.coerceIn(0, l.size), d)
        return l
    }

    fun moveDrag(geo: Geo, finger: Offset, currentPage: Int) {
        val d = drag ?: return
        dragFinger = finger
        val inDockZone = geo.showDock && finger.y > geo.dockTop - geo.density * 8f
        if (inDockZone) {
            mergeTarget = null
            if (d.kind == Kind.APP || d.kind == Kind.SYS) {
                val others = dock.filter { it.id != d.id }
                if (others.size < prefs.cols) {
                    tgtDock = true
                    tgtIndex = if (prefs.freeLayout)
                        ((finger.x - geo.dockLeft - geo.dockPad) / geo.dockCellW).toInt().coerceIn(0, prefs.cols - 1)
                    else
                        ((finger.x - geo.dockLeft - geo.dockPad) / geo.dockCellW).toInt().coerceIn(0, others.size)
                }
            }
        } else if (finger.y >= geo.gridTop - geo.density * 20f && finger.y <= geo.gridBottom + geo.density * 20f) {
            tgtDock = false
            tgtPage = currentPage
            val base = pages.getOrElse(currentPage) { emptyList() }.filter { it.id != d.id }
            if (d.kind == Kind.APP) {
                val (pl0, _) = layoutShown(base)
                var cand: Item? = null
                for (p in pl0) {
                    if (p.item.w != 1 || p.item.h != 1) continue
                    if (p.item.kind != Kind.APP && p.item.kind != Kind.FOLDER) continue
                    val cx = geo.cellLeft(p.col) + geo.cellW / 2f
                    val cy = geo.cellTop(p.row) + geo.cellH / 2f
                    val dist = Math.hypot((finger.x - cx).toDouble(), (finger.y - cy).toDouble()).toFloat()
                    if (dist < geo.cellW * 0.5f) cand = p.item
                }
                if (cand != null) {
                    if (cand.kind == Kind.FOLDER) {
                        // le cartelle restano ferme: ci si entra subito
                        hoverId = null
                        mergeTarget = cand.id
                    } else if (mergeTarget != cand.id) {
                        // sopra un'altra app: aspetta un secondo prima di spostare, per poter fare la cartella
                        mergeTarget = null
                        hoverId = cand.id
                    }
                    return
                }
                hoverId = null
                mergeTarget = null
            } else {
                mergeTarget = null
                hoverId = null
            }
            if (prefs.freeLayout) {
                val w = minOf(d.w, prefs.cols)
                val h = minOf(d.h, prefs.rows)
                val tlx = finger.x - dragGrab.x + geo.cellW / 2f - geo.gridLeft
                val tly = finger.y - dragGrab.y + geo.cellH / 2f - geo.gridTop
                tgtCol = (tlx / geo.cellW).toInt().coerceIn(0, prefs.cols - w)
                tgtRow = (tly / geo.cellH).toInt().coerceIn(0, prefs.rows - h)
                return
            }
            val (placed, _) = layoutShown(base)
            val c = ((finger.x - geo.gridLeft) / geo.cellW).toInt().coerceIn(0, prefs.cols - 1)
            val r = ((finger.y - geo.gridTop) / geo.cellH).toInt().coerceIn(0, prefs.rows - 1)
            val lin = r * prefs.cols + c
            var idx = base.size
            for ((i, pl) in placed.withIndex()) {
                if (pl.row * prefs.cols + pl.col >= lin) {
                    idx = i
                    break
                }
            }
            tgtIndex = idx
        }
    }

    fun endDrag() {
        val dd = drag ?: return
        val mt = mergeTarget
        hoverId = null
        if (mt != null && dd.kind == Kind.APP) {
            mergeInto(mt, dd)
            mergeTarget = null
            drag = null
            save()
            return
        }
        val newPages = shownPages()
        val newDock = shownDock()
        pages = rp(newPages)
        dock = bakeDock(newDock)
        drag = null
        save()
    }

    fun cancelDrag() {
        drag = null
    }
}
