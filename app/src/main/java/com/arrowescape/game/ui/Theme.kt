package com.arrowescape.game.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arrowescape.game.data.Style
import com.arrowescape.game.data.ThemeMode

/** Everything that differs between the two styles. Board sizes are fractions of a cell. */
data class Look(
    val playful: Boolean,
    val arrows: List<Color>,
    /** Hint glow colour; null = the arrow's own colour. Needed when every arrow is the same ink. */
    val hint: Color?,
    val confetti: List<Color>,
    val gold: Color,
    val stroke: Float,
    val headTip: Float,
    val headHalf: Float,
    val headOutline: Float,
    val shadowAlpha: Float,
    val shadowDx: Float,
    val shadowDy: Float,
    val dot: Float,
    val dotAlpha: Float,
    val boardCorner: Float,
    val hintAlpha: Float,
    val hintWidth: Float,
    val corner: Dp,
) {
    fun arrow(i: Int) = arrows[i % arrows.size]
}

private fun palette(vararg c: Long) = c.map(::Color)

// Classic arrows are single-ink (black, or near-white in dark mode); these muted hues are only for confetti.
private val classicLight = palette(0xFF3F5F8F, 0xFFB5543C, 0xFF4E7F6D, 0xFFB08A2E, 0xFF6E5288, 0xFF2F7A7A, 0xFFA2566B, 0xFF76783F, 0xFF3A437A, 0xFF8A6A48)
private val classicDark = palette(0xFF8AA6D1, 0xFFDB8B72, 0xFF8EBCA9, 0xFFD9B865, 0xFFAD94C6, 0xFF79BDBA, 0xFFD495A6, 0xFFB5B67E, 0xFF8E97C9, 0xFFC4A585)
// Playful: saturated, bright hues.
// Every colour keeps >= 3:1 contrast on the board panel (WCAG minimum for graphics); ThemeContrastTest checks it.
internal val playfulLight = palette(0xFFE5484D, 0xFF008AF5, 0xFF2D9A66, 0xFFE65C08, 0xFF8E4EC6, 0xFF109788, 0xFFD6409F, 0xFFBA7B00, 0xFF3E63DD, 0xFF0797B8)
private val playfulDark = palette(0xFFFF6369, 0xFF52A9FF, 0xFF4CC38A, 0xFFFF8B3E, 0xFFB07FE0, 0xFF2FC4B2, 0xFFF06BBD, 0xFFFFCA16, 0xFF7B93F5, 0xFFB8A383)

internal val ClassicBoard = Color(0xFFF4F6FA)
internal val PlayfulBoard = Color(0xFFF5F3FF)

fun look(style: Style, dark: Boolean) = when (style) {
    // Arrow sizes are fractions of a cell; kept slim so dense boards read as clean lines, not blocks.
    Style.CLASSIC -> Look(
        playful = false, arrows = listOf(if (dark) Color(0xFFE8EAF0) else Color(0xFF1B2133)),
        hint = if (dark) Color(0xFF6B8CFF) else Color(0xFF2F5BEA), confetti = if (dark) classicDark else classicLight, gold = Color(0xFFF2B01E),
        stroke = 0.13f, headTip = 0.26f, headHalf = 0.17f, headOutline = 0f,
        shadowAlpha = 0f, shadowDx = 0f, shadowDy = 0f, dot = 0.035f, dotAlpha = 0.16f, boardCorner = 0.18f,
        hintAlpha = 0.35f, hintWidth = 0.5f, corner = 14.dp,
    )
    Style.PLAYFUL -> Look(
        playful = true, arrows = if (dark) playfulDark else playfulLight,
        hint = null, confetti = if (dark) playfulDark else playfulLight, gold = Color(0xFFFFB224),
        stroke = 0.2f, headTip = 0.3f, headHalf = 0.22f, headOutline = 0.05f,
        shadowAlpha = 0.10f, shadowDx = 0f, shadowDy = 0.04f, dot = 0.045f, dotAlpha = 0.14f, boardCorner = 0.3f,
        hintAlpha = 0.40f, hintWidth = 0.6f, corner = 20.dp,
    )
}

val LocalLook = staticCompositionLocalOf { look(Style.CLASSIC, false) }

// Light schemes: pure white backgrounds, one confident accent for the main action, a barely-there board panel.
private val classicLightScheme = lightColorScheme(
    primary = Color(0xFF2F5BEA), onPrimary = Color.White,
    background = Color.White, surface = Color.White, surfaceVariant = ClassicBoard,
    secondaryContainer = Color(0xFFEEF2FD), onSecondaryContainer = Color(0xFF1B2133),
    onBackground = Color(0xFF1B2133), onSurface = Color(0xFF1B2133), outline = Color(0xFFDDE2EC),
    error = Color(0xFFE5484D),
)
private val classicDarkScheme = darkColorScheme(
    primary = Color(0xFFE6E3DD), onPrimary = Color(0xFF16181D),
    background = Color(0xFF16181D), surface = Color(0xFF1F2228), surfaceVariant = Color(0xFF23262D),
    secondaryContainer = Color(0xFF2A2D35), onSecondaryContainer = Color(0xFFE6E3DD),
    onBackground = Color(0xFFE6E3DD), onSurface = Color(0xFFE6E3DD), outline = Color(0xFF3A3E47),
    error = Color(0xFFE07A6E),
)
private val playfulLightScheme = lightColorScheme(
    primary = Color(0xFF6C4BF4), onPrimary = Color.White,
    background = Color.White, surface = Color.White, surfaceVariant = PlayfulBoard,
    secondaryContainer = Color(0xFFEFEBFF), onSecondaryContainer = Color(0xFF2A1F5C),
    onBackground = Color(0xFF1E2235), onSurface = Color(0xFF1E2235), outline = Color(0xFFE2DCFA),
    error = Color(0xFFFF4D5E),
)
private val playfulDarkScheme = darkColorScheme(
    primary = Color(0xFF7B93F5), onPrimary = Color(0xFF111426),
    background = Color(0xFF111426), surface = Color(0xFF1B1F33), surfaceVariant = Color(0xFF262B44),
    secondaryContainer = Color(0xFF2C3252), onSecondaryContainer = Color(0xFFE8E9F0),
    onBackground = Color(0xFFE8E9F0), onSurface = Color(0xFFE8E9F0), error = Color(0xFFFF6369),
)

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ArrowTheme(dark: Boolean, style: Style, content: @Composable () -> Unit) {
    val scheme = when (style) {
        Style.CLASSIC -> if (dark) classicDarkScheme else classicLightScheme
        Style.PLAYFUL -> if (dark) playfulDarkScheme else playfulLightScheme
    }
    CompositionLocalProvider(LocalLook provides look(style, dark)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Secondary action: outlined in Classic, filled tonal pill in Playful. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.corner)
    if (look.playful) FilledTonalButton(onClick, modifier, enabled, shape, contentPadding = contentPadding, content = content)
    else OutlinedButton(onClick, modifier, enabled, shape, contentPadding = contentPadding, content = content)
}
