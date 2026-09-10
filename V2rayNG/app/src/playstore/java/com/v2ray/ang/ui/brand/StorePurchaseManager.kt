package com.v2ray.ang.ui.brand

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith

/** Google Play implementation. Purchase authority remains the verified backend webhook. */
object StorePurchaseManager {
    const val supported = true

    private val packagesById = mutableMapOf<String, Package>()

    fun configure(
        context: Context,
        config: IapConfig,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!config.enabled || config.publicSdkKey.isBlank() || config.appUserId.isBlank()) {
            onError("In-app purchases are not configured")
            return
        }

        if (!Purchases.isConfigured) {
            Purchases.logLevel = if (com.v2ray.ang.BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(context.applicationContext, config.publicSdkKey)
                    .appUserID(config.appUserId)
                    .build()
            )
            onReady()
        } else if (Purchases.sharedInstance.appUserID == config.appUserId) {
            onReady()
        } else {
            Purchases.sharedInstance.logInWith(
                appUserID = config.appUserId,
                onError = { onError(it.message) },
                onSuccess = { _, _ -> onReady() },
            )
        }
    }

    fun loadProducts(
        config: IapConfig,
        onSuccess: (List<StoreProductOption>) -> Unit,
        onError: (String) -> Unit,
    ) {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { onError(it.message) },
            onSuccess = { offerings ->
                val packages = offerings.current?.availablePackages.orEmpty()
                packagesById.clear()
                val subscriptions = config.subscriptionProducts.associateBy { canonical(it.productId) }
                val wallet = config.walletProducts.associateBy { canonical(it.productId) }
                val result = packages.mapNotNull { purchasePackage ->
                    val canonicalId = canonical(purchasePackage.product.id)
                    val subscription = subscriptions[canonicalId]
                    val funds = wallet[canonicalId]
                    val kind = when {
                        subscription != null -> StoreProductKind.SUBSCRIPTION
                        funds != null -> StoreProductKind.WALLET
                        else -> return@mapNotNull null
                    }
                    packagesById[canonicalId] = purchasePackage
                    StoreProductOption(
                        id = canonicalId,
                        title = purchasePackage.product.title,
                        description = purchasePackage.product.description,
                        formattedPrice = purchasePackage.product.price.formatted,
                        kind = kind,
                        durationDays = subscription?.durationDays,
                        walletAmountUsd = funds?.amountUsd,
                    )
                }.sortedWith(
                    compareBy<StoreProductOption> { it.kind.ordinal }
                        .thenBy { it.durationDays ?: Int.MAX_VALUE }
                        .thenBy { it.walletAmountUsd ?: Double.MAX_VALUE }
                )
                onSuccess(result)
            },
        )
    }

    fun purchase(
        activity: Activity,
        productId: String,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val purchasePackage = packagesById[canonical(productId)]
        if (purchasePackage == null) {
            onError("This product is currently unavailable")
            return
        }
        Purchases.sharedInstance.purchaseWith(
            PurchaseParams.Builder(activity, purchasePackage).build(),
            onError = { error, userCancelled ->
                if (userCancelled) onCancelled() else onError(error.message)
            },
            onSuccess = { _, _ -> onSuccess() },
        )
    }

    fun restore(onSuccess: () -> Unit, onError: (String) -> Unit) {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { onError(it.message) },
            onSuccess = { onSuccess() },
        )
    }

    fun managementUrl(onResult: (Uri?) -> Unit) {
        if (!Purchases.isConfigured) {
            onResult(null)
            return
        }
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { onResult(null) },
            onSuccess = { onResult(it.managementURL) },
        )
    }

    private fun canonical(productId: String): String = productId.substringBefore(':')
}
