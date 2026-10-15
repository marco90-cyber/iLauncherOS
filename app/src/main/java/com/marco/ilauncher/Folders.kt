package com.marco.ilauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Cartella aperta: tocca un'app per avviarla; tieni premuto su un'icona per rinominare la cartella. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderOverlay(st: LauncherState, id: String) {
    val ctx = LocalContext.current
    val folder = st.folderById(id)
    if (folder == null) {
        st.openFolder = null
        return
    }
    val focus = remember { FocusRequester() }
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(st.folderEditing) {
        if (st.folderEditing) {
            delay(150)
            try {
                focus.requestFocus()
            } catch (e: Exception) {
            }
            try {
                val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } catch (e: Exception) {
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) {
            detectTapGestures {
                if (st.folderEditing) st.folderEditing = false
                st.openFolder = null
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp))
                .background(Color(0xE6303036)).pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (st.folderEditing) {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF)).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = folder.name, onValueChange = { st.renameFolder(id, it) }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
            } else {
                Text(folder.name.ifBlank { tr("Folder") }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            folder.apps.mapNotNull { st.appOf(it) }.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    row.forEach { a ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (!st.folderEditing) {
                                            st.openFolder = null
                                            launchApp(ctx, a)
                                        }
                                    },
                                    onLongClick = { st.folderEditing = true }
                                )
                            ) {
                                Image(st.icon(a), null, Modifier.size(58.dp))
                                Text(
                                    st.label(a), color = Color.White, fontSize = 11.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    modifier = Modifier.width(66.dp).padding(top = 3.dp)
                                )
                            }
                            if (st.folderEditing) {
                                Box(
                                    Modifier.align(Alignment.TopStart).padding(start = 2.dp).size(22.dp).clip(CircleShape)
                                        .background(Color(0xFF8E8E93))
                                        .clickable { st.removeFromFolder(id, a.key) },
                                    contentAlignment = Alignment.Center
                                ) { Box(Modifier.width(11.dp).height(2.dp).background(Color.White)) }
                            }
                        }
                    }
                    for (i in row.size until 4) Spacer(Modifier.weight(1f))
                }
            }
            if (st.folderEditing) {
                Text(
                    tr("Done"), color = Color.White, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF0A84FF))
                        .clickable { st.folderEditing = false }.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** "Crea cartella": nome + multiselezione delle app (le scelte escono dalla Home). */
@Composable
fun CreateFolderDialog(st: LauncherState) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = { st.showCreateFolder = false },
        title = { Text(tr("Create folder")) },
        text = {
            Column {
                SearchField(name, { name = it }, tr("Folder name"), Color(0x22888888), Color.Gray)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(st.visibleApps) { a ->
                        val on = a.key in selected
                        Row(
                            Modifier.fillMaxWidth().clickable { if (on) selected.remove(a.key) else selected.add(a.key) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(a.key) else selected.add(a.key) })
                            Image(st.icon(a), null, Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(st.label(a), fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                st.createFolder(name, selected.toList(), st.pickerPage)
                st.showCreateFolder = false
            }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = { st.showCreateFolder = false }) { Text(tr("Cancel")) } }
    )
}
