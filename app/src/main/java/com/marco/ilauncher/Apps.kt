package com.marco.ilauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

data class AppInfo(
    val label: String,
    val pkg: String,
    val cls: String,
    val drawable: Drawable,
    val category: String,
    val installTime: Long
) {
    val key: String get() = "$pkg/$cls"
}

val CATEGORIES = listOf("Social", "Entertainment", "Creativity", "Productivity", "Games", "Utilities", "Other")

private val SOCIAL_HINTS = listOf(
    "facebook", "instagram", "whatsapp", "telegram", "tiktok", "twitter", "snapchat", "discord",
    "messenger", "linkedin", "reddit", "signal", "pinterest", "threads"
)
private val ENT_HINTS = listOf("youtube", "spotify", "netflix", "music", "video", "twitch", "primevideo", "disney", "radio", "podcast", "tv")
private val CREATIVE_HINTS = listOf("camera", "photo", "gallery", "gcam", "lightroom", "capcut", "canva", "snapseed", "editor")
private val PROD_HINTS = listOf("docs", "sheets", "drive", "mail", "gmail", "office", "word", "excel", "calendar", "keep", "notes", "pdf", "acrobat", "bank", "pay", "wallet")

fun categoryOf(info: ApplicationInfo, pkg: String): String {
    val p = pkg.lowercase()
    if (SOCIAL_HINTS.any { p.contains(it) }) return "Social"
    return when (info.category) {
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> "Creativity"
        ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> "Productivity"
        ApplicationInfo.CATEGORY_MAPS -> "Utilities"
        else -> when {
            ENT_HINTS.any { p.contains(it) } -> "Entertainment"
            CREATIVE_HINTS.any { p.contains(it) } -> "Creativity"
            PROD_HINTS.any { p.contains(it) } -> "Productivity"
            p.contains("settings") || p.contains("calc") || p.contains("clock") || p.contains("files") ||
                p.contains("chrome") || p.contains("browser") || p.contains("dialer") || p.contains("contacts") -> "Utilities"
            else -> "Other"
        }
    }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    return try {
        la.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != ctx.packageName }
            .map { ai ->
                AppInfo(
                    ai.label.toString(),
                    ai.componentName.packageName,
                    ai.componentName.className,
                    ai.getIcon(0),
                    categoryOf(ai.applicationInfo, ai.componentName.packageName),
                    ai.firstInstallTime
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }
}

fun launchApp(ctx: Context, app: AppInfo) {
    try {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.pkg, app.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        ctx.startActivity(i)
    } catch (e: Exception) {
    }
}

fun openAppInfo(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

fun uninstallApp(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

// ---------- Icone in stile iOS ----------

fun shapePath(shape: String, s: Float): Path {
    val p = Path()
    when (shape) {
        "circle" -> p.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
        "rounded" -> p.addRoundRect(RectF(0f, 0f, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW)
        else -> {
            val r = s / 2.0
            val n = 5.0
            val steps = 140
            for (i in 0..steps) {
                val t = 2.0 * PI * i / steps
                val ct = cos(t)
                val st = sin(t)
                val x = (r + r * sign(ct) * abs(ct).pow(2.0 / n)).toFloat()
                val y = (r + r * sign(st) * abs(st).pow(2.0 / n)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
    }
    return p
}

/** Disegna l'icona dell'app dentro la forma scelta, come fa iOS. */
fun renderIcon(d: Drawable, shape: String, px: Int = 192): ImageBitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.save()
    c.clipPath(shapePath(shape, px.toFloat()))
    try {
        if (d is AdaptiveIconDrawable) {
            val q = (px * 0.25f).toInt()
            d.background?.let {
                it.setBounds(-q, -q, px + q, px + q)
                it.draw(c)
            }
            d.foreground?.let {
                it.setBounds(-q, -q, px + q, px + q)
                it.draw(c)
            }
        } else {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.color = 0xFFF2F2F7.toInt()
            c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), bg)
            val inset = (px * 0.14f).toInt()
            d.setBounds(inset, inset, px - inset, px - inset)
            d.draw(c)
        }
    } catch (e: Exception) {
    }
    c.restore()
    return bmp.asImageBitmap()
}

/** Icona dell'app "Launcher" interna (porta alle impostazioni). */
fun renderSysIcon(shape: String, px: Int = 192): ImageBitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.save()
    c.clipPath(shapePath(shape, px.toFloat()))
    val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    bg.shader = android.graphics.LinearGradient(
        0f, 0f, px.toFloat(), px.toFloat(), 0xFFFF5A5F.toInt(), 0xFF5B3CC4.toInt(), android.graphics.Shader.TileMode.CLAMP
    )
    c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), bg)
    val w = Paint(Paint.ANTI_ALIAS_FLAG)
    w.color = 0xFFFFFFFF.toInt()
    val g = px * 0.12f
    val s = (px - 3 * g) / 2f
    for (i in 0..1) for (j in 0..1) {
        w.alpha = if (i == j) 255 else 170
        val x = g + i * (s + g)
        val y = g + j * (s + g)
        c.drawRoundRect(RectF(x, y, x + s, y + s), s * 0.28f, s * 0.28f, w)
    }
    c.restore()
    return bmp.asImageBitmap()
}

/** Apre il pannello delle notifiche di sistema (API nascosta: su alcuni telefoni potrebbe non fare nulla). */
fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (e: Exception) {
    }
}
