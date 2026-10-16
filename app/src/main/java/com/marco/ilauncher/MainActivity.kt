package com.marco.ilauncher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var st: LauncherState

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            st.reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        st = LauncherState(applicationContext, getSharedPreferences("launcher", Context.MODE_PRIVATE))
        st.widgets = WidgetController(this)
        st.pickWallpaper = {
            try {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 5150
                )
            } catch (e: Exception) {
                try {
                    startActivityForResult(
                        Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), null), 5150
                    )
                } catch (e2: Exception) {
                }
            }
        }
        st.requestLocation = {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 77)
        }
        st.reload()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, f)
        setContent { LauncherRoot(st) }
        orient = object : android.view.OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (st.prefs.orientation == "rotate") st.onDeviceAngle(o)
            }
        }
    }

    private var orient: android.view.OrientationEventListener? = null

    override fun onStart() {
        super.onStart()
        orient?.enable()
        st.widgets.start()
    }

    override fun onStop() {
        super.onStop()
        orient?.disable()
        st.widgets.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pkgReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            st.editing = false
            st.showPicker = false
            st.showSearch = false
            st.settingsStack = emptyList()
            st.homeTick++
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        st.widgets.handleResult(requestCode, resultCode)
        if (requestCode == 5150 && resultCode == RESULT_OK) data?.data?.let { st.setWallpaperFromUri(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) st.locationTick++
    }
}

@Composable
fun LauncherRoot(st: LauncherState) {
    val p = st.prefs
    val sysDark = isSystemInDarkTheme()
    val dark = p.isDark(sysDark)
    val pal = remember(dark) { Pal(dark) }
    val lang = p.lang
    Lang.it = if (lang == "auto") Locale.getDefault().language == "it" else lang == "it"
    val view = LocalView.current
    val act = view.context as? android.app.Activity
    LaunchedEffect(p.orientation) {
        act?.requestedOrientation = when (p.orientation) {
            "portrait", "rotate" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val settingsOpen = st.settingsStack.isNotEmpty()

    LaunchedEffect(p.hideNav) {
        val c = WindowInsetsControllerCompat(
            (view.context as android.app.Activity).window, view
        )
        if (p.hideNav) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            c.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
    LaunchedEffect(dark, settingsOpen) {
        val c = WindowInsetsControllerCompat((view.context as android.app.Activity).window, view)
        c.isAppearanceLightStatusBars = !dark && settingsOpen
        c.isAppearanceLightNavigationBars = !dark && settingsOpen
    }

    BackHandler(enabled = settingsOpen || st.showPicker || st.showSearch || st.editing || st.resizeItem != null || st.openFolder != null || st.showCreateFolder) {
        when {
            st.showCreateFolder -> st.showCreateFolder = false
            st.openFolder != null -> if (st.folderEditing) st.folderEditing = false else st.openFolder = null
            st.resizeItem != null -> st.resizeItem = null
            settingsOpen -> st.settingsStack = st.settingsStack.dropLast(1)
            st.showPicker -> st.showPicker = false
            st.showSearch -> st.showSearch = false
            st.editing -> st.editing = false
        }
    }

    key(lang) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(st, pal, dark)
            if (st.showSearch) SearchScreen(st, pal)
            if (st.showPicker) WidgetPicker(st, pal)
            if (settingsOpen) SettingsHost(st, pal)
            st.renameKey?.let { RenameDialog(st, it) }
            st.openFolder?.let { FolderOverlay(st, it) }
            if (st.showCreateFolder) CreateFolderDialog(st)
            st.resizeItem?.let { ResizeDialog(st, it) }
            st.removeTarget?.let { RemoveDialog(st, it) }
            if (st.wallpaperDialog) WallpaperDialog(st)
        }
    }
}
