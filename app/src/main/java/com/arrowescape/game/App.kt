package com.arrowescape.game

import android.app.Application
import com.arrowescape.game.data.Prefs
import com.google.firebase.analytics.FirebaseAnalytics
import com.arrowescape.game.data.Sfx

/** Manual DI: the whole object graph is these singletons. */
class App : Application() {
    val prefs by lazy { Prefs(this) }
    val sfx by lazy { Sfx(this) }
    /** Firebase logs retention, ad_impression (from AdMob) and in_app_purchase (from Play) by itself; level events are ours. */
    val analytics by lazy { FirebaseAnalytics.getInstance(this) }
}
