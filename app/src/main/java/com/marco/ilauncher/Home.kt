@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.marco.ilauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Colori dell'interfaccia in base al tema chiaro/scuro. */
class Pal(val dark: Boolean) {
    val bg = if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val text = if (dark) Color.White else Color.Black
    val sub = if (dark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val sep = if (dark) Color(0xFF38383A) else Color(0xFFD1D1D6)
    val accent = Color(0xFF0A84FF)
    val dock = if (dark) Color(0x55000000) else Color(0x55FFFFFF)
    val dockSolid = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
}

@Composable
fun pxDp(px: Float): Dp = with(LocalDensity.current) { px.toDp() }

fun iconPxFor(geo: Geo, prefs: LauncherPrefs): Float =
    min(geo.cellW * 0.72f, geo.cellH * 0.62f) * prefs.iconScale

fun labelPx(geo: Geo, prefs: LauncherPrefs, dock: Boolean): Float =
    if (dock || prefs.hideNames) 0f else geo.density * 16f

fun badgeCenter(rect: Rect, item: Item, iconPx: Float, labelH: Float, density: Float): Offset =
    if (item.isWidget) Offset(rect.left + 10f * density, rect.top + 10f * density)
    else Offset(
        rect.center.x - iconPx / 2f + 4f * density,
        rect.top + (rect.height - iconPx - labelH) / 2f + 4f * density
    )

fun hitItem(st: LauncherState, geo: Geo, page: Int, p: Offset): Pair<Item, Rect>? {
    if (geo.showDock && p.y >= geo.dockTop) {
        val d = st.shownDock()
        for ((i, it) in d.withIndex()) {
            val slot = if (st.prefs.freeLayout && it.col >= 0) it.col else i
            val r = Rect(geo.dockCellLeft(slot), geo.dockTop, geo.dockCellLeft(slot) + geo.dockCellW, geo.dockTop + geo.dockH)
            if (r.contains(p)) return it to r
        }
        return null
    }
    val pg = st.shownPages().getOrNull(page) ?: return null
    val (placed, _) = layoutPage(pg, geo.cols, geo.rows, st.prefs.freeLayout)
    for (pl in placed) {
        val r = Rect(
            geo.cellLeft(pl.col), geo.cellTop(pl.row),
            geo.cellLeft(pl.col) + pl.item.w * geo.cellW, geo.cellTop(pl.row) + pl.item.h * geo.cellH
        )
        if (r.contains(p)) return pl.item to r
    }
    return null
}

@Composable
fun jiggle(seed: Int): Float {
    val t = rememberInfiniteTransition(label = "jiggle")
    val v by t.animateFloat(
        initialValue = -1.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(110 + (seed % 5) * 9, easing = LinearEasing), RepeatMode.Reverse),
        label = "j"
    )
    return v
}

fun Modifier.pageFx(pager: PagerState, page: Int, kind: String): Modifier = graphicsLayer {
    val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
    val a = abs(off).coerceIn(0f, 1f)
    when (kind) {
        "cube" -> {
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(if (off > 0) 1f else 0f, 0.5f)
            rotationY = off * 55f
            alpha = 1f - a * 0.4f
        }
        "zoom" -> {
            val s = 1f - 0.3f * a
            scaleX = s
            scaleY = s
            alpha = 1f - a
        }
        "fade" -> {
            translationX = off * size.width
            alpha = 1f - a
        }
    }
}

/** Contenuto di un elemento (icona + nome oppure widget con didascalia). */
@Composable
fun ItemContent(
    st: LauncherState, item: Item, wPx: Float, hPx: Float, iconPx: Float,
    showLabel: Boolean, editing: Boolean, dark: Boolean, density: Float
) {
    val prefs = st.prefs
    if (item.isWidget) {
        val caption = prefs.widgetCaptions
        val capH = if (caption) 18f * density else 0f
        val padX = 6f * density
        val padY = 4f * density
        val areaW = wPx - 2 * padX
        val areaH = hPx - 2 * padY - capH
        Box(Modifier.offset(pxDp(padX), pxDp(padY)).size(pxDp(areaW), pxDp(areaH))) {
            if (item.kind == Kind.WIDGET_B) {
                BuiltinWidgetView(item.key, st)
            } else {
                val id = item.key.toIntOrNull()
                if (id != null) {
                    AndroidWidgetView(
                        st.widgets, id, (areaW / density).toInt(), (areaH / density).toInt()
                    ) { st.editing = true }
                }
            }
        }
        if (caption) {
            val title = if (item.kind == Kind.WIDGET_B) tr(builtinTitle(item.key))
            else st.widgets.awm.getAppWidgetInfo(item.key.toIntOrNull() ?: -1)
                ?.loadLabel(st.ctx.packageManager)?.toString().orEmpty()
            Text(
                title, color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.offset(0.dp, pxDp(hPx - padY - capH + 2f * density)).width(pxDp(wPx))
            )
        }
    } else {
        val labelH = if (showLabel) 16f * density else 0f
        val top = (hPx - iconPx - labelH) / 2f
        val bmp = if (item.kind == Kind.SYS) st.sysIconBitmap()
        else st.appOf(item.key)?.let { st.icon(it) }
        val label = if (item.kind == Kind.SYS) tr("Launcher")
        else if (item.kind == Kind.FOLDER) item.name
        else st.appOf(item.key)?.let { st.label(it) }.orEmpty()
        if (item.kind == Kind.FOLDER) {
            Box(
                Modifier.offset((pxDp(wPx) - pxDp(iconPx)) / 2, pxDp(top)).size(pxDp(iconPx))
                    .clip(RoundedCornerShape(pxDp(iconPx * 0.24f))).background(Color(0x66FFFFFF))
            ) {
                val pad = iconPx * 0.1f
                val gap = iconPx * 0.05f
                val mini = (iconPx - 2 * pad - 2 * gap) / 3f
                item.apps.take(9).forEachIndexed { i, k ->
                    val a = st.appOf(k)
                    if (a != null) {
                        Image(
                            st.icon(a), null,
                            Modifier.offset(pxDp(pad + (i % 3) * (mini + gap)), pxDp(pad + (i / 3) * (mini + gap))).size(pxDp(mini))
                        )
                    }
                }
            }
        }
        if (bmp != null) {
            Image(
                bmp, label, contentScale = ContentScale.Fit,
                modifier = Modifier.offset((pxDp(wPx) - pxDp(iconPx)) / 2, pxDp(top)).size(pxDp(iconPx))
            )
        }
        if (showLabel) {
            val scale = if (prefs.flexText) prefs.textScale else 1f
            Box(
                Modifier.offset(0.dp, pxDp(top + iconPx)).width(pxDp(wPx)).height(pxDp(labelH)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, color = Color.White, fontSize = (11f * scale).sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
        // badge notifiche
        if (!editing && prefs.badges && item.kind == Kind.APP) {
            val n = BadgeHub.counts[item.key.substringBefore('/')] ?: 0
            if (n > 0) {
                Box(
                    Modifier
                        .offset(pxDp((wPx + iconPx) / 2f - 14f * density), pxDp(top - 4f * density))
                        .size(20.dp).clip(CircleShape).background(Color(0xFFFF3B30)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (n > 99) "99" else n.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (editing && item.isWidget) {
        val hr = 11f * density
        Box(
            Modifier.offset(pxDp(wPx - 12f * density - hr), pxDp(hPx - 12f * density - hr)).size(22.dp)
                .clip(CircleShape).background(Color(0xDDFFFFFF)),
            contentAlignment = Alignment.Center
        ) { Text("⤡", color = Color(0xFF333333), fontSize = 14.sp) }
    }
    if (editing && !(item.kind == Kind.SYS)) {
        val c = badgeCenter(Rect(0f, 0f, wPx, hPx), item, iconPx, if (item.isWidget) 0f else (if (showLabel) 16f * density else 0f), density)
        MinusBadge(c, density)
    } else if (editing) {
        val c = badgeCenter(Rect(0f, 0f, wPx, hPx), item, iconPx, if (showLabel) 16f * density else 0f, density)
        MinusBadge(c, density)
    }
}

@Composable
fun MinusBadge(center: Offset, density: Float) {
    val r = 10f * density
    Box(
        Modifier.offset(pxDp(center.x - r), pxDp(center.y - r)).size(20.dp).clip(CircleShape)
            .background(Color(0xFF8E8E93)).border(1.dp, Color(0x44000000), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(10.dp).height(2.dp).background(Color.White))
    }
}

/** Singolo elemento sulla pagina: posizione animata, tap, menu contestuale. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeItem(
    st: LauncherState, item: Item, target: IntOffset, wPx: Float, hPx: Float,
    geo: Geo, iconPx: Float, showLabel: Boolean, inDock: Boolean, dark: Boolean
) {
    val ctx = LocalContext.current
    val anim by animateIntOffsetAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "pos")
    val editing = st.editing
    var menu by remember { mutableStateOf(false) }
    val hidden = st.drag?.id == item.id
    val rot = if (editing && !hidden) jiggle(item.id.hashCode().let { if (it < 0) -it else it }) else 0f
    val clickMod = if (editing) Modifier else Modifier.combinedClickable(
        onClick = {
            when (item.kind) {
                Kind.APP -> st.appOf(item.key)?.let { launchApp(ctx, it) }
                Kind.SYS -> st.settingsStack = listOf("main")
                Kind.FOLDER -> {
                    st.folderEditing = false
                    st.openFolder = item.id
                }
                else -> {}
            }
        },
        onLongClick = { st.editing = true }
    )
    Box(
        Modifier
            .offset { anim }
            .size(pxDp(wPx), pxDp(hPx))
            .alpha(if (hidden) 0f else 1f)
            .graphicsLayer {
                rotationZ = rot
                val sc = if (st.mergeTarget == item.id) 1.18f else 1f
                scaleX = sc
                scaleY = sc
            }
            .then(clickMod)
    ) {
        ItemContent(st, item, wPx, hPx, iconPx, showLabel, editing, dark, geo.density)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Edit Home Screen")) }, onClick = { menu = false; st.editing = true })
            if (item.kind == Kind.APP) {
                DropdownMenuItem(text = { Text(tr("Rename")) }, onClick = { menu = false; st.renameKey = item.key })
                DropdownMenuItem(text = { Text(tr("App Info")) }, onClick = { menu = false; openAppInfo(ctx, item.key.substringBefore('/')) })
                DropdownMenuItem(text = { Text(tr("Uninstall")) }, onClick = { menu = false; uninstallApp(ctx, item.key.substringBefore('/')) })
            }
            if (item.kind != Kind.SYS) {
                DropdownMenuItem(text = { Text(tr("Remove from Home Screen")) }, onClick = { menu = false; st.remove(item) })
            }
        }
    }
}

@Composable
fun HomePageGrid(st: LauncherState, geo: Geo, items: List<Item>, dark: Boolean) {
    val (placed, _) = layoutPage(items, geo.cols, geo.rows, st.prefs.freeLayout)
    val iconPx = iconPxFor(geo, st.prefs)
    Box(Modifier.fillMaxSize()) {
        for (pl in placed) {
            key(pl.item.id) {
                HomeItem(
                    st, pl.item,
                    IntOffset(geo.cellLeft(pl.col).roundToInt(), geo.cellTop(pl.row).roundToInt()),
                    pl.item.w * geo.cellW, pl.item.h * geo.cellH, geo, iconPx,
                    !st.prefs.hideNames, false, dark
                )
            }
        }
    }
}

@Composable
fun DockBar(st: LauncherState, geo: Geo, pal: Pal, dark: Boolean) {
    val iconPx = iconPxFor(geo, st.prefs)
    val shown = st.shownDock()
    Box(
        Modifier
            .offset(pxDp(geo.dockLeft), pxDp(geo.dockTop))
            .size(pxDp(geo.dockWidth), pxDp(geo.dockH))
            .clip(RoundedCornerShape(30.dp))
            .background(if (st.prefs.blur) pal.dock else pal.dockSolid)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(30.dp))
    )
    for ((i, it) in shown.withIndex()) {
        key(it.id) {
            HomeItem(
                st, it, IntOffset(geo.dockCellLeft(if (st.prefs.freeLayout && it.col >= 0) it.col else i).roundToInt(), geo.dockTop.roundToInt()),
                geo.dockCellW, geo.dockH, geo, iconPx, false, true, dark
            )
        }
    }
}

@Composable
fun PageDots(count: Int, current: Int, pal: Pal) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x55000000)).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        for (i in 0 until count) {
            Box(
                Modifier.padding(horizontal = 3.dp).size(7.dp).clip(CircleShape)
                    .background(if (i == current) Color.White else Color(0x66FFFFFF))
            )
        }
    }
}

/** Cambia pagina durante il trascinamento; oltre l'ultima pagina ne crea una nuova. */
fun flipPage(st: LauncherState, pager: PagerState, scope: kotlinx.coroutines.CoroutineScope, dir: Int) {
    val cur = pager.currentPage
    val count = st.shownPages().size
    if (dir < 0 && cur > 0) {
        scope.launch { pager.animateScrollToPage(cur - 1) }
    } else if (dir > 0 && cur < count - 1) {
        scope.launch { pager.animateScrollToPage(cur + 1) }
    } else if (dir > 0 && cur == count - 1) {
        val d = st.drag
        val others = st.pages.getOrNull(cur)?.any { it.id != d?.id } ?: false
        if (others) {
            st.tgtDock = false
            st.tgtPage = cur + 1
            st.tgtIndex = 0
            scope.launch {
                delay(150)
                pager.animateScrollToPage(cur + 1)
            }
        }
    }
}

fun rectOfItem(st: LauncherState, geo: Geo, page: Int, id: String): Rect? {
    val pg = st.shownPages().getOrNull(page) ?: return null
    val (placed, _) = layoutPage(pg, geo.cols, geo.rows, st.prefs.freeLayout)
    val pl = placed.firstOrNull { it.item.id == id } ?: return null
    return Rect(
        geo.cellLeft(pl.col), geo.cellTop(pl.row),
        geo.cellLeft(pl.col) + pl.item.w * geo.cellW, geo.cellTop(pl.row) + pl.item.h * geo.cellH
    )
}

@Composable
fun EditOverlay(st: LauncherState, geo: Geo, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val g by rememberUpdatedState(geo)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                val dens = g.density
                val hit0 = hitItem(st, g, pager.currentPage, down.position)
                // maniglia di ridimensionamento nell'angolo in basso a destra dei widget
                if (hit0 != null && hit0.first.isWidget &&
                    (down.position - Offset(hit0.second.right, hit0.second.bottom)).getDistance() < 30f * dens
                ) {
                    down.consume()
                    val wid = hit0.first.id
                    val page = pager.currentPage
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        val cur = st.pages.getOrNull(page)?.firstOrNull { it.id == wid } ?: break
                        val rr = rectOfItem(st, g, page, wid) ?: break
                        val nw = Math.ceil(((c.position.x - rr.left) / g.cellW).toDouble()).toInt().coerceIn(1, g.cols)
                        val nh = Math.ceil(((c.position.y - rr.top) / g.cellH).toDouble()).toInt().coerceIn(1, g.rows)
                        if (cur.w != nw || cur.h != nh) st.resize(cur, nw, nh)
                    }
                    return@awaitEachGesture
                }
                val outcome = withTimeoutOrNull(600_000L) {
                    var res = 0
                    while (res == 0) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id }
                        if (c == null) res = 2
                        else if (!c.pressed) res = 1
                        else if ((c.position - down.position).getDistance() > slop) res = 2
                    }
                    res
                }
                val homeCount = st.shownPages().size
                if (outcome == null || (outcome == 2 && hit0 != null)) {
                    // in modalità modifica basta trascinare: nessuna pressione lunga necessaria
                    val hit = hit0
                    if (hit != null) {
                        val (item, rect) = hit
                        st.beginDrag(item, down.position, down.position - Offset(rect.left, rect.top), Size(rect.width, rect.height))
                        down.consume()
                        var edgeDir = 0
                        val second = HashMap<Long, Float>()
                        var edgeSince = 0L
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            st.moveDrag(g, c.position, pager.currentPage)
                            val dir = if (c.position.x < 28f * dens) -1 else if (c.position.x > g.wPx - 28f * dens) 1 else 0
                            if (dir == 0) {
                                edgeDir = 0
                            } else if (edgeDir != dir) {
                                edgeDir = dir
                                edgeSince = c.uptimeMillis
                            } else if (c.uptimeMillis - edgeSince > 650) {
                                edgeSince = c.uptimeMillis
                                flipPage(st, pager, scope, dir)
                            }
                            // secondo dito: cambia pagina (tocco a sinistra/destra o scorrimento)
                            for (o in ev.changes) {
                                if (o.id == down.id) continue
                                if (o.pressed && !o.previousPressed) {
                                    second[o.id.value] = o.position.x
                                } else if (!o.pressed && o.previousPressed) {
                                    val sx = second.remove(o.id.value)
                                    if (sx != null) {
                                        val dx = o.position.x - sx
                                        val dirS = if (dx < -50f * dens) 1 else if (dx > 50f * dens) -1
                                        else if (o.position.x < g.wPx / 2f) -1 else 1
                                        flipPage(st, pager, scope, dirS)
                                    }
                                }
                                o.consume()
                            }
                        }
                        st.endDrag()
                    }
                } else if (outcome == 1) {
                    // tap
                    val hit = hitItem(st, g, pager.currentPage, down.position)
                    if (hit != null) {
                        val (item, rect) = hit
                        val inDock = g.showDock && down.position.y >= g.dockTop
                        val lh = if (item.isWidget) 0f else labelPx(g, st.prefs, inDock)
                        val bc = badgeCenter(rect, item, iconPxFor(g, st.prefs), lh, dens)
                        if ((down.position - bc).getDistance() < 24f * dens) {
                            if (item.kind == Kind.APP) uninstallApp(st.ctx, item.key.substringBefore('/')) else if (item.kind == Kind.FOLDER) st.dissolveFolder(item) else st.remove(item)
                        }
                    }
                } else {
                    // swipe orizzontale tra le pagine
                    var total = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        total = c.position.x - down.position.x
                        if (!c.pressed) break
                        c.consume()
                    }
                    val cur = pager.currentPage
                    if (total < -60f * dens && cur < homeCount - 1) scope.launch { pager.animateScrollToPage(cur + 1) }
                    else if (total > 60f * dens && cur > 0) scope.launch { pager.animateScrollToPage(cur - 1) }
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(st: LauncherState, pal: Pal, dark: Boolean) {
    val density = LocalDensity.current
    val ctx = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val top = WindowInsets.statusBars.getTop(density).toFloat()
        val bottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val prefs = st.prefs
        val geo = Geo(density.density, wPx, hPx, top, bottom, prefs.cols, prefs.rows, prefs.showDock)
        val shown = st.shownPages()
        val lib = prefs.showLibrary
        val count = shown.size + if (lib) 1 else 0
        val pager = rememberPagerState { count }
        val scope = rememberCoroutineScope()

        // usciti dalla modalità modifica dalla libreria: resta sulle pagine home
        val widgetsMode = prefs.lastPage == "widgets"
        LaunchedEffect(st.editing) {
            if (st.editing && !widgetsMode && pager.currentPage >= shown.size) pager.animateScrollToPage(shown.size - 1)
        }
        LaunchedEffect(st.homeTick) {
            if (st.homeTick > 0) pager.animateScrollToPage(0)
        }
        LaunchedEffect(pager.currentPage, st.drag) {
            if (st.drag != null) st.moveDrag(geo, st.dragFinger, pager.currentPage)
        }

        st.wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (prefs.dimWallpaper && dark) Box(Modifier.fillMaxSize().background(Color(0x55000000)))

        Box(
            Modifier.fillMaxSize().pointerInput(st.editing) {
                if (!st.editing) detectTapGestures(onLongPress = { st.editing = true })
            }.pointerInput(st.editing, pager.currentPage >= shown.size) {
                if (!st.editing && pager.currentPage < shown.size) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > 120f * geo.density) expandNotifications(st.ctx)
                            else if (total < -120f * geo.density) st.showSearch = true
                        },
                        onDragCancel = {},
                        onVerticalDrag = { _, dy -> total += dy }
                    )
                }
            }
        ) {
            HorizontalPager(
                state = pager, modifier = Modifier.fillMaxSize(),
                userScrollEnabled = (!st.editing || pager.currentPage >= shown.size) && st.drag == null
            ) { page ->
                Box(Modifier.fillMaxSize().pageFx(pager, page, prefs.transition)) {
                    if (page < shown.size) HomePageGrid(st, geo, shown[page], dark)
                    else if (widgetsMode) WidgetsPage(st, geo, dark)
                    else LibraryPage(st, pal, dark)
                }
            }
        }

        // indicatore pagine / barra Cerca
        if (!(pager.currentPage >= shown.size)) {
            Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
            ) {
                Box(Modifier.offset(0.dp, pxDp(geo.dotsCenterY - 14f * geo.density))) {
                    if (prefs.showSearch && count == 1 && !st.editing) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0x55000000))
                                .clickable { st.showSearch = true }.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔍 " + tr("Search"), color = Color.White, fontSize = 12.sp)
                        }
                    } else if (prefs.showDots && count > 1) {
                        PageDots(count, pager.currentPage, pal)
                    }
                }
            }
        }

        if (prefs.showDock && pager.currentPage < shown.size) DockBar(st, geo, pal, dark)

        if (st.editing && !(widgetsMode && pager.currentPage >= shown.size)) EditOverlay(st, geo, pager)

        // elemento trascinato
        val d = st.drag
        if (d != null) {
            val iconPx = iconPxFor(geo, prefs)
            Box(
                Modifier
                    .offset { IntOffset((st.dragFinger.x - st.dragGrab.x).roundToInt(), (st.dragFinger.y - st.dragGrab.y).roundToInt()) }
                    .size(pxDp(st.dragSize.width), pxDp(st.dragSize.height))
                    .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.92f }
            ) {
                ItemContent(st, d, st.dragSize.width, st.dragSize.height, iconPx, !st.dragInDock && !prefs.hideNames, true, dark, geo.density)
            }
        }

        // barra superiore della modalità modifica
        if (st.editing) {
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.offset(12.dp, pxDp(top + 6f * geo.density))) {
                Text(
                    tr("Edit"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x88000000))
                        .clickable { menu = true }.padding(horizontal = 16.dp, vertical = 7.dp)
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Add Widget")) }, onClick = {
                        menu = false
                        st.pickerPage = pager.currentPage
                        st.showPicker = true
                    })
                    DropdownMenuItem(text = { Text(tr("Create folder")) }, onClick = {
                        menu = false
                        st.pickerPage = pager.currentPage
                        st.showCreateFolder = true
                    })
                    DropdownMenuItem(text = { Text(tr("Customize Theme")) }, onClick = {
                        menu = false
                        st.settingsStack = listOf("main", "appearance")
                    })
                    DropdownMenuItem(text = { Text(tr("Edit Wallpaper")) }, onClick = {
                        menu = false
                        st.pickWallpaper()
                    })
                    DropdownMenuItem(text = { Text(tr("Launcher Settings")) }, onClick = {
                        menu = false
                        st.settingsStack = listOf("main")
                    })
                }
            }
            Box(Modifier.fillMaxSize().offset(0.dp, pxDp(top + 6f * geo.density)), contentAlignment = Alignment.TopEnd) {
                Text(
                    tr("Done"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(50)).background(Color(0x88000000))
                        .clickable { st.editing = false }.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}
