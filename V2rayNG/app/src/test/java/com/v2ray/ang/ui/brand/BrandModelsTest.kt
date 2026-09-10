package com.v2ray.ang.ui.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandModelsTest {

    @Test
    fun testPlanInfoInstantiation() {
        val plan = PlanInfo(
            id = "plan-1",
            name = "1 Month Premium",
            description = "High speed access",
            priceUsd = 9.99,
            durationDays = 30,
            trafficLimitGb = 100,
        )

        assertEquals("plan-1", plan.id)
        assertEquals("1 Month Premium", plan.name)
        assertEquals(9.99, plan.priceUsd, 0.001)
        assertEquals(30, plan.durationDays)
        assertEquals(100, plan.trafficLimitGb)
    }

    @Test
    fun testSubscriptionInfo() {
        val sub = SubscriptionInfo(
            id = "sub-123",
            planName = "Basic 30d",
            expiryDate = "2026-12-31T23:59:59Z",
            isActive = true,
            usedBytes = 1024L * 1024L * 500L,
            totalBytes = 1024L * 1024L * 1024L * 10L,
            subscriptionUrl = "https://vpn-billing-dashboard.vercel.app/api/sub/token123",
        )

        assertEquals("sub-123", sub.id)
        assertTrue(sub.isActive)
        assertEquals(1024L * 1024L * 500L, sub.usedBytes)
        assertEquals(1024L * 1024L * 1024L * 10L, sub.totalBytes)
    }

    @Test
    fun testMeResultRoleParsing() {
        val userMe = MeResult(
            userId = "user-1",
            email = "user@example.com",
            role = "USER",
            subscriptions = listOf(),
        )
        assertEquals("USER", userMe.role)

        val resellerMe = MeResult(
            userId = "reseller-1",
            email = "reseller@example.com",
            role = "RESELLER",
            subscriptions = listOf(),
        )
        assertEquals("RESELLER", resellerMe.role)
    }

    @Test
    fun storeProductsKeepSubscriptionsAndWalletFundingDistinct() {
        val config = IapConfig(
            enabled = true,
            publicSdkKey = "goog_test",
            appUserId = "user-1",
            entitlementId = "hushtunnel_access",
            subscriptionProducts = listOf(IapSubscriptionProduct("hushtunnel_1_month", 30)),
            walletProducts = listOf(IapWalletProduct("hushtunnel_funds_10", 10.0)),
        )
        assertEquals(30, config.subscriptionProducts.single().durationDays)
        assertEquals(10.0, config.walletProducts.single().amountUsd, 0.001)
        assertEquals("user-1", config.appUserId)
    }

    @Test
    fun testResellerOverviewTiers() {
        val overview = ResellerOverview(
            balanceUsd = 350.0,
            discountPct = 10,
            nextTierMinBalance = 1000.0,
            nextTierDiscountPct = 15,
        )

        assertEquals(350.0, overview.balanceUsd, 0.001)
        assertEquals(10, overview.discountPct)
        assertEquals(1000.0, overview.nextTierMinBalance!!, 0.001)
        assertEquals(15, overview.nextTierDiscountPct!!)
    }

    @Test
    fun testGatewayInfo() {
        val gw = GatewayInfo(cryptomus = true, nowpayments = false, revolut = true)
        assertTrue(gw.cryptomus)
        assertFalse(gw.nowpayments)
        assertTrue(gw.revolut)
    }

    @Test
    fun testSupportedLanguages() {
        val langs = LocaleHelper.supportedLanguages
        assertEquals(5, langs.size)
        assertTrue(langs.any { it.code == "en" })
        assertTrue(langs.any { it.code == "fa" })
        assertTrue(langs.any { it.code == "ru" })
        assertTrue(langs.any { it.code == "zh-CN" })
        assertTrue(langs.any { it.code == "tr" })
    }

    @Test
    fun selectedServerSurvivesRefreshAndFallsBackSafely() {
        val servers = listOf(
            ServerNode(id = "md", name = "Moldova", host = "143.246.213.17", isDefault = true),
            ServerNode(id = "nl", name = "Netherlands", host = "5.255.125.216"),
        )

        assertEquals("nl", resolveSelectedServerId(servers, currentServerId = "nl", savedServerId = "md"))
        assertEquals("nl", resolveSelectedServerId(servers, currentServerId = null, savedServerId = "nl"))
        assertEquals("md", resolveSelectedServerId(servers, currentServerId = "removed", savedServerId = "removed"))
    }
}
