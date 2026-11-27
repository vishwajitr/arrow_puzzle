package com.arrowescape.game.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.ln
import kotlin.math.roundToInt

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Classic: calm, thin-line look for adults. Playful: bold, bright look for kids. */
enum class Style { CLASSIC, PLAYFUL }

data class Settings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val style: Style = Style.CLASSIC,
    /** Cached "Remove ads" entitlement; Play's purchase list (see Billing) is the source of truth. */
    val adsRemoved: Boolean = false,
)

data class Progress(
    val stars: Map<Int, Int> = emptyMap(), // level -> best stars
    val hintTokens: Int = START_TOKENS,
) {
    /**
     * Playful "Puzzle IQ" for the share card, not a real IQ: grows with levels cleared (log, so it slows down) and with
     * average stars (skips count as 0). 80 at the start, about 116 after the tutorial, about 154 at level 500.
     */
    val iq get() = if (stars.isEmpty()) 80 else (80 + 10 * ln(1.0 + stars.size) + 4 * stars.values.average()).roundToInt().coerceAtMost(160)

    /** Highest playable level: one past the highest cleared. */
    val unlocked get() = (stars.keys.maxOrNull() ?: 0) + 1

    companion object { const val START_TOKENS = 3 }
}

private val Context.store by preferencesDataStore("arrow_escape")

class Prefs(context: Context) {
    private val store = context.store
    private val sound = booleanPreferencesKey("sound")
    private val haptics = booleanPreferencesKey("haptics")
    private val theme = stringPreferencesKey("theme")
    private val style = stringPreferencesKey("style")
    private val tokens = intPreferencesKey("hint_tokens")
    private val adsRemoved = booleanPreferencesKey("ads_removed")
    private val reviewAsked = booleanPreferencesKey("review_asked")
    private fun starsKey(level: Int) = intPreferencesKey("stars_$level")

    val settings: Flow<Settings> = store.data.map {
        Settings(
            it[sound] ?: true, it[haptics] ?: true,
            it[theme]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
            it[style]?.let(Style::valueOf) ?: Style.CLASSIC,
            it[adsRemoved] ?: false,
        )
    }

    val progress: Flow<Progress> = store.data.map { p ->
        Progress(
            stars = p.asMap().mapNotNull { (k, v) -> k.name.removePrefix("stars_").toIntOrNull()?.let { it to v as Int } }.toMap(),
            hintTokens = p[tokens] ?: Progress.START_TOKENS,
        )
    }

    suspend fun currentProgress() = progress.first()

    suspend fun setSound(on: Boolean) = store.edit { it[sound] = on }
    suspend fun setHaptics(on: Boolean) = store.edit { it[haptics] = on }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[theme] = mode.name }
    suspend fun setStyle(s: Style) = store.edit { it[style] = s.name }
    suspend fun setAdsRemoved(on: Boolean) = store.edit { it[adsRemoved] = on }

    /** True only the first time ever: the in-app review is asked for once per install. */
    suspend fun markReviewAsked(): Boolean {
        var first = false
        store.edit { first = it[reviewAsked] != true; it[reviewAsked] = true }
        return first
    }

    /** Keeps the best result. A first 3-star clear earns a hint token. */
    suspend fun recordWin(level: Int, stars: Int) = store.edit {
        val old = it[starsKey(level)] ?: 0
        if (stars == 3 && old < 3) it[tokens] = (it[tokens] ?: Progress.START_TOKENS) + 1
        if (stars > old || it[starsKey(level)] == null) it[starsKey(level)] = stars // 0 stars = skipped, still unlocks the next
    }

    /** Reward for watching a rewarded ad. */
    suspend fun addToken() = store.edit { it[tokens] = (it[tokens] ?: Progress.START_TOKENS) + 1 }

    /** Returns false (and spends nothing) when there are fewer than [n] tokens. */
    suspend fun spendToken(n: Int = 1): Boolean {
        var ok = false
        store.edit {
            val t = it[tokens] ?: Progress.START_TOKENS
            ok = t >= n
            if (ok) it[tokens] = t - n
        }
        return ok
    }

    /** Clears stars and tokens; keeps settings. */
    suspend fun resetProgress() = store.edit { p ->
        p.asMap().keys.filter { it.name.startsWith("stars_") || it == tokens }.forEach { p.remove(it as Preferences.Key<*>) }
    }
}
