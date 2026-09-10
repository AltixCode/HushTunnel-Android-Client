package com.v2ray.ang.ui.brand

import android.app.Activity
import android.content.Context
import android.net.Uri

/** F-Droid deliberately excludes Google Play Billing and the RevenueCat SDK. */
object StorePurchaseManager {
    const val supported = false

    fun configure(context: Context, config: IapConfig, onReady: () -> Unit, onError: (String) -> Unit) =
        onError("In-app purchases are only available in the Google Play edition")

    fun loadProducts(config: IapConfig, onSuccess: (List<StoreProductOption>) -> Unit, onError: (String) -> Unit) =
        onSuccess(emptyList())

    fun purchase(activity: Activity, productId: String, onSuccess: () -> Unit, onCancelled: () -> Unit, onError: (String) -> Unit) =
        onError("In-app purchases are only available in the Google Play edition")

    fun restore(onSuccess: () -> Unit, onError: (String) -> Unit) = onError("Not available")

    fun managementUrl(onResult: (Uri?) -> Unit) = onResult(null)
}
