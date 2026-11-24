package com.arrowescape.game.game

enum class Dir(val dr: Int, val dc: Int) {
    UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1);

    val opposite get() = entries[ordinal xor 1] // UP<->DOWN, LEFT<->RIGHT
}

data class Cell(val r: Int, val c: Int) {
    operator fun plus(d: Dir) = Cell(r + d.dr, c + d.dc)
    operator fun minus(d: Dir) = Cell(r - d.dr, c - d.dc)
}

/** [cells] are ordered head first; the head points in [dir]. */
data class Arrow(val cells: List<Cell>, val dir: Dir) {
    val head get() = cells.first()
}

/** [off]: cells outside the board's shape. Arrows never sit there, but exit rays pass through them freely. */
data class Level(val rows: Int, val cols: Int, val arrows: List<Arrow>, val off: Set<Cell> = emptySet()) {
    fun isOn(c: Cell) = c.r in 0 until rows && c.c in 0 until cols && c !in off
}

/**
 * Mutable occupancy grid for a [Level]. Shared by the solver and the live game.
 * An arrow's exit ray (head to board edge) must be free of every cell, its own body included.
 */
class Board(val level: Level) {
    val rows = level.rows
    val cols = level.cols
    private val grid = IntArray(rows * cols) { -1 }
    val alive = BooleanArray(level.arrows.size) { true }
    var remaining = level.arrows.size
        private set

    init {
        level.arrows.forEachIndexed { i, a ->
            a.cells.forEach { cell ->
                require(level.isOn(cell) && grid[idx(cell)] == -1) { "arrow $i overlaps or is off-board at $cell" }
                grid[idx(cell)] = i
            }
        }
    }

    fun inBounds(cell: Cell) = cell.r in 0 until rows && cell.c in 0 until cols
    private fun idx(cell: Cell) = cell.r * cols + cell.c

    /** Arrow index at [cell], or -1. */
    fun arrowAt(cell: Cell) = if (inBounds(cell)) grid[idx(cell)] else -1

    /** 0 if arrow [i] can exit, else the number of steps to the first blocking cell (1 = adjacent). */
    fun blockDistance(i: Int): Int {
        val a = level.arrows[i]
        var cell = a.head + a.dir
        var k = 1
        while (inBounds(cell)) {
            if (grid[idx(cell)] != -1) return k
            cell += a.dir; k++
        }
        return 0
    }

    fun isFree(i: Int) = alive[i] && blockDistance(i) == 0

    fun freeArrows() = level.arrows.indices.filter(::isFree)

    fun remove(i: Int) {
        check(alive[i])
        level.arrows[i].cells.forEach { grid[idx(it)] = -1 }
        alive[i] = false
        remaining--
    }
}
