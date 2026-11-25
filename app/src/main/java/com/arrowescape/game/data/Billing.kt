package com.arrowescape.game.data

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One-time "Remove ads" purchase (Play product id [REMOVE_ADS], create it in Play Console → Monetize → In-app products).
 * Play's purchase list is the source of truth: it is re-read on every start/resume (that is the restore), and the
 * result is cached in [Prefs] so ads stay off offline. A refunded purchase disappears from the list and ads return.
 */
class Billing(private val activity: Activity, private val prefs: Prefs, private val scope: CoroutineScope) : PurchasesUpdatedListener {
    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var details: ProductDetails? = null
    private val _price = MutableStateFlow<String?>(null)
    /** Localised price, or null until Play knows the product (not set up yet, no Play Store, offline). */
    val price: StateFlow<String?> = _price

    private val _pending = MutableStateFlow(false)
    /** A purchase waiting for payment (e.g. cash at a shop); ads stay until it completes. */
    val pending: StateFlow<Boolean> = _pending

    fun start() = client.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(r: BillingResult) {
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return
            restore()
            loadDetails()
        }
        override fun onBillingServiceDisconnected() = Unit // auto reconnection is on
    })

    /** Re-reads what this Google account owns. Called on start, on resume and from Settings → Restore purchases. */
    fun restore() {
        if (!client.isReady) return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) handle(list, complete = true)
        }
    }

    private fun loadDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(REMOVE_ADS).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, result ->
            details = result.productDetailsList.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    fun buy() {
        val d = details ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: List<Purchase>?) {
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), complete = false)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
        }
    }

    /** [complete]: the list is everything the account owns, so an absent product means not owned (e.g. refunded). */
    private fun handle(purchases: List<Purchase>, complete: Boolean) {
        val mine = purchases.filter { REMOVE_ADS in it.products }
        val owned = mine.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Unacknowledged purchases are refunded by Play after 3 days.
        owned.filterNot { it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
        }
        _pending.value = mine.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        if (owned.isNotEmpty() || complete) scope.launch { prefs.setAdsRemoved(owned.isNotEmpty()) }
    }

    companion object { const val REMOVE_ADS = "remove_ads" }
}
