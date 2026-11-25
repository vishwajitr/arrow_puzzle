package com.arrowescape.game.data

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrowescape.game.BuildConfig
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/**
 * AdMob behind Google's consent form (UMP): the SDK is only initialised, and ads only requested, once consent allows it.
 * Banner under the board; interstitial between levels; rewarded for a hint; app-open when returning to the app.
 * Debug builds always use Google's test unit ids (see app/build.gradle.kts).
 */
class Ads(private val activity: Activity) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var initialising = false

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _privacyOptions = MutableStateFlow(false)
    /** True when the user's region requires an "Ad privacy settings" entry point. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptions

    /** Set from the "Remove ads" entitlement: hides the banner, interstitials and app-open ads. Rewarded stays (opt-in). */
    val removed = MutableStateFlow(false)

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady

    /** True while any full-screen ad covers the app, so returning from it doesn't trigger an app-open ad. */
    private var fullScreen = false

    fun start() {
        consent.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { refresh() } },
            { refresh() },
        )
        refresh() // consent from an earlier session may already allow ads
    }

    fun showPrivacyOptions() = UserMessagingPlatform.showPrivacyOptionsForm(activity) { refresh() }

    private fun refresh() {
        _privacyOptions.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!consent.canRequestAds() || initialising) return
        initialising = true
        MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build())
        thread { // the SDK asks to be initialised off the main thread
            MobileAds.initialize(activity) {
                activity.runOnUiThread { _ready.value = true; loadInterstitial(); loadRewarded(); loadAppOpen() }
            }
        }
    }

    private fun callback(reload: () -> Unit, then: () -> Unit = {}) = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() { fullScreen = false; reload(); then() }
        override fun onAdFailedToShowFullScreenContent(e: AdError) { fullScreen = false; reload(); then() }
    }

    // --- Interstitial: between levels -------------------------------------------------------------------------------

    private var interstitial: InterstitialAd? = null
    private var lastFullScreen = 0L

    private fun loadInterstitial() = InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(),
        object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
        })

    /** Shows an interstitial after every [EVERY_N_LEVELS]th clear (with a cooldown), then runs [then]. */
    fun maybeShowInterstitial(clearedLevel: Int, then: () -> Unit) {
        val ad = interstitial
        val now = System.currentTimeMillis()
        if (removed.value || ad == null || clearedLevel < FIRST_LEVEL || clearedLevel % EVERY_N_LEVELS != 0 || now - lastFullScreen < COOLDOWN_MS) return then()
        interstitial = null
        lastFullScreen = now
        fullScreen = true
        ad.fullScreenContentCallback = callback(::loadInterstitial, then)
        ad.show(activity)
    }

    // --- Rewarded: watch an ad for one hint token -------------------------------------------------------------------

    private var rewarded: RewardedAd? = null

    private fun loadRewarded() = RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(),
        object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; _rewardedReady.value = true }
            override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; _rewardedReady.value = false }
        })

    /** [onReward] runs only if the user watched long enough to earn it. */
    fun showRewarded(onReward: () -> Unit) {
        val ad = rewarded ?: return
        rewarded = null
        _rewardedReady.value = false
        fullScreen = true
        ad.fullScreenContentCallback = callback(::loadRewarded)
        ad.show(activity) { onReward() }
    }

    // --- App open: when the user comes back to the app --------------------------------------------------------------

    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L

    private fun loadAppOpen() = AppOpenAd.load(activity, BuildConfig.ADMOB_APP_OPEN_ID, AdRequest.Builder().build(),
        object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) { appOpen = ad; appOpenLoadedAt = System.currentTimeMillis() }
            override fun onAdFailedToLoad(e: LoadAdError) { appOpen = null }
        })

    /** Call from Activity.onStart on a warm return. Skipped right after another full-screen ad or within the cooldown. */
    fun onAppForegrounded() {
        if (fullScreen || removed.value) return
        val ad = appOpen ?: return
        val now = System.currentTimeMillis()
        if (now - appOpenLoadedAt > APP_OPEN_TTL_MS) { appOpen = null; loadAppOpen(); return } // Google: stale after 4h
        if (now - lastFullScreen < COOLDOWN_MS) return
        appOpen = null
        lastFullScreen = now
        fullScreen = true
        ad.fullScreenContentCallback = callback(::loadAppOpen)
        ad.show(activity)
    }

    private companion object {
        const val FIRST_LEVEL = 20 // ad-free start (tutorial + 10 levels): first impressions drive D1 retention
        const val EVERY_N_LEVELS = 2
        const val COOLDOWN_MS = 60_000L // ponytail: one fixed cap shared by interstitial + app-open; tune from AdMob stats
        const val APP_OPEN_TTL_MS = 4 * 3_600_000L
    }
}

/**
 * Anchored adaptive banner. [collapsible] opens it larger first, then the player collapses it to the strip; AdMob allows
 * that only on static screens (Home), never on the puzzle screen, where accidental taps count as invalid clicks.
 * Draws nothing until ads are allowed, or once ads are removed.
 */
@Composable
fun AdBanner(ads: Ads, modifier: Modifier = Modifier, collapsible: Boolean = false) {
    val ready by ads.ready.collectAsStateWithLifecycle()
    val removed by ads.removed.collectAsStateWithLifecycle()
    if (!ready || removed) return
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        @Suppress("DEPRECATION") // still the standard-height anchored banner; the "large" replacement is much taller
        val size = remember(maxWidth) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, maxWidth.value.toInt()) }
        val view = remember(size) {
            AdView(context).apply { setAdSize(size); adUnitId = BuildConfig.ADMOB_BANNER_ID
                val request = AdRequest.Builder()
                if (collapsible) request.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("collapsible", "bottom") })
                loadAd(request.build())
            }
        }
        DisposableEffect(view) { onDispose { view.destroy() } }
        AndroidView({ view }, Modifier.fillMaxWidth().height(size.height.dp))
    }
}
