package com.arrowescape.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arrowescape.game.data.Progress
import com.arrowescape.game.data.Settings
import com.arrowescape.game.data.Style
import com.arrowescape.game.data.ThemeMode
import com.arrowescape.game.game.Levels

@Composable
fun HomeScreen(progress: Progress, onPlay: () -> Unit, onLevels: () -> Unit, onSettings: () -> Unit) {
    val look = LocalLook.current
    val level = progress.unlocked.coerceAtMost(Levels.TOTAL)
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (look.playful) {
            Text("Arrow", fontSize = 52.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("Escape", fontSize = 52.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text("Tap the arrows out, in the right order.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        } else {
            Text("ARROW", fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = 14.sp)
            Text("ESCAPE", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 14.sp)
            Spacer(Modifier.height(16.dp))
            Text("Find the order. Clear the grid.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.height(56.dp))
        val wide = Modifier.widthIn(max = if (look.playful) 320.dp else 300.dp).fillMaxWidth().height(if (look.playful) 60.dp else 54.dp)
        val label = if (look.playful) 18.sp else 16.sp
        val spacing = if (look.playful) 0.sp else 1.sp
        Button(onPlay, wide, shape = RoundedCornerShape(look.corner)) {
            if (look.playful) Text("Play  ·  Level $level", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            else Text("Play   Level $level", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(if (look.playful) 14.dp else 12.dp))
        SecondaryButton(onLevels, wide) { Text("Levels", fontSize = label, letterSpacing = spacing) }
        Spacer(Modifier.height(if (look.playful) 14.dp else 12.dp))
        SecondaryButton(onSettings, wide) { Text("Settings", fontSize = label, letterSpacing = spacing) }
    }
}

private const val PER_PAGE = 30

@Composable
fun LevelSelectScreen(progress: Progress, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val pages = (Levels.TOTAL + PER_PAGE - 1) / PER_PAGE
    val pager = rememberPagerState(initialPage = ((progress.unlocked - 1) / PER_PAGE).coerceIn(0, pages - 1)) { pages }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Back", fontSize = 18.sp) }
            Spacer(Modifier.weight(1f))
            Text("Levels ${pager.currentPage * PER_PAGE + 1}–${minOf((pager.currentPage + 1) * PER_PAGE, Levels.TOTAL)}", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val range = (page * PER_PAGE + 1)..minOf((page + 1) * PER_PAGE, Levels.TOTAL)
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(range.toList()) { n -> LevelTile(n, progress.stars[n] ?: 0, locked = n > progress.unlocked, onPick) }
            }
        }
        Text("Swipe for more", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
    }
}

@Composable
private fun LevelTile(n: Int, stars: Int, locked: Boolean, onPick: (Int) -> Unit) {
    val playful = LocalLook.current.playful
    val current = !locked && stars == 0
    val bg = when {
        locked -> if (playful) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent
        current -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(if (playful) 14.dp else 10.dp)).background(bg)
            .then(if (playful) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (locked) 0.5f else 1f), RoundedCornerShape(10.dp)))
            .clickable(enabled = !locked) { onPick(n) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (playful && locked) Text("🔒", fontSize = 14.sp)
            else Text(
                "$n", fontWeight = if (playful) FontWeight.Bold else FontWeight.Medium, fontSize = if (playful) 18.sp else 16.sp,
                color = when { current -> MaterialTheme.colorScheme.onPrimary; locked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f); else -> MaterialTheme.colorScheme.onSurface },
            )
            if (!locked && stars > 0) Text("★".repeat(stars) + "☆".repeat(3 - stars), fontSize = if (playful) 10.sp else 9.sp, color = LocalLook.current.gold, letterSpacing = if (playful) 0.sp else 1.sp)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: Settings,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onStyle: (Style) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onAdPrivacy: (() -> Unit)? = null,
    removeAdsPrice: String? = null,
    purchasePending: Boolean = false,
    onRemoveAds: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onBack) { Text("‹ Back", fontSize = 18.sp) }
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
        ToggleRow("Sound", settings.sound, onSound)
        ToggleRow("Haptics", settings.haptics, onHaptics)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Theme", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(settings.theme == m, { onTheme(m) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
        Text("Style", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Style.entries.forEachIndexed { i, s ->
                SegmentedButton(settings.style == s, { onStyle(s) }, SegmentedButtonDefaults.itemShape(i, Style.entries.size)) {
                    Text(if (s == Style.CLASSIC) "Classic" else "Playful")
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        when {
            settings.adsRemoved -> Text("Ads removed. Thank you!", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            purchasePending -> Text("Remove ads: payment pending", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            removeAdsPrice != null -> Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Button(onRemoveAds) { Text("Remove ads  ·  $removeAdsPrice") }
                Text("No banners or ads between levels. Optional videos for hints stay.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!settings.adsRemoved) TextButton(onRestore, Modifier.padding(horizontal = 4.dp)) { Text("Restore purchases") }
        if (onAdPrivacy != null) TextButton(onAdPrivacy, Modifier.padding(horizontal = 4.dp)) { Text("Ad privacy settings") }
        TextButton({ confirm = true }, Modifier.padding(horizontal = 4.dp)) { Text("Reset progress", color = MaterialTheme.colorScheme.error) }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Reset progress?") },
        text = { Text("All stars and hint tokens will be lost. This can't be undone.") },
        confirmButton = { TextButton({ onReset(); confirm = false }) { Text("Reset", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(value, onChange)
    }
