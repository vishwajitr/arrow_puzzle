package com.arrowescape.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.arrowescape.game.data.Style
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Arrows are graphics: WCAG asks for at least 3:1 contrast against what they sit on. */
class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun playfulArrowsReadOnTheirBoard() = playfulLight.forEach {
        assertThat(contrast(it, PlayfulBoard)).isAtLeast(3f)
    }

    @Test fun classicInkReadsOnItsBoard() =
        assertThat(contrast(look(Style.CLASSIC, dark = false).arrow(0), ClassicBoard)).isAtLeast(4.5f)
}
