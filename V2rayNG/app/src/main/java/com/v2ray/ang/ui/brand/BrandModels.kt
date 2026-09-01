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

data class ServerNode(
    val id: String,
    val name: String,
    val countryCode: String = "GLOBAL",
    val flag: String = "🌐",
    val city: String? = null,
    val host: String = "",
    val port: Int = 443,
    val isDefault: Boolean = false,
)

data class MeResult(
    val email: String,
    val role: String,
    val subscriptions: List<SubscriptionInfo>,
    val servers: List<ServerNode> = emptyList(),
)

data class AuthResult(
    val token: String,
    val email: String,
    val role: String,
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

data class GatewayInfo(
    val cryptomus: Boolean = false,
    val nowpayments: Boolean = false,
    val revolut: Boolean = false,
)

data class OrderItem(
    val id: String,
    val planName: String,
    val amountUsd: Double,
    val gateway: String,
    val status: String,
    val createdAt: String,
)

data class ResellerOverview(
    val balanceUsd: Double,
    val discountPct: Int,
    val nextTierMinBalance: Double?,
    val nextTierDiscountPct: Int?,
)

data class ResellerCustomer(
    val id: String,
    val email: String,
    val createdAt: String,
)

data class ResellerCustomerDetail(
    val id: String,
    val email: String,
    val createdAt: String,
    val subscriptions: List<SubscriptionInfo>,
)

data class SubResellerItem(
    val id: String,
    val email: String,
    val balanceUsd: Double,
    val createdAt: String,
    val customerCount: Int,
    val subscriptionCount: Int,
)

data class ResellerOrder(
    val id: String,
    val customerEmail: String,
    val planName: String,
    val amountUsd: Double,
    val status: String,
    val createdAt: String,
)

data class ResellerSubscription(
    val id: String,
    val customerEmail: String,
    val planName: String,
    val expiryDate: String,
    val isActive: Boolean,
    val usedBytes: Long,
    val totalBytes: Long,
)

data class WalletTransactionItem(
    val id: String,
    val type: String,
    val amountUsd: Double,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val description: String?,
    val counterpartEmail: String?,
    val createdAt: String,
)

data class ResellerDeposit(
    val id: String,
    val amountUsd: Double,
    val gateway: String,
    val status: String,
    val createdAt: String,
)

/** Thrown for any non-2xx API response; [message] is the server's own error text when available. */
class ApiException(val statusCode: Int, override val message: String) : Exception(message)
