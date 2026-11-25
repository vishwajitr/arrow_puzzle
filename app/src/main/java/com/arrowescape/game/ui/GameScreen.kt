package com.arrowescape.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrowescape.game.data.AdBanner
import com.arrowescape.game.data.Ads
import com.arrowescape.game.data.Settings
import com.arrowescape.game.game.BoardEvent
import com.arrowescape.game.game.GameViewModel
import com.arrowescape.game.game.Levels
import com.arrowescape.game.game.Status
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun GameScreen(vm: GameViewModel, ads: Ads, settings: Settings, onHome: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val rewardedReady by ads.rewardedReady.collectAsStateWithLifecycle()
    val watchForHint = { ads.showRewarded(vm::rewardHint) }
    val haptics = LocalHapticFeedback.current
    val look = LocalLook.current

    LaunchedEffect(settings.haptics) {
        if (settings.haptics) vm.events.collect {
            haptics.performHapticFeedback(if (it is BoardEvent.Exit) HapticFeedbackType.ContextClick else HapticFeedbackType.Reject)
        }
    }
    LaunchedEffect(ui.status, settings.haptics) {
        if (ui.status == Status.WON && settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    // Stall detection: no correct move for STALL_MS (wrong taps don't count) -> pulse the hint, the best moment to offer it.
    var stuck by remember { mutableStateOf(false) }
    LaunchedEffect(ui.level, ui.alive.count { it }, ui.hint, ui.status, ui.paused) {
        stuck = false
        if (ui.status == Status.PLAYING && ui.hint == null && !ui.paused) { delay(STALL_MS); stuck = true }
    }
    val pulse = if (stuck) rememberInfiniteTransition(label = "stuck")
        .animateFloat(1f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse").value else 1f

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // HUD
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon("Ⅱ", "Pause") { vm.setPaused(true) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (look.playful) Text("Level ${ui.levelNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    else Text("LEVEL ${ui.levelNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, letterSpacing = 3.sp)
                    Row(Modifier.semantics { contentDescription = "${ui.hearts} hearts" }) {
                        // Playful: the colour emoji heart. Classic: U+FE0E forces the plain text glyph.
                        val heart = if (look.playful) "\u2665" else "\u2665\uFE0E"
                        repeat(3) { Text(heart, Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {}, color = if (it < ui.hearts) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), fontSize = if (look.playful) 22.sp else 16.sp) }
                    }
                }
                Spacer(Modifier.weight(1f))
                RoundIcon("↻", "Restart") { vm.restart() }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                ui.level?.let { BoardView(it, ui.alive, ui.hint, vm.events, vm::tap, Modifier.fillMaxSize()) }
            }
            SecondaryButton(vm::hint, Modifier.height(if (look.playful) 52.dp else 48.dp).scale(if (ui.tokens > 0) pulse else 1f), enabled = ui.tokens > 0 && ui.hint == null && ui.status == Status.PLAYING) {
                if (look.playful) Text("Hint  ·  ${ui.tokens}", style = MaterialTheme.typography.titleMedium)
                else Text("Hint   ${ui.tokens}", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
            }
            if (ui.tokens == 0 && rewardedReady && ui.status == Status.PLAYING && ui.hint == null) TextButton(watchForHint, Modifier.scale(pulse)) {
                Text(if (stuck) "Stuck? Watch an ad for a hint" else "Watch an ad for a hint", letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(16.dp)) // AdMob: keep banners clear of tappable controls
            AdBanner(ads)
        }

        if (ui.status == Status.WON) Confetti(look)
    }

    when {
        ui.status == Status.WON -> WinDialog(
            stars = ui.stars,
            hasNext = ui.levelNumber < Levels.TOTAL,
            onNext = {
                // Capture the level now: reading ui.levelNumber inside the callback after the ad closes picks up any
                // advance a second Next tap already made, which skipped a level.
                val n = ui.levelNumber
                ads.maybeShowInterstitial(n) { vm.load(n + 1) }
            },
            onReplay = vm::restart,
        )
        ui.status == Status.FAILED -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Out of hearts") },
            text = { Text(if (ui.tokens > 0) "Retry the level, or spend a hint token to keep going with one heart." else "Retry the level, or watch a short video to keep going with one heart.") },
            confirmButton = { Button(onClick = vm::restart) { Text("Retry") } },
            dismissButton = {
                if (ui.tokens > 0) TextButton(onClick = vm::revive) { Text("Continue (1 hint)") }
                else if (rewardedReady) TextButton(onClick = { ads.showRewarded(vm::reviveFree) }) { Text("Watch ad: +1 heart") }
            },
        )
        ui.paused -> AlertDialog(
            onDismissRequest = { vm.setPaused(false) },
            title = { Text("Paused") },
            confirmButton = { Button(onClick = { vm.setPaused(false) }) { Text("Resume") } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::restart) { Text("Restart") }
                    TextButton(onClick = onHome) { Text("Home") }
                }
            },
        )
    }
}

private const val STALL_MS = 20_000L

@Composable
private fun RoundIcon(symbol: String, label: String, onClick: () -> Unit) {
    val playful = LocalLook.current.playful
    SecondaryButton(onClick, Modifier.size(if (playful) 52.dp else 48.dp).semantics { contentDescription = label }, contentPadding = PaddingValues(0.dp)) {
        Text(symbol, fontSize = if (playful) 22.sp else 18.sp, fontWeight = if (playful) FontWeight.Bold else null, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun WinDialog(stars: Int, hasNext: Boolean, onNext: () -> Unit, onReplay: () -> Unit) = AlertDialog(
    onDismissRequest = {},
    title = { Text(if (LocalLook.current.playful) "Cleared!" else "Level cleared", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium) },
    text = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                val s = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    delay(250L + i * 220L)
                    s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
                Text(
                    "★", fontSize = if (LocalLook.current.playful) 52.sp else 40.sp, modifier = Modifier.padding(horizontal = 4.dp).scale(s.value),
                    color = if (i < stars) LocalLook.current.gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                )
            }
        }
    },
    confirmButton = { if (hasNext) Button(onClick = onNext) { Text("Next") } },
    dismissButton = { TextButton(onClick = onReplay) { Text("Replay") } },
)

/** Closed-form particles: position is a function of time only, so the only state is the clock. */
@Composable
private fun Confetti(look: Look) {
    val parts = remember {
        val r = Random(System.nanoTime())
        List(if (look.playful) 120 else 70) { i ->
            floatArrayOf(r.nextFloat(), -0.05f - r.nextFloat() * 0.1f, (r.nextFloat() - 0.5f) * 0.5f, 0.25f + r.nextFloat() * 0.5f, r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 720f, i.toFloat())
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < 3.5f) withFrameNanos { time = (it - start) / 1e9f }
    }
    if (time >= 3.5f) return
    Canvas(Modifier.fillMaxSize()) {
        parts.forEach { (x0, y0, vx, vy, rot0, spin, i) ->
            val x = (x0 + vx * time) * size.width
            val y = (y0 + vy * time + 0.25f * time * time) * size.height
            rotate(rot0 + spin * time, Offset(x, y)) {
                val (w, h) = if (look.playful) 12f to 6f else 14f to 3f
                drawRect(look.confetti[i.toInt() % look.confetti.size].copy(alpha = (1f - time / 3.5f).coerceIn(0f, 1f)), Offset(x - w / 2, y - h / 2), Size(w, h))
            }
        }
    }
}

private operator fun FloatArray.component6() = this[5]
private operator fun FloatArray.component7() = this[6]
