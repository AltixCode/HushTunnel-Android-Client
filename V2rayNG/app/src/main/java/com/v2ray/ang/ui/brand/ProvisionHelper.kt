package com.v2ray.ang.ui.brand

import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.UUID

/**
 * Bridges our backend's subscription feed into v2rayNG's normal (headless)
 * subscription-import mechanism, so the user never sees a server list or a
 * "paste subscription URL" screen — this runs invisibly after login/order.
 */
object ProvisionHelper {

    private const val REMARK = "HushTunnel"

    /**
     * Points the app's single subscription at [subscriptionUrl] and fetches it now.
     * Returns true if at least one server profile is selected afterwards.
     */
    suspend fun provisionSubscription(subscriptionUrl: String): Boolean = withContext(Dispatchers.IO) {
        val existingGuid = MmkvManager.decodeSubsList().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        val subItem = SubscriptionItem(
            remarks = REMARK,
            url = subscriptionUrl,
            enabled = true,
        )
        MmkvManager.encodeSubscription(existingGuid, subItem)

        AngConfigManager.updateConfigViaSubAll()

        val selected = MmkvManager.getSelectServer()
        if (selected.isNullOrEmpty()) {
            val serverList = MmkvManager.decodeServerList(existingGuid)
            if (serverList.isNotEmpty()) {
                MmkvManager.setSelectServer(serverList.first())
            }
        }

        MmkvManager.getSelectServer()?.isNotBlank() == true
    }
}
