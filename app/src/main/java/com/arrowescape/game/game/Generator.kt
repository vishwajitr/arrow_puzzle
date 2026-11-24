package com.arrowescape.game.game

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** One row of the difficulty curve; applies from [fromLevel] until the next row starts. */
data class DifficultyConfig(
    val fromLevel: Int,
    val rows: Int,
    val cols: Int,
    val density: Float,
    val avgLength: Float,
    val bendChance: Float,
    val targetDepth: Int,
    /** From this tier on, every second level uses a [Shape] instead of a full rectangle. */
    val shaped: Boolean = false,
)

/** Board silhouettes, tested on cell centres in normalised coordinates (x, y in -1..1, y down). */
enum class Shape(val contains: (x: Float, y: Float) -> Boolean) {
    HEART({ x, y ->
        val hx = x * 1.15f
        val hy = 1.25f - (y + 1f) / 2f * 2.33f
        val a = hx * hx + hy * hy - 1f
        a * a * a - hx * hx * hy * hy * hy <= 0f
    }),
    CIRCLE({ x, y -> x * x + y * y <= 1f }),
    DIAMOND({ x, y -> abs(x) + abs(y) <= 1.08f }),
    CROSS({ x, y -> abs(x) <= 0.4f || abs(y) <= 0.4f }),
    HOURGLASS({ x, y -> abs(x) <= abs(y) + 0.2f }),
    RING({ x, y -> (x * x + y * y).let { it in 0.16f..1f } });

    /** Cells of a rows x cols grid that fall outside this shape. */
    fun off(rows: Int, cols: Int): Set<Cell> = (0 until rows * cols).map { Cell(it / cols, it % cols) }.filterNot {
        contains((it.c + 0.5f) / cols * 2 - 1, (it.r + 0.5f) / rows * 2 - 1)
    }.toSet()

    companion object {
        /** Even levels in a shaped tier get a shape, cycling through all of them; odd levels stay rectangular. */
        fun forLevel(n: Int, cfg: DifficultyConfig) = if (cfg.shaped && n % 2 == 0) entries[(n / 2) % entries.size] else null
    }
}

object Difficulty {
    // Levels 1..TUTORIAL_LEVELS come from assets; this table drives everything after them.
    val table = listOf(
        // density = share of the board's cells covered by arrows; arrow count follows from density / length.
        DifficultyConfig(1, 5, 5, 0.75f, 2.5f, 0.10f, 2),
        DifficultyConfig(11, 6, 6, 0.80f, 2.8f, 0.20f, 3),
        DifficultyConfig(21, 7, 7, 0.85f, 3.0f, 0.25f, 4),
        DifficultyConfig(36, 8, 8, 0.88f, 3.2f, 0.30f, 5),
        DifficultyConfig(41, 9, 9, 0.88f, 3.3f, 0.35f, 5, shaped = true),
        DifficultyConfig(51, 10, 9, 0.90f, 3.4f, 0.35f, 6, shaped = true),
        DifficultyConfig(71, 11, 10, 0.90f, 3.6f, 0.40f, 7, shaped = true),
        DifficultyConfig(96, 13, 10, 0.92f, 3.8f, 0.45f, 8, shaped = true),
        // From here arrows get long and snaky.
        DifficultyConfig(131, 14, 11, 0.92f, 4.6f, 0.50f, 9, shaped = true),
        DifficultyConfig(171, 16, 12, 0.94f, 5.5f, 0.60f, 10, shaped = true),
        DifficultyConfig(251, 16, 12, 0.95f, 6.5f, 0.65f, 11, shaped = true),
    )

    fun forLevel(level: Int) = table.last { it.fromLevel <= level }
}

object Generator {
    private const val CANDIDATES = 3

    /** Deterministic: the same [levelNumber] always yields the same level. */
    fun generate(levelNumber: Int, config: DifficultyConfig = Difficulty.forLevel(levelNumber)): Level {
        var best: Level? = null
        var bestScore = Int.MAX_VALUE
        var attempt = 0
        // Try a few seeded candidates and keep the one closest to the target depth (ties: more arrows).
        val off = Shape.forLevel(levelNumber, config)?.off(config.rows, config.cols).orEmpty()
        val cfg = config
        while (attempt < CANDIDATES || best == null) {
            val level = build(Random(levelNumber * 1_000_003L + attempt), cfg, off)
            attempt++
            val solution = Solver.solve(level) ?: continue // can't happen by construction; regenerate if it does
            val score = abs(solution.depth - cfg.targetDepth) * 1000 - level.arrows.size
            if (score < bestScore) { best = level; bestScore = score }
        }
        return best
    }

    /**
     * Places arrows one at a time; each new arrow's exit ray only needs to be clear of arrows placed *before* it.
     * Removing arrows in reverse placement order then always works, so every level is solvable by construction.
     * Dependencies appear when a later arrow's body lands in an earlier arrow's ray. To reach the target depth, each
     * step samples a few head cells and keeps the placement that extends the longest blocking chain (capped at target).
     */
    private fun build(rnd: Random, cfg: DifficultyConfig, off: Set<Cell>): Level {
        val rows = cfg.rows
        val cols = cfg.cols
        val grid = BooleanArray(rows * cols)
        fun inBounds(c: Cell) = c.r in 0 until rows && c.c in 0 until cols
        fun empty(c: Cell) = !grid[c.r * cols + c.c]
        fun free(c: Cell) = inBounds(c) && c !in off && empty(c) // can hold an arrow cell
        fun ray(h: Cell, d: Dir) = generateSequence(h + d) { it + d }.takeWhile(::inBounds).toList()

        val arrows = ArrayList<Arrow>()
        val chain = ArrayList<Int>() // chain[k]: length of the blocking chain that ends at arrow k
        val rayOwners = Array(rows * cols) { ArrayList<Int>() } // cell -> earlier arrows whose exit ray crosses it

        fun unseen(c: Cell) = rayOwners[c.r * cols + c.c].isEmpty() // no placed arrow's exit ray crosses it

        /**
         * [last] = false: removed before every arrow placed so far, so its ray must be clear of them (rays may cross
         * cells outside the shape). [last] = true (fill pass): removed after every arrow placed so far, so its ray may
         * cross anything, but no cell of it may sit in another arrow's ray.
         */
        fun place(h: Cell, last: Boolean = false): Arrow? {
            fun ok(c: Cell) = free(c) && (!last || unseen(c))
            if (!ok(h)) return null
            val dirs = Dir.entries.shuffled(rnd).filter { d -> last || ray(h, d).all(::empty) }
            // Prefer a direction with room for a neck directly behind the head, so the arrow reads straight.
            val d = dirs.firstOrNull { ok(h - it) } ?: dirs.firstOrNull() ?: return null
            val forbidden = ray(h, d).toHashSet() // own body must not sit in own exit ray
            val target = (cfg.avgLength + (rnd.nextFloat() * 2 - 1) * cfg.avgLength / 2).roundToInt().coerceAtLeast(1)
            val body = arrayListOf(h)
            var grow = d.opposite
            while (body.size < target) {
                val cur = body.last()
                val turns = Dir.entries.filter { it != grow && it != grow.opposite }.shuffled(rnd)
                val options = when {
                    body.size == 1 -> listOf(grow)
                    rnd.nextFloat() < cfg.bendChance -> turns + grow
                    else -> listOf(grow) + turns
                }
                val next = options.firstOrNull { val n = cur + it; ok(n) && n !in forbidden && n !in body } ?: break
                body += cur + next
                grow = next
            }
            return Arrow(body, d)
        }

        fun chainOf(a: Arrow) = 1 + (a.cells.flatMap { rayOwners[it.r * cols + it.c] }.maxOfOrNull { chain[it] } ?: 0)
        fun score(c: Int) = if (c <= cfg.targetDepth) c else 2 * cfg.targetDepth - c

        val all = (0 until rows * cols).map { Cell(it / cols, it % cols) }
        val target = ((rows * cols - off.size) * cfg.density).toInt()
        var covered = 0
        while (covered < target) {
            val empty = all.filter(::free).shuffled(rnd)
            // Bigger boards need more search to build long chains: ~1 sample per 8 cells, at least 6.
            val best = empty.asSequence().mapNotNull(::place).take(maxOf(6, rows * cols / 8)).maxByOrNull { score(chainOf(it)) } ?: break
            best.cells.forEach { grid[it.r * cols + it.c] = true }
            covered += best.cells.size
            chain += chainOf(best)
            ray(best.head, best.dir).forEach { rayOwners[it.r * cols + it.c] += arrows.size }
            arrows += best
        }
        // Fill pass: plug the gaps the first pass can't reach with arrows that leave last (see place()).
        while (covered < target) {
            val best = all.filter(::free).shuffled(rnd).asSequence().mapNotNull { place(it, last = true) }
                .take(6).maxByOrNull { it.cells.size } ?: break
            best.cells.forEach { grid[it.r * cols + it.c] = true }
            covered += best.cells.size
            ray(best.head, best.dir).forEach { rayOwners[it.r * cols + it.c] += arrows.size }
            chain += 0 // fill arrows block nobody placed before them
            arrows += best
        }
        return Level(rows, cols, arrows, off)
    }
}
