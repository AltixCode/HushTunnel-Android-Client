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

/**
 * Talks to the backend's /api/mobile/* REST endpoints (Bearer-token auth,
 * same JWT format the web dashboard uses for its cookie session). See
 * lib/mobile-auth.ts and app/api/mobile/* in the vpn-billing-dashboard repo.
 */
object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

    suspend fun register(email: String, password: String): Pair<String, String> {
        val json = request(
            "/api/mobile/register", "POST",
            body = JSONObject().put("email", email).put("password", password)
        )
        return json.getString("token") to json.getString("email")
    }

    suspend fun login(email: String, password: String): Pair<String, String> {
        val json = request(
            "/api/mobile/login", "POST",
            body = JSONObject().put("email", email).put("password", password)
        )
        return json.getString("token") to json.getString("email")
    }

    suspend fun me(token: String): MeResult {
        val json = request("/api/mobile/me", token = token)
        val subsJson = json.getJSONArray("subscriptions")
        val subs = (0 until subsJson.length()).map { i ->
            val s = subsJson.getJSONObject(i)
            SubscriptionInfo(
                id = s.getString("id"),
                planName = s.getString("planName"),
                expiryDate = s.getString("expiryDate"),
                isActive = s.getBoolean("isActive"),
                usedBytes = s.getLong("usedBytes"),
                totalBytes = s.getLong("totalBytes"),
                subscriptionUrl = s.getString("subscriptionUrl"),
            )
        }
        return MeResult(email = json.getString("email"), role = json.getString("role"), subscriptions = subs)
    }

    suspend fun plans(): List<PlanInfo> {
        val json = request("/api/mobile/plans")
        val plansJson: JSONArray = json.getJSONArray("plans")
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

    suspend fun checkout(token: String, planId: String, gateway: String, subscriptionId: String? = null): CheckoutResult {
        val body = JSONObject().put("planId", planId).put("gateway", gateway)
        if (subscriptionId != null) body.put("subscriptionId", subscriptionId)
        val json = request("/api/mobile/checkout", "POST", token = token, body = body)
        return CheckoutResult(
            orderId = json.getString("orderId"),
            checkoutUrl = json.optNullableString("checkoutUrl"),
            gateway = json.getString("gateway"),
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
}
