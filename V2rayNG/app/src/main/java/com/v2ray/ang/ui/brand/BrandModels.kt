package com.v2ray.ang.ui.brand

data class PlanInfo(
    val id: String,
    val name: String,
    val description: String?,
    val priceUsd: Double,
    val durationDays: Int,
    val trafficLimitGb: Int,
)

data class SubscriptionInfo(
    val id: String,
    val planName: String,
    val expiryDate: String,
    val isActive: Boolean,
    val usedBytes: Long,
    val totalBytes: Long,
    val subscriptionUrl: String,
)

data class MeResult(
    val email: String,
    val role: String,
    val subscriptions: List<SubscriptionInfo>,
)

data class CheckoutResult(
    val orderId: String,
    val checkoutUrl: String?,
    val gateway: String,
)

data class OrderStatus(
    val id: String,
    val status: String,
    val subscriptionId: String?,
)

/** Thrown for any non-2xx API response; [message] is the server's own error text when available. */
class ApiException(val statusCode: Int, override val message: String) : Exception(message)
