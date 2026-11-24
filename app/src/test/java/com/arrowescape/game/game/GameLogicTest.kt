package com.arrowescape.game.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class GameLogicTest {
    private fun arrow(dir: Dir, vararg rc: Pair<Int, Int>) = Arrow(rc.map { Cell(it.first, it.second) }, dir)

    // Row 0: A at (0,0) points RIGHT into B at (0,2), which points UP (free). A depends on B.
    private val chain = Level(3, 3, listOf(
        arrow(Dir.RIGHT, 0 to 0),
        arrow(Dir.UP, 0 to 2, 1 to 2, 2 to 2),
    ))

    private fun fill(l: Level) = l.arrows.sumOf { it.cells.size }.toDouble() / (l.rows * l.cols - l.off.size)

    @Test fun blockDistanceCountsStepsToFirstObstacle() {
        val b = Board(chain)
        assertThat(b.blockDistance(0)).isEqualTo(2)
        assertThat(b.blockDistance(1)).isEqualTo(0)
    }

    @Test fun solverReturnsOrderAndDepth() {
        val s = Solver.solve(chain)!!
        assertThat(s.order).containsExactly(1, 0).inOrder()
        assertThat(s.depth).isEqualTo(2)
    }

    @Test fun headToHeadIsUnsolvable() {
        val deadlock = Level(1, 2, listOf(arrow(Dir.RIGHT, 0 to 0), arrow(Dir.LEFT, 0 to 1)))
        assertThat(Solver.solve(deadlock)).isNull()
    }

    @Test fun ownBodyInRayBlocks() {
        // U-shape: head (1,0) points UP, body curls over to (0,0) which is in its own ray.
        val curl = Level(2, 2, listOf(arrow(Dir.UP, 1 to 0, 1 to 1, 0 to 1, 0 to 0)))
        assertThat(Solver.solve(curl)).isNull()
    }

    @Test fun overlappingArrowsAreRejected() {
        val bad = Level(2, 2, listOf(arrow(Dir.UP, 0 to 0), arrow(Dir.UP, 0 to 0)))
        runCatching { Board(bad) }.let { assertThat(it.isFailure).isTrue() }
    }

    @Test fun blockedTapCostsHeartAndStars() {
        val g = Game(chain)
        assertThat(g.tap(0)).isEqualTo(TapResult.Blocked(0, 2))
        assertThat(g.hearts).isEqualTo(2)
        assertThat(g.tap(1)).isEqualTo(TapResult.Exited(1))
        assertThat(g.tap(0)).isEqualTo(TapResult.Exited(0))
        assertThat(g.won).isTrue()
        assertThat(g.stars).isEqualTo(2)
        assertThat(g.tap(0)).isEqualTo(TapResult.Ignored)
    }

    @Test fun threeMistakesFailAndReviveGivesOneHeart() {
        val g = Game(chain)
        repeat(3) { g.tap(0) }
        assertThat(g.failed).isTrue()
        assertThat(g.tap(1)).isEqualTo(TapResult.Ignored)
        g.revive()
        assertThat(g.hearts).isEqualTo(1)
        assertThat(g.tap(1)).isInstanceOf(TapResult.Exited::class.java)
        g.tap(0)
        assertThat(g.stars).isEqualTo(1)
    }

    @Test fun hintIsAlwaysAFreeArrow() {
        val g = Game(Generator.generate(150))
        while (!g.won) {
            val h = g.hint()!!
            assertThat(g.board.isFree(h)).isTrue()
            g.tap(h)
        }
        assertThat(g.mistakes).isEqualTo(0)
    }

    @Test fun generatorIsDeterministic() {
        assertThat(Generator.generate(37)).isEqualTo(Generator.generate(37))
        assertThat(Generator.generate(37)).isNotEqualTo(Generator.generate(38))
    }

    @Test fun generatedLevelsAreSolvableAndFollowTheCurve() {
        var minFill = 1.0
        for (n in Levels.TUTORIAL_LEVELS + 1..Levels.TOTAL) {
            val level = Generator.generate(n)
            val cfg = Difficulty.forLevel(n)
            assertThat(level.rows to level.cols).isEqualTo(cfg.rows to cfg.cols)
            assertThat(level.off).isEqualTo(Shape.forLevel(n, cfg)?.off(cfg.rows, cfg.cols).orEmpty())
            // Fills toward the target density; it may stop a little short when no arrow fits anywhere.
            minFill = minOf(minFill, fill(level))
            assertThat(level.arrows.flatMap { it.cells }.none { it in level.off }).isTrue() // Board() also enforces this
            assertThat(Solver.solve(level)).isNotNull()
        }
        println("min fill ${"%.2f".format(minFill)}")
        assertThat(minFill).isAtLeast(0.75)
        Difficulty.table.forEach { cfg ->
            val ls = (0 until 20).map { Generator.generate(maxOf(cfg.fromLevel, Levels.TUTORIAL_LEVELS + 1) + it) }
            println("from ${cfg.fromLevel}: target depth ${cfg.targetDepth}, avg depth ${ls.map { Solver.solve(it)!!.depth }.average()}, avg arrows ${ls.map { it.arrows.size }.average()}, avg length ${"%.1f".format(ls.flatMap { it.arrows }.map { it.cells.size }.average())}, fill ${"%.0f".format(ls.map { fill(it) }.average() * 100)}% of ${"%.0f".format(cfg.density * 100)}%")
        }
    }

    @Test fun everyShapeAppearsAndKeepsMostOfTheBoard() {
        val seen = (Levels.TUTORIAL_LEVELS + 1..Levels.TOTAL).mapNotNull { Shape.forLevel(it, Difficulty.forLevel(it)) }.toSet()
        assertThat(seen).containsExactlyElementsIn(Shape.entries)
        Shape.entries.forEach { s ->
            val off = s.off(16, 12)
            println("$s:\n" + (0 until 16).joinToString("\n") { r -> (0 until 12).joinToString("") { c -> if (Cell(r, c) in off) "." else "#" } })
            assertThat(off.size).isLessThan(16 * 12 / 2)
        }
    }

    @Test fun hardestLevelReaches12x16() {
        val l = Generator.generate(Levels.TOTAL)
        assertThat(l.cols to l.rows).isEqualTo(12 to 16)
    }

    @Test fun tutorialLevelsParseAndSolve() {
        for (n in 1..Levels.TUTORIAL_LEVELS) {
            val level = Levels.parse(File("src/main/assets/levels/$n.json").readText())
            assertThat(Solver.solve(level)).isNotNull()
        }
    }

    /** Budget is 50 ms on a mid-range phone; a desktop JVM is ~3-5x faster, so hold it to 15 ms. */
    @Test fun generationBenchmark() {
        repeat(50) { Generator.generate(200 + it) } // warm-up JIT
        val levels = (Levels.TOTAL - 100..Levels.TOTAL) // the hardest, largest boards
        var worst = 0L
        val total = levels.sumOf { n ->
            val t = System.nanoTime(); Generator.generate(n); (System.nanoTime() - t).also { worst = maxOf(worst, it) }
        }
        val avgMs = total / levels.count() / 1e6
        println("generation: avg %.2f ms, worst %.2f ms".format(avgMs, worst / 1e6))
        assertThat(avgMs).isLessThan(15.0)
    }
}
