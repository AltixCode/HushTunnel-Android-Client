package com.v2ray.ang.ui.brand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** org.json quirk: a JSON `null` value round-trips as the sentinel [JSONObject.NULL], not Kotlin null. */
private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key)

// Talks to backend mobile REST endpoints (Bearer-token auth)
object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            var request = chain.request()
            var response = chain.proceed(request)
            var attempts = 0
            while (response.isRedirect && attempts < 5) {
                val location = response.header("Location") ?: break
                val newUrl = if (location.startsWith("http")) location else request.url.resolve(location)?.toString() ?: break
                val authHeader = request.header("Authorization")
                val newRequestBuilder = request.newBuilder().url(newUrl)
                if (authHeader != null) {
                    newRequestBuilder.header("Authorization", authHeader)
                }
                response.close()
                request = newRequestBuilder.build()
                response = chain.proceed(request)
                attempts++
            }
            response
        }
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url("${BrandConfig.API_BASE_URL}$path")
        if (token != null) requestBuilder.addHeader("Authorization", "Bearer $token")

        when (method) {
            "POST" -> requestBuilder.post((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
            "PUT" -> requestBuilder.put((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
            "DELETE" -> if (body != null) {
                requestBuilder.delete(body.toString().toRequestBody(jsonMediaType))
            } else {
                requestBuilder.delete()
            }
            "GET" -> requestBuilder.get()
            else -> error("Unsupported method $method")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
            if (!response.isSuccessful) {
                throw ApiException(response.code, json.optString("error", "Request failed (HTTP ${response.code})"))
            }
            json
        }
    }

    suspend fun register(email: String, password: String): AuthResult {
        val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val json = request(
            "/api/mobile/register", "POST",
            body = JSONObject().put("email", cleanEmail).put("password", password)
        )
        return AuthResult(
            token = json.getString("token"),
            email = json.getString("email"),
            role = json.optString("role", "USER"),
        )
    }

    suspend fun login(email: String, password: String): AuthResult {
        val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val json = request(
            "/api/mobile/login", "POST",
            body = JSONObject().put("email", cleanEmail).put("password", password)
        )
        return AuthResult(
            token = json.getString("token"),
            email = json.getString("email"),
            role = json.optString("role", "USER"),
        )
    }

    suspend fun me(token: String): MeResult {
        val json = request("/api/mobile/me", token = token)
        val subsJson = json.optJSONArray("subscriptions") ?: JSONArray()
        val subs = (0 until subsJson.length()).map { i ->
            val s = subsJson.getJSONObject(i)
            val up = s.optLong("upload", s.optLong("up", 0L))
            val down = s.optLong("download", s.optLong("down", 0L))
            val used = s.optLong("usedBytes", s.optLong("used", up + down))
            val total = s.optLong("totalBytes", s.optLong("total", s.optLong("dataLimit", 0L)))
            SubscriptionInfo(
                id = s.getString("id"),
                planName = s.getString("planName"),
                expiryDate = s.getString("expiryDate"),
                isActive = s.optBoolean("isActive", true),
                usedBytes = used,
                totalBytes = total,
                subscriptionUrl = s.getString("subscriptionUrl"),
            )
        }
        val serversJson = json.optJSONArray("servers") ?: JSONArray()
        val servers = (0 until serversJson.length()).map { i ->
            val s = serversJson.getJSONObject(i)
            ServerNode(
                id = s.getString("id"),
                name = s.getString("name"),
                countryCode = s.optString("countryCode", "GLOBAL"),
                flag = s.optString("flag", "🌐"),
                city = s.optNullableString("city"),
                host = s.optString("host", ""),
                port = s.optInt("port", 443),
                isDefault = s.optBoolean("isDefault", false),
            )
        }
        return MeResult(
            email = json.getString("email"),
            role = json.optString("role", "USER"),
            subscriptions = subs,
            servers = servers,
        )
    }

    suspend fun plans(): List<PlanInfo> {
        val json = request("/api/mobile/plans")
        val plansJson: JSONArray = json.optJSONArray("plans") ?: JSONArray()
        return (0 until plansJson.length()).map { i ->
            val p = plansJson.getJSONObject(i)
            PlanInfo(
                id = p.getString("id"),
                name = p.getString("name"),
                description = p.optNullableString("description"),
                priceUsd = p.getDouble("priceUsd"),
                durationDays = p.getInt("durationDays"),
                trafficLimitGb = p.getInt("trafficLimitGb"),
            )
        }
    }

    suspend fun gateways(): GatewayInfo {
        return try {
            val json = request("/api/mobile/gateways")
            GatewayInfo(
                cryptomus = json.optBoolean("cryptomus", false),
                nowpayments = json.optBoolean("nowpayments", false),
                revolut = json.optBoolean("revolut", false),
            )
        } catch (_: Exception) {
            GatewayInfo()
        }
    }

    suspend fun checkout(token: String, planId: String, gateway: String, subscriptionId: String? = null): CheckoutResult {
        val body = JSONObject().put("planId", planId).put("gateway", gateway)
        if (subscriptionId != null) body.put("subscriptionId", subscriptionId)
        val json = request("/api/mobile/checkout", "POST", token = token, body = body)
        return CheckoutResult(
            orderId = json.getString("orderId"),
            checkoutUrl = json.optNullableString("checkoutUrl"),
            gateway = json.optString("gateway", gateway),
        )
    }

    suspend fun orderStatus(token: String, orderId: String): OrderStatus {
        val json = request("/api/mobile/orders/$orderId", token = token)
        return OrderStatus(
            id = json.getString("id"),
            status = json.getString("status"),
            subscriptionId = json.optNullableString("subscriptionId"),
        )
    }

    suspend fun orders(token: String): List<OrderItem> {
        val json = request("/api/mobile/orders", token = token)
        val ordersJson = json.optJSONArray("orders") ?: JSONArray()
        return (0 until ordersJson.length()).map { i ->
            val o = ordersJson.getJSONObject(i)
            OrderItem(
                id = o.getString("id"),
                planName = o.optString("planName", "VPN Plan"),
                amountUsd = o.optDouble("amountUsd", 0.0),
                gateway = o.optString("gateway", "MANUAL"),
                status = o.optString("status", "PENDING"),
                createdAt = o.optString("createdAt", ""),
            )
        }
    }

    // ---------- Reseller API ----------

    suspend fun resellerOverview(token: String): ResellerOverview {
        val json = request("/api/mobile/reseller/overview", token = token)
        val nextTier = json.optJSONObject("nextTier")
        return ResellerOverview(
            balanceUsd = json.optDouble("balanceUsd", 0.0),
            discountPct = json.optInt("discountPct", 0),
            nextTierMinBalance = if (nextTier != null && !nextTier.isNull("minBalance")) nextTier.getDouble("minBalance") else null,
            nextTierDiscountPct = if (nextTier != null && !nextTier.isNull("discountPct")) nextTier.getInt("discountPct") else null,
        )
    }

    suspend fun resellerCustomers(token: String): List<ResellerCustomer> {
        val json = request("/api/mobile/reseller/customers", token = token)
        val list = json.optJSONArray("customers") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val c = list.getJSONObject(i)
            ResellerCustomer(
                id = c.getString("id"),
                email = c.getString("email"),
                createdAt = c.optString("createdAt", ""),
            )
        }
    }

    suspend fun resellerCustomerDetails(token: String, id: String): ResellerCustomerDetail {
        val json = request("/api/mobile/reseller/customers/$id", token = token)
        val c = json.getJSONObject("customer")
        val subsJson = c.optJSONArray("subscriptions") ?: JSONArray()
        val subs = (0 until subsJson.length()).map { i ->
            val s = subsJson.getJSONObject(i)
            val up = s.optLong("upload", s.optLong("up", 0L))
            val down = s.optLong("download", s.optLong("down", 0L))
            val used = s.optLong("usedBytes", s.optLong("used", up + down))
            val total = s.optLong("totalBytes", s.optLong("total", s.optLong("dataLimit", 0L)))
            SubscriptionInfo(
                id = s.getString("id"),
                planName = s.getString("planName"),
                expiryDate = s.getString("expiryDate"),
                isActive = s.optBoolean("isActive", true),
                usedBytes = used,
                totalBytes = total,
                subscriptionUrl = s.getString("subscriptionUrl"),
            )
        }
        return ResellerCustomerDetail(
            id = c.getString("id"),
            email = c.getString("email"),
            createdAt = c.optString("createdAt", ""),
            subscriptions = subs,
        )
    }

    suspend fun createResellerCustomer(token: String, email: String, password: String? = null): Pair<ResellerCustomer, String> {
        val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val body = JSONObject().put("email", cleanEmail)
        if (!password.isNullOrBlank()) body.put("password", password)
        val json = request(
            "/api/mobile/reseller/customers", "POST", token = token,
            body = body
        )
        val customer = ResellerCustomer(
            id = json.getString("id"),
            email = json.getString("email"),
            createdAt = json.optString("createdAt", ""),
        )
        return customer to json.getString("generatedPassword")
    }

    suspend fun resellerOrders(token: String): List<ResellerOrder> {
        val json = request("/api/mobile/reseller/orders", token = token)
        val list = json.optJSONArray("orders") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            ResellerOrder(
                id = o.getString("id"),
                customerEmail = o.optString("customerEmail", ""),
                planName = o.optString("planName", ""),
                amountUsd = o.optDouble("amountUsd", 0.0),
                status = o.optString("status", "PAID"),
                createdAt = o.optString("createdAt", ""),
            )
        }
    }

    suspend fun createResellerOrder(token: String, customerEmail: String, planId: String): Pair<String, String?> {
        val json = request(
            "/api/mobile/reseller/orders", "POST", token = token,
            body = JSONObject().put("customerEmail", customerEmail).put("planId", planId)
        )
        return json.getString("orderId") to json.optNullableString("generatedPassword")
    }

    suspend fun resellerSubscriptions(token: String): List<ResellerSubscription> {
        val json = request("/api/mobile/reseller/subscriptions", token = token)
        val list = json.optJSONArray("subscriptions") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            ResellerSubscription(
                id = s.getString("id"),
                customerEmail = s.optString("customerEmail", ""),
                planName = s.optString("planName", ""),
                expiryDate = s.optString("expiryDate", ""),
                isActive = s.optBoolean("isActive", true),
                usedBytes = s.optLong("usedBytes", 0L),
                totalBytes = s.optLong("totalBytes", 0L),
            )
        }
    }

    suspend fun extendResellerSubscription(token: String, id: String, days: Int) {
        request("/api/mobile/reseller/subscriptions/$id/extend", "POST", token = token, body = JSONObject().put("days", days))
    }

    suspend fun toggleResellerSubscription(token: String, id: String, enable: Boolean) {
        request("/api/mobile/reseller/subscriptions/$id/toggle", "POST", token = token, body = JSONObject().put("enable", enable))
    }

    suspend fun resetResellerSubscriptionUuid(token: String, id: String) {
        request("/api/mobile/reseller/subscriptions/$id/reset-uuid", "POST", token = token)
    }

    suspend fun resetResellerSubscriptionTraffic(token: String, id: String) {
        request("/api/mobile/reseller/subscriptions/$id/reset-traffic", "POST", token = token)
    }

    suspend fun revokeResellerSubscription(token: String, id: String) {
        request("/api/mobile/reseller/subscriptions/$id/revoke", "POST", token = token)
    }

    suspend fun getWalletTransactions(token: String): List<WalletTransactionItem> {
        val json = request("/api/mobile/wallet/transactions", token = token)
        val list = json.optJSONArray("transactions") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val t = list.getJSONObject(i)
            WalletTransactionItem(
                id = t.getString("id"),
                type = t.optString("type", "UNKNOWN"),
                amountUsd = t.optDouble("amountUsd", 0.0),
                balanceBefore = t.optDouble("balanceBefore", 0.0),
                balanceAfter = t.optDouble("balanceAfter", 0.0),
                description = t.optString("description").takeIf { it.isNotBlank() },
                counterpartEmail = t.optString("counterpartEmail").takeIf { it.isNotBlank() },
                createdAt = t.optString("createdAt", ""),
            )
        }
    }

    suspend fun resellerDeposits(token: String): List<ResellerDeposit> {
        val json = request("/api/mobile/reseller/deposits", token = token)
        val list = json.optJSONArray("deposits") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            ResellerDeposit(
                id = d.getString("id"),
                amountUsd = d.optDouble("amountUsd", 0.0),
                gateway = d.optString("gateway", "MANUAL"),
                status = d.optString("status", "PENDING"),
                createdAt = d.optString("createdAt", ""),
            )
        }
    }

    suspend fun createResellerDeposit(token: String, amountUsd: Double, gateway: String): Pair<String, String?> {
        val json = request(
            "/api/mobile/reseller/deposits", "POST", token = token,
            body = JSONObject().put("amountUsd", amountUsd).put("gateway", gateway)
        )
        return json.optString("depositId", "") to json.optNullableString("checkoutUrl")
    }

    suspend fun changePassword(token: String, currentPassword: String?, newPassword: String) {
        val body = JSONObject().put("newPassword", newPassword)
        if (!currentPassword.isNullOrBlank()) {
            body.put("currentPassword", currentPassword)
        }
        request("/api/mobile/account/password", "POST", token = token, body = body)
    }

    suspend fun updateCustomerPassword(token: String, customerId: String, newPassword: String) {
        request("/api/mobile/reseller/customers/$customerId/password", "POST", token = token, body = JSONObject().put("newPassword", newPassword))
    }

    suspend fun resetCustomerPassword(token: String, customerId: String): String {
        val json = request("/api/mobile/reseller/customers/$customerId/password", "POST", token = token)
        return json.getString("newPassword")
    }

    suspend fun deleteCustomer(token: String, customerId: String) {
        request("/api/mobile/reseller/customers/$customerId", "DELETE", token = token)
    }

    suspend fun createSelfSubscription(token: String, planId: String, subscriptionId: String? = null): String {
        val body = JSONObject().put("planId", planId)
        if (!subscriptionId.isNullOrBlank()) {
            body.put("subscriptionId", subscriptionId)
        }
        val json = request("/api/mobile/reseller/self-subscription", "POST", token = token, body = body)
        return json.optString("subscriptionId", "")
    }

    suspend fun transferFunds(token: String, recipientEmail: String, amountUsd: Double, description: String? = null): JSONObject {
        val body = JSONObject()
            .put("recipientEmail", recipientEmail)
            .put("amountUsd", amountUsd)
        if (!description.isNullOrBlank()) {
            body.put("description", description)
        }
        return request("/api/mobile/wallet/transfer", "POST", token = token, body = body)
    }

    suspend fun getTransactions(token: String): JSONArray {
        val json = request("/api/mobile/wallet/transactions", "GET", token = token)
        return json.optJSONArray("transactions") ?: JSONArray()
    }

    suspend fun resellerSubResellers(token: String): List<SubResellerItem> {
        val json = request("/api/mobile/reseller/resellers", token = token)
        val list = json.optJSONArray("resellers") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val r = list.getJSONObject(i)
            val count = r.optJSONObject("_count")
            SubResellerItem(
                id = r.getString("id"),
                email = r.getString("email"),
                balanceUsd = r.optDouble("balanceUsd", 0.0),
                createdAt = r.optString("createdAt", ""),
                customerCount = count?.optInt("customers", 0) ?: 0,
                subscriptionCount = count?.optInt("subscriptions", 0) ?: 0,
            )
        }
    }

    /** Returns the new sub-reseller plus its one-time generated password. */
    suspend fun createSubReseller(token: String, email: String, initialBalanceUsd: Double): Pair<SubResellerItem, String> {
        val cleanEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val body = JSONObject()
            .put("email", cleanEmail)
            .put("initialBalanceUsd", initialBalanceUsd)
        val json = request("/api/mobile/reseller/resellers", "POST", token = token, body = body)
        val r = json.getJSONObject("reseller")
        val item = SubResellerItem(
            id = r.getString("id"),
            email = r.getString("email"),
            balanceUsd = r.optDouble("balanceUsd", 0.0),
            createdAt = "",
            customerCount = 0,
            subscriptionCount = 0,
        )
        return item to r.getString("generatedPassword")
    }
}
