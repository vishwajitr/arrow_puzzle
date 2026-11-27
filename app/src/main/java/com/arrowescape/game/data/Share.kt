package com.arrowescape.game.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import java.io.File

const val STORE_URL = "https://play.google.com/store/apps/details?id=com.vishwajit.arrowescape"

enum class ShareTarget(val label: String, val pkg: String?) {
    X("X", "com.twitter.android"), FACEBOOK("Facebook", "com.facebook.katana"), INSTAGRAM("Instagram", "com.instagram.android"), MORE("More", null)
}

fun iqTitle(iq: Int) = when { iq < 110 -> "Sharp Thinker"; iq < 125 -> "Clever Solver"; iq < 140 -> "Puzzle Master"; else -> "Arrow Genius" }

/** Shares a square Puzzle IQ card (image + text + store link). App not installed -> the system share sheet. */
fun shareIq(activity: Activity, iq: Int, cleared: Int, target: ShareTarget) {
    val file = File(activity.cacheDir, "share").apply { mkdirs() }.resolve("puzzle-iq.png")
    file.outputStream().use { iqCard(iq, cleared).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "My Puzzle IQ is $iq after $cleared levels of Arrow Escape. Can you beat it? $STORE_URL")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        activity.startActivity(Intent(send).setPackage(target.pkg ?: throw ActivityNotFoundException()))
    } catch (_: ActivityNotFoundException) {
        activity.startActivity(Intent.createChooser(send, "Share your Puzzle IQ"))
    }
}

private fun iqCard(iq: Int, cleared: Int): Bitmap {
    val size = 1080f
    val bmp = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, size, size, 0xFF2F5BEA.toInt(), 0xFF7A3FE0.toInt(), Shader.TileMode.CLAMP) })
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    fun text(t: String, y: Float, sz: Float, bold: Boolean = false, alpha: Int = 255, spacing: Float = 0f) {
        p.textSize = sz; p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; p.alpha = alpha; p.letterSpacing = spacing
        c.drawText(t, size / 2, y, p)
    }
    text("ARROW ESCAPE", 160f, 52f, bold = true, alpha = 210, spacing = 0.3f)
    text("My Puzzle IQ", 380f, 76f, alpha = 235)
    text("$iq", 640f, 280f, bold = true)
    p.color = 0xFFF2B01E.toInt()
    text("★  ${iqTitle(iq)}  ★", 770f, 66f, bold = true)
    p.color = Color.WHITE
    text("$cleared levels cleared", 880f, 50f, alpha = 220)
    text("Can you beat it?", 970f, 50f, alpha = 220)
    return bmp
}

/** Google Play in-app review, at most once per install. Play may still show nothing (its own quota); that's fine. */
suspend fun askForReviewOnce(activity: Activity, prefs: Prefs) {
    if (!prefs.markReviewAsked()) return
    val manager = ReviewManagerFactory.create(activity)
    runCatching { manager.launchReview(activity, manager.requestReview()) }
}
