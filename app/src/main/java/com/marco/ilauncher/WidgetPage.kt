package com.marco.ilauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Pagina "Widget": una colonna scorrevole dove si aggiungono widget (Edit > Add Widget). */
@Composable
fun WidgetsPage(st: LauncherState, geo: Geo, dark: Boolean) {
    val items = st.widgetPage
    val editing = st.editing
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = pxDp(geo.gridTop), bottom = 60.dp)
    ) {
        for (item in items) {
            key(item.id) {
                val cur by rememberUpdatedState(item)
                val wPx = item.w * geo.cellW
                val hPx = item.h * geo.cellH
                Box(Modifier.padding(start = pxDp(geo.gridLeft)).size(pxDp(wPx), pxDp(hPx))) {
                    ItemContent(st, item, wPx, hPx, 0f, false, editing, dark, geo.density)
                    if (editing) {
                        Box(Modifier.size(40.dp).clickable { st.removeW(item) })
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(44.dp).pointerInput(item.id) {
                                var accX = 0f
                                var accY = 0f
                                var baseW = 1
                                var baseH = 1
                                detectDragGestures(
                                    onDragStart = {
                                        accX = 0f
                                        accY = 0f
                                        baseW = cur.w
                                        baseH = cur.h
                                    },
                                    onDrag = { change, d ->
                                        change.consume()
                                        accX += d.x
                                        accY += d.y
                                        val nw = ((baseW * geo.cellW + accX) / geo.cellW).roundToInt().coerceIn(1, geo.cols)
                                        val nh = ((baseH * geo.cellH + accY) / geo.cellH).roundToInt().coerceIn(1, 8)
                                        if (nw != cur.w || nh != cur.h) st.resizeW(cur, nw, nh)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                tr("Edit") + " › " + tr("Add Widget"), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp,
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}
