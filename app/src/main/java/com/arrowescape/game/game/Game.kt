package com.arrowescape.game.game

sealed interface TapResult {
    data class Exited(val arrow: Int) : TapResult
    data class Blocked(val arrow: Int, val distance: Int) : TapResult
    data object Ignored : TapResult
}

/** Pure rules of one play-through. No Android, no coroutines: the ViewModel wraps it. */
class Game(val level: Level) {
    companion object {
        const val MAX_HEARTS = 3
    }

    val board = Board(level)
    var hearts = MAX_HEARTS
        private set
    var mistakes = 0
        private set

    val won get() = board.remaining == 0
    val failed get() = hearts == 0 && !won
    val stars get() = when (mistakes) { 0 -> 3; 1 -> 2; else -> 1 }

    fun tap(i: Int): TapResult {
        if (won || failed || i !in level.arrows.indices || !board.alive[i]) return TapResult.Ignored
        val d = board.blockDistance(i)
        if (d == 0) {
            board.remove(i)
            return TapResult.Exited(i)
        }
        hearts--
        mistakes++
        return TapResult.Blocked(i, d)
    }

    /** Continue after failing: one heart back. Mistakes still count toward stars. */
    fun revive() {
        if (failed) hearts = 1
    }

    /** Any currently free arrow is a correct move (removal never hurts), so the hint is the solver's first pick. */
    fun hint(): Int? = Solver.solve(level, board.alive)?.order?.firstOrNull()
}
