package com.arrowescape.game.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arrowescape.game.App
import com.arrowescape.game.data.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Status { LOADING, PLAYING, WON, FAILED }

data class GameUi(
    val levelNumber: Int,
    val level: Level? = null,
    val alive: List<Boolean> = emptyList(),
    val hearts: Int = Game.MAX_HEARTS,
    val stars: Int = 0,
    val status: Status = Status.LOADING,
    val hint: Int? = null,
    val tokens: Int = 0,
    val paused: Boolean = false,
)

/** One-shot animation triggers for the board. */
sealed interface BoardEvent {
    data class Exit(val arrow: Int) : BoardEvent
    data class Bounce(val arrow: Int, val distance: Int) : BoardEvent
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = (app as App).prefs
    private val sfx = (app as App).sfx
    private var game: Game? = null

    private val _ui = MutableStateFlow(GameUi(levelNumber = 1))
    val ui = _ui.asStateFlow()
    private val _events = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch { prefs.progress.collect { p -> _ui.update { it.copy(tokens = p.hintTokens) } } }
        viewModelScope.launch { prefs.settings.collect { sfx.enabled = it.sound } }
    }

    fun load(n: Int) {
        if (_ui.value.levelNumber == n && game != null) return
        _ui.update { GameUi(levelNumber = n, tokens = it.tokens) }
        viewModelScope.launch {
            val assets = getApplication<Application>().assets
            val level = withContext(Dispatchers.Default) {
                Levels.load(n) { path -> assets.open(path).bufferedReader().use { it.readText() } }
            }
            game = Game(level)
            publish()
        }
    }

    fun restart() {
        val g = game ?: return
        game = Game(g.level)
        _ui.update { it.copy(paused = false) }
        publish()
    }

    fun setPaused(p: Boolean) = _ui.update { it.copy(paused = p) }

    fun tap(arrow: Int) {
        val g = game ?: return
        if (_ui.value.paused) return
        when (val r = g.tap(arrow)) {
            is TapResult.Exited -> {
                sfx.play(Sound.EXIT)
                _events.tryEmit(BoardEvent.Exit(r.arrow))
                if (g.won) {
                    sfx.play(Sound.WIN)
                    viewModelScope.launch { prefs.recordWin(_ui.value.levelNumber, g.stars) }
                }
            }
            is TapResult.Blocked -> {
                sfx.play(Sound.BLOCK)
                _events.tryEmit(BoardEvent.Bounce(r.arrow, r.distance))
            }
            TapResult.Ignored -> return
        }
        publish(keepHint = _ui.value.hint?.takeIf { g.board.alive[it] })
    }

    /** Spends a token to highlight a removable arrow. */
    fun hint() {
        val g = game ?: return
        if (_ui.value.hint != null || g.won) return
        viewModelScope.launch {
            if (prefs.spendToken()) _ui.update { it.copy(hint = g.hint()) }
        }
    }

    fun grantToken() { viewModelScope.launch { prefs.addToken() } }

    /** Spends a token to continue after running out of hearts. */
    fun revive() {
        val g = game ?: return
        viewModelScope.launch {
            if (prefs.spendToken()) { g.revive(); publish() }
        }
    }

    private fun publish(keepHint: Int? = null) {
        val g = game ?: return
        _ui.update {
            it.copy(
                level = g.level,
                alive = g.board.alive.toList(),
                hearts = g.hearts,
                stars = g.stars,
                status = when { g.won -> Status.WON; g.failed -> Status.FAILED; else -> Status.PLAYING },
                hint = keepHint,
            )
        }
    }
}
