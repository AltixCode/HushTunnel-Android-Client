package com.v2ray.ang.ui.brand

import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Bridges our backend's subscription feed into v2rayNG's normal (headless)
 * subscription-import mechanism, so the user never sees a server list or a
 * "paste subscription URL" screen — this runs invisibly after login/order.
 */
object ProvisionHelper {

    private const val REMARK = "HushTunnel"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Points the app's single subscription at [subscriptionUrl] and fetches it now.
     * Returns true if at least one server profile is selected afterwards.
     */
    suspend fun provisionSubscription(subscriptionUrl: String, preferredServer: ServerNode? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingGuid = MmkvManager.decodeSubsList().firstOrNull()?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()

            val effectiveUrl = if (subscriptionUrl.startsWith("http://localhost:3000") || subscriptionUrl.startsWith("http://127.0.0.1:3000")) {
                subscriptionUrl.replace("http://localhost:3000", BrandConfig.API_BASE_URL)
                    .replace("http://127.0.0.1:3000", BrandConfig.API_BASE_URL)
            } else {
                subscriptionUrl
            }

            val subItem = SubscriptionItem(
                remarks = REMARK,
                url = effectiveUrl,
                enabled = true,
                allowInsecureUrl = true,
            )
            MmkvManager.encodeSubscription(existingGuid, subItem)

            // Direct fetch of base64 subscription config without proxy
            val req = Request.Builder().url(effectiveUrl).build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        AngConfigManager.importBatchConfig(body, existingGuid, false)
                    }
                }
            }

            val selectedByNode = selectServerByNode(preferredServer)
            if (!selectedByNode) {
                val selected = MmkvManager.getSelectServer()
                if (selected.isNullOrEmpty()) {
                    val serverList = MmkvManager.decodeServerList(existingGuid)
                    if (serverList.isNotEmpty()) {
                        MmkvManager.setSelectServer(serverList.first())
                    }
                }
            }

            MmkvManager.getSelectServer()?.isNotBlank() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Finds the profile GUID matching the given server node and sets it as the active server.
     */
    fun selectServerByNode(serverNode: ServerNode?): Boolean {
        if (serverNode == null) return false
        val allServers = MmkvManager.decodeAllServerList()
        for (guid in allServers) {
            val config = MmkvManager.decodeServerConfig(guid) ?: continue
            val matchesHost = config.server?.trim().equals(serverNode.host.trim(), ignoreCase = true)
            val matchesName = config.remarks.contains(serverNode.name, ignoreCase = true) ||
                    config.remarks.contains(serverNode.countryCode, ignoreCase = true) ||
                    (!serverNode.city.isNullOrBlank() && config.remarks.contains(serverNode.city, ignoreCase = true))
            if (matchesHost || matchesName) {
                MmkvManager.setSelectServer(guid)
                return true
            }
        }
        return false
    }
}
