package com.arrowescape.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arrowescape.game.data.AdBanner
import com.arrowescape.game.data.Ads
import com.arrowescape.game.data.Billing
import com.arrowescape.game.data.Progress
import com.arrowescape.game.data.Settings
import com.arrowescape.game.game.GameViewModel
import com.arrowescape.game.game.Levels
import com.arrowescape.game.ui.ArrowTheme
import com.arrowescape.game.ui.GameScreen
import com.arrowescape.game.ui.HomeScreen
import com.arrowescape.game.ui.LevelSelectScreen
import com.arrowescape.game.ui.SettingsScreen
import com.arrowescape.game.ui.isDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ads: Ads
    private lateinit var billing: Billing
    private var started = false

    override fun onStart() {
        super.onStart()
        if (started) { billing.restore(); ads.onAppForegrounded() } // warm return only; never on cold launch
        started = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ads = Ads(this).also { it.start() }
        val prefs = (application as App).prefs
        billing = Billing(this, prefs, lifecycleScope).also { it.start() }
        lifecycleScope.launch { prefs.settings.collect { ads.removed.value = it.adsRemoved } }
        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(Settings())
            val progress by prefs.progress.collectAsStateWithLifecycle(Progress())
            val dark = isDark(settings.theme)
            val scope = rememberCoroutineScope()
            val privacyRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()
            val removeAdsPrice by billing.price.collectAsStateWithLifecycle()
            val purchasePending by billing.pending.collectAsStateWithLifecycle()
            ArrowTheme(dark, settings.style) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(nav, "home", Modifier.safeDrawingPadding()) {
                        composable("home") {
                            Box(Modifier.fillMaxSize()) {
                                HomeScreen(
                                    progress,
                                    onPlay = { nav.navigate("game/${progress.unlocked.coerceAtMost(Levels.TOTAL)}") },
                                    onLevels = { nav.navigate("levels") },
                                    onSettings = { nav.navigate("settings") },
                                )
                                AdBanner(ads, Modifier.align(Alignment.BottomCenter), collapsible = true)
                            }
                        }
                        composable("levels") {
                            LevelSelectScreen(progress, onPick = { nav.navigate("game/$it") }, onBack = nav::popBackStack)
                        }
                        composable("game/{n}", listOf(navArgument("n") { type = NavType.IntType })) { entry ->
                            val vm: GameViewModel = viewModel()
                            val n = entry.arguments!!.getInt("n")
                            LaunchedEffect(n) { vm.load(n) }
                            GameScreen(vm, ads, settings, onHome = { nav.popBackStack("home", inclusive = false) })
                        }
                        composable("settings") {
                            SettingsScreen(
                                settings,
                                onSound = { scope.launch { prefs.setSound(it) } },
                                onHaptics = { scope.launch { prefs.setHaptics(it) } },
                                onTheme = { scope.launch { prefs.setTheme(it) } },
                                onStyle = { scope.launch { prefs.setStyle(it) } },
                                onReset = { scope.launch { prefs.resetProgress() } },
                                onBack = nav::popBackStack,
                                onAdPrivacy = if (privacyRequired) ads::showPrivacyOptions else null,
                                removeAdsPrice = removeAdsPrice,
                                purchasePending = purchasePending,
                                onRemoveAds = billing::buy,
                                onRestore = billing::restore,
                            )
                        }
                    }
                }
            }
        }
    }
}
