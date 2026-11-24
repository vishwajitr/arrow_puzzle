package com.arrowescape.game.game

/** [depth] = number of rounds needed when every free arrow is removed each round = longest dependency chain. */
data class Solution(val order: List<Int>, val depth: Int)

object Solver {
    /**
     * Greedy: removing an arrow only frees cells, so it never hurts to remove any free arrow.
     * Hence the level is solvable iff greedy clears it. Returns null when it gets stuck.
     */
    fun solve(level: Level, alive: BooleanArray? = null): Solution? {
        val board = Board(level)
        alive?.forEachIndexed { i, a -> if (!a) board.remove(i) }
        val order = ArrayList<Int>(board.remaining)
        var depth = 0
        while (board.remaining > 0) {
            val free = board.freeArrows()
            if (free.isEmpty()) return null
            free.forEach(board::remove)
            order += free
            depth++
        }
        return Solution(order, depth)
    }
}
