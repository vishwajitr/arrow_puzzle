package com.arrowescape.game

import android.app.Application
import com.arrowescape.game.data.Prefs
import com.arrowescape.game.data.Sfx

/** Manual DI: the whole object graph is these two singletons. */
class App : Application() {
    val prefs by lazy { Prefs(this) }
    val sfx by lazy { Sfx(this) }
}
