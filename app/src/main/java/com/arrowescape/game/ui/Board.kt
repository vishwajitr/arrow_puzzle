package com.arrowescape.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.arrowescape.game.game.Arrow
import com.arrowescape.game.game.BoardEvent
import com.arrowescape.game.game.Cell
import com.arrowescape.game.game.Dir
import com.arrowescape.game.game.Level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Per-arrow animation: [t] = cells travelled along the snake path, [shake] = sideways wobble 0..1, [tint] = red flash 0..1. */
private class Motion {
    val t = Animatable(0f)
    val shake = Animatable(0f)
    val tint = Animatable(0f)
}

@Composable
fun BoardView(
    level: Level,
    alive: List<Boolean>,
    hint: Int?,
    events: Flow<BoardEvent>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motions = remember(level) { mutableStateMapOf<Int, Motion>() }

    LaunchedEffect(level) {
        events.collect { e ->
            when (e) {
                is BoardEvent.Exit -> {
                    val m = motions.getOrPut(e.arrow) { Motion() }
                    launch {
                        val a = level.arrows[e.arrow]
                        val travel = a.cells.size + rayLength(level, a) + 1.2f
                        m.t.animateTo(travel, tween((220 + travel * 45).toInt(), easing = FastOutLinearInEasing))
                        motions.remove(e.arrow)
                    }
                }
                is BoardEvent.Bounce -> {
                    val m = motions.getOrPut(e.arrow) { Motion() }
                    launch {
                        // Slide up to the blocker (head just touching it), then spring back with a shake.
                        val reach = e.distance - 1 + 0.3f
                        m.t.animateTo(reach, tween((90 + reach * 45).toInt(), easing = FastOutSlowInEasing))
                        coroutineScope {
                            launch { m.tint.snapTo(1f); m.tint.animateTo(0f, tween(600)) }
                            launch { m.shake.snapTo(1f); m.shake.animateTo(0f, tween(380)) }
                            m.t.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 500f))
                        }
                        motions.remove(e.arrow) // skipped if a later Exit cancelled us: Exit owns the motion now
                    }
                }
            }
        }
    }

    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        0.25f, 0.75f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "hintPulse",
    )
    val look = LocalLook.current
    val boardColor = MaterialTheme.colorScheme.surfaceVariant
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = look.dotAlpha)
    val errorColor = MaterialTheme.colorScheme.error

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val cellPx = with(density) { min(maxWidth.toPx() / level.cols, maxHeight.toPx() / level.rows) }
        // Read through updated state: keying pointerInput on `alive` restarts the detector mid-gesture,
        // which re-delivered one touch to the next arrow under the finger (one tap cleared a whole level).
        val owner by rememberUpdatedState(remember(level, alive) { cellOwners(level, alive) })
        val tap by rememberUpdatedState(onTap)

        Canvas(
            Modifier
                .size(with(density) { (cellPx * level.cols).toDp() }, with(density) { (cellPx * level.rows).toDp() })
                .pointerInput(level, cellPx) {
                    detectTapGestures { p -> hitTest(level, owner, p, cellPx)?.let(tap) }
                },
        ) {
            if (level.off.isEmpty()) drawRoundRect(boardColor, cornerRadius = CornerRadius(cellPx * look.boardCorner))
            // Shaped board: fill only the cells inside the silhouette (slight overlap hides seams between cells).
            else for (r in 0 until level.rows) for (c in 0 until level.cols) if (Cell(r, c) !in level.off)
                drawRect(boardColor, Offset(c * cellPx - 0.5f, r * cellPx - 0.5f), Size(cellPx + 1f, cellPx + 1f))
            for (r in 0 until level.rows) for (c in 0 until level.cols)
                if (Cell(r, c) !in level.off) drawCircle(dotColor, cellPx * look.dot, center(Cell(r, c), cellPx))

            level.arrows.forEachIndexed { i, a ->
                val m = motions[i]
                if (!alive.getOrElse(i) { false } && m == null) return@forEachIndexed
                val base = look.arrow(i)
                val color = lerp(base, errorColor, (m?.tint?.value ?: 0f) * 0.8f)
                val t = m?.t?.value ?: 0f
                val wobble = (m?.shake?.value ?: 0f).let { s -> sin(s * 18f) * s * cellPx * 0.12f }
                val exiting = !alive.getOrElse(i) { false }
                // Fade as it leaves the board so it doesn't fly over the HUD.
                val alpha = if (exiting) (1f - (t / (a.cells.size + rayLength(level, a) + 1.2f))).coerceIn(0f, 1f) else 1f
                if (i == hint && !exiting) drawSnake(a, t, cellPx, look, (look.hint ?: base).copy(alpha = pulse * look.hintAlpha), widthFactor = look.hintWidth, wobble = 0f)
                if (look.shadowAlpha > 0f) drawSnake(a, t, cellPx, look, Color.Black.copy(alpha = look.shadowAlpha * alpha), offset = Offset(cellPx * look.shadowDx, cellPx * look.shadowDy), wobble = wobble)
                drawSnake(a, t, cellPx, look, color.copy(alpha = alpha), wobble = wobble)
            }
        }
    }
}

private fun center(c: Cell, cell: Float) = Offset((c.c + 0.5f) * cell, (c.r + 0.5f) * cell)

private fun rayLength(level: Level, a: Arrow): Int = when (a.dir) {
    Dir.UP -> a.head.r
    Dir.DOWN -> level.rows - 1 - a.head.r
    Dir.LEFT -> a.head.c
    Dir.RIGHT -> level.cols - 1 - a.head.c
}

/**
 * Snake movement: the arrow occupies a window of length (len-1) sliding along the polyline
 * tail..head followed by the cells ahead of the head. Body cells follow the head's exact path.
 */
private fun DrawScope.drawSnake(
    a: Arrow, t: Float, cell: Float, look: Look, color: Color,
    widthFactor: Float = look.stroke, offset: Offset = Offset.Zero, wobble: Float,
) {
    val len = a.cells.size
    val track = a.cells.reversed() + (1..len + 40).map { k -> Cell(a.head.r + a.dir.dr * k, a.head.c + a.dir.dc * k) }
    val perp = Offset(-a.dir.dc.toFloat(), a.dir.dr.toFloat()) * wobble
    fun pt(s: Float): Offset {
        val i = floor(s).toInt().coerceIn(0, track.size - 2)
        val f = s - i
        return lerpOffset(center(track[i], cell), center(track[i + 1], cell), f) + offset + perp
    }
    val dir = Offset(a.dir.dc.toFloat(), a.dir.dr.toFloat())
    val head = pt(t + len - 1)
    val path = Path().apply {
        // Single-cell arrows get a short tail so they still read as arrows.
        val tail = if (len == 1) head - dir * (cell * 0.3f) else pt(t)
        moveTo(tail.x, tail.y)
        var j = floor(t).toInt() + 1
        while (j < t + len - 1) { pt(j.toFloat()).let { lineTo(it.x, it.y) }; j++ }
        lineTo(head.x, head.y)
    }
    val w = cell * widthFactor
    drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Arrowhead: rounded triangle pointing along dir.
    val side = Offset(-dir.y, dir.x)
    val tip = head + dir * (cell * look.headTip)
    val back = head - dir * (cell * 0.02f)
    val tri = Path().apply {
        moveTo(tip.x, tip.y)
        (back + side * (cell * look.headHalf)).let { lineTo(it.x, it.y) }
        (back - side * (cell * look.headHalf)).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(tri, color)
    if (look.headOutline > 0f) drawPath(tri, color, style = Stroke(cell * look.headOutline, join = StrokeJoin.Round)) // rounds the corners
}

private fun lerpOffset(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

private fun cellOwners(level: Level, alive: List<Boolean>): Array<IntArray> {
    val g = Array(level.rows) { IntArray(level.cols) { -1 } }
    level.arrows.forEachIndexed { i, a -> if (alive.getOrElse(i) { false }) a.cells.forEach { g[it.r][it.c] = i } }
    return g
}

/** Any cell of an arrow counts. Taps on an empty cell snap to the nearest arrow cell within 0.8 cell, for fat fingers. */
private fun hitTest(level: Level, owner: Array<IntArray>, p: Offset, cell: Float): Int? {
    val r = (p.y / cell).toInt()
    val c = (p.x / cell).toInt()
    owner.getOrNull(r)?.getOrNull(c)?.takeIf { it >= 0 }?.let { return it }
    var best: Int? = null
    var bestD = cell * 0.8f
    for (dr in -1..1) for (dc in -1..1) {
        val i = owner.getOrNull(r + dr)?.getOrNull(c + dc)?.takeIf { it >= 0 } ?: continue
        val ctr = center(Cell(r + dr, c + dc), cell)
        val d = hypot(p.x - ctr.x, p.y - ctr.y)
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}
