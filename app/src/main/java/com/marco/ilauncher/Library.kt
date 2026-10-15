@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.marco.ilauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppRow(st: LauncherState, app: AppInfo, textColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(st.icon(app), null, Modifier.size(44.dp))
        Spacer(Modifier.width(14.dp))
        Text(st.label(app), color = textColor, fontSize = 17.sp)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, bg: Color, fg: Color, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val view = androidx.compose.ui.platform.LocalView.current
    var focused by remember { mutableStateOf(false) }
    if (autoFocus) androidx.compose.runtime.LaunchedEffect(Unit) {
        var tries = 0
        while (!focused && tries < 12) {
            kotlinx.coroutines.delay(120)
            try {
                focus.requestFocus()
            } catch (e: Exception) {
            }
            tries++
        }
        kotlinx.coroutines.delay(100)
        kb?.show()
        try {
            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = fg.copy(alpha = 0.55f), fontSize = 16.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = 16.sp), cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth().then(
                if (autoFocus) Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused } else Modifier
            )
        )
    }
}

@Composable
fun LibraryPage(st: LauncherState, pal: Pal, dark: Boolean) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val apps = st.visibleApps
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val card = Color(if (dark) 0x44000000 else 0x66FFFFFF)

    Column(Modifier.fillMaxSize().padding(top = top + 8.dp)) {
        SearchField(
            query, { query = it }, tr("App Library"), card, Color.White,
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        val q = query.trim().lowercase()
        if (q.isNotEmpty() || open != null) {
            val list = if (q.isNotEmpty()) apps.filter { st.label(it).lowercase().contains(q) }
            else if (open == "Recently Added") apps.sortedByDescending { it.installTime }.take(30)
            else apps.filter { it.category == open }
            if (open != null && q.isEmpty()) {
                Text(
                    "‹ " + tr(open ?: ""), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { open = null }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize().background(card.copy(alpha = 0.25f))) {
                items(list) { a -> AppRow(st, a, Color.White) { launchApp(ctx, a) } }
            }
        } else {
            val groups = ArrayList<Pair<String, List<AppInfo>>>()
            groups.add("Recently Added" to apps.sortedByDescending { it.installTime }.take(8))
            CATEGORIES.forEach { c ->
                val l = apps.filter { it.category == c }
                if (l.isNotEmpty()) groups.add(c to l)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                groups.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        row.forEach { (name, list) ->
                            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(card).padding(10.dp)
                                ) {
                                    list.take(4).chunked(2).forEach { r ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                                            r.forEach { a ->
                                                Image(
                                                    st.icon(a), null,
                                                    Modifier.size(52.dp).clickable { launchApp(ctx, a) }
                                                )
                                            }
                                            if (r.size == 1) Spacer(Modifier.size(52.dp))
                                        }
                                    }
                                }
                                Text(
                                    tr(name), color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp, start = 6.dp).clickable { open = name }
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
