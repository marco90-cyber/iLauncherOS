@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.marco.ilauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun WidgetPicker(st: LauncherState, pal: Pal) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    var query by remember { mutableStateOf("") }
    val providers = remember {
        st.widgets.providers().map { p ->
            Triple(
                p, p.loadLabel(ctx.packageManager).toString(),
                try {
                    p.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            )
        }
    }
    val q = query.trim().lowercase()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable { st.showPicker = false }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxSize()
                .padding(top = top + 40.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(pal.bg).clickable(enabled = false) {}
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(50)).background(pal.sep))
            SearchField(
                query, { query = it }, tr("Search Widgets"), pal.card, pal.text,
                Modifier.padding(16.dp).fillMaxWidth()
            )
            LazyColumn(Modifier.fillMaxSize()) {
                val builtins = BUILTINS.filter { q.isEmpty() || tr(it.title).lowercase().contains(q) || tr(it.subtitle).lowercase().contains(q) }
                if (builtins.isNotEmpty()) item {
                    Text(tr("Built-in widgets"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
                }
                items(builtins) { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            st.addWidget(Kind.WIDGET_B, b.key, b.w, b.h, st.pickerPage)
                            st.showPicker = false
                        }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
                            BuiltinWidgetView(b.key, st)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(tr(b.title), color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(tr(b.subtitle), color = pal.sub, fontSize = 13.sp)
                        }
                    }
                }
                val list = providers.filter { q.isEmpty() || it.second.lowercase().contains(q) }
                if (list.isNotEmpty()) item {
                    Text(tr("Widgets from apps"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
                }
                items(list) { (p, label, icon) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val (w, h) = sizeForProvider(p, density)
                            val page = st.pickerPage
                            st.showPicker = false
                            st.widgets.begin(p) { id ->
                                if (id != null) st.addWidget(Kind.WIDGET_A, id.toString(), w, h, page)
                            }
                        }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, label, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(label, color = pal.text, fontSize = 16.sp)
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}

@Composable
fun SearchScreen(st: LauncherState, pal: Pal) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val list = if (q.isEmpty()) st.visibleApps.take(8) else st.visibleApps.filter { st.label(it).lowercase().contains(q) }
    Box(Modifier.fillMaxSize().background(Color(0xDD000000)).clickable { st.showSearch = false }) {
        Column(Modifier.fillMaxSize().padding(top = top + 16.dp).clickable(enabled = false) {}) {
            SearchField(
                query, { query = it }, tr("Search"), Color(0x33FFFFFF), Color.White,
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { a ->
                    AppRow(st, a, Color.White) {
                        st.showSearch = false
                        launchApp(ctx, a)
                    }
                }
                if (q.isNotEmpty()) item {
                    Text(
                        "🔍 " + tr("Search the web") + ": " + query, color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            st.showSearch = false
                            try {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query))
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                            }
                        }.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDialog(st: LauncherState, key: String) {
    val app = st.appOf(key) ?: run { st.renameKey = null; return }
    var text by remember { mutableStateOf(st.label(app)) }
    AlertDialog(
        onDismissRequest = { st.renameKey = null },
        title = { Text(tr("Rename")) },
        text = { SearchField(text, { text = it }, tr("App name"), Color(0x22888888), Color.Gray) },
        confirmButton = { TextButton(onClick = { st.rename(key, text); st.renameKey = null }) { Text(tr("OK")) } },
        dismissButton = { TextButton(onClick = { st.renameKey = null }) { Text(tr("Cancel")) } }
    )
}

@Composable
fun ResizeDialog(st: LauncherState, item: Item) {
    val sizes = listOf(2 to 1, 2 to 2, 4 to 1, 4 to 2, 4 to 4)
    AlertDialog(
        onDismissRequest = { st.resizeItem = null },
        title = { Text(tr("Resize widget")) },
        text = {
            Column {
                sizes.forEach { (w, h) ->
                    Text(
                        "$w × $h" + if (w == item.w && h == item.h) "  ✓" else "", fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            st.resize(item, w, h)
                            st.resizeItem = null
                        }.padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { st.resizeItem = null }) { Text(tr("Cancel")) } }
    )
}

@Composable
fun RemoveDialog(st: LauncherState, item: Item) {
    val ctx = LocalContext.current
    val app = st.appOf(item.key)
    val pkg = item.key.substringBefore('/')
    AlertDialog(
        onDismissRequest = { st.removeTarget = null },
        title = { Text(app?.let { st.label(it) } ?: "") },
        text = {
            Column {
                Text(
                    tr("Uninstall"), color = Color(0xFFFF3B30), fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        st.removeTarget = null
                        uninstallApp(ctx, pkg)
                    }.padding(vertical = 12.dp)
                )
                Text(
                    tr("Remove from Home Screen"), fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        st.removeTarget = null
                        st.remove(item)
                    }.padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { st.removeTarget = null }) { Text(tr("Cancel")) } }
    )
}

@Composable
fun WallpaperDialog(st: LauncherState) {
    AlertDialog(
        onDismissRequest = { st.wallpaperDialog = false },
        title = { Text(tr("Edit Wallpaper")) },
        text = {
            Column {
                Text(
                    tr("Choose photo"), fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        st.wallpaperDialog = false
                        st.pickWallpaper()
                    }.padding(vertical = 12.dp)
                )
                Text(
                    tr("Use system wallpaper"), fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        st.wallpaperDialog = false
                        st.clearWallpaper()
                    }.padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { st.wallpaperDialog = false }) { Text(tr("Cancel")) } }
    )
}
