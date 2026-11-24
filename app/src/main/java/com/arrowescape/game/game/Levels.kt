package com.arrowescape.game.game

import org.json.JSONObject

object Levels {
    const val TUTORIAL_LEVELS = 10
    const val TOTAL = 500 // ponytail: generator is unbounded; raise this to ship more levels

    /** Tutorial levels come from assets/levels/N.json (read via [readAsset]); the rest are generated from the seed. */
    fun load(n: Int, readAsset: (String) -> String): Level =
        if (n <= TUTORIAL_LEVELS) parse(readAsset("levels/$n.json")) else Generator.generate(n)

    /** Format: {"rows":R,"cols":C,"arrows":[{"dir":"UP","cells":[[r,c],...head first]}]} */
    fun parse(json: String): Level {
        val o = JSONObject(json)
        val arr = o.getJSONArray("arrows")
        val arrows = (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            val cells = a.getJSONArray("cells")
            Arrow(
                cells = (0 until cells.length()).map { j -> cells.getJSONArray(j).let { Cell(it.getInt(0), it.getInt(1)) } },
                dir = Dir.valueOf(a.getString("dir")),
            )
        }
        return Level(o.getInt("rows"), o.getInt("cols"), arrows)
    }
}
