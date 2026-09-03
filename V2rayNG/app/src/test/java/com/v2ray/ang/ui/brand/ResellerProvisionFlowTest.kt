package com.v2ray.ang.ui.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResellerProvisionFlowTest {

    @Test
    fun customerCreationPromptsOrderAndOrderCreationShowsConnectionDetails() {
        val customer = ResellerCustomer(
            id = "customer-1",
            email = "new-customer@example.com",
            createdAt = "2026-09-03T00:00:00Z",
        )
        val overview = ResellerOverview(
            balanceUsd = 100.0,
            discountPct = 5,
            nextTierMinBalance = 300.0,
            nextTierDiscountPct = 10,
        )
        val prompted = ResellerUiState().afterCustomerCreated(
            customer = customer,
            password = "generated-password",
            refreshedOverview = overview,
            refreshedCustomers = listOf(customer),
            successMessage = "created",
        )

        assertEquals(customer.email, prompted.pendingOrderForEmail)
        assertEquals("generated-password", prompted.pendingCustomerPassword)

        val result = CreateResellerOrderResult(
            orderId = "order-1",
            customerEmail = customer.email,
            generatedPassword = null,
            amountUsd = 3.0,
            planName = "30 days",
            subscriptionUrl = "https://www.hushtunnel.com/api/sub/test-token",
            vlessLink = "vless://test",
            servers = listOf(
                ResellerServerLink(
                    id = "server-1",
                    name = "Moldova",
                    countryCode = "MD",
                    flag = "🇲🇩",
                    city = null,
                    isDefault = true,
                    vlessLink = "vless://test",
                ),
            ),
        )
        val completed = prompted.afterOrderCreated(
            customerEmail = customer.email,
            result = result,
            refreshedOverview = overview,
            refreshedOrders = emptyList(),
            refreshedSubscriptions = emptyList(),
            successMessage = "paid",
            carriedPassword = prompted.pendingCustomerPassword,
            fallbackPlanName = "Subscriptions",
        )

        assertNull(completed.pendingOrderForEmail)
        assertNull(completed.pendingCustomerPassword)
        assertEquals(result.subscriptionUrl, completed.activeConnectionDetails?.subscriptionUrl)
        assertEquals("generated-password", completed.activeConnectionDetails?.generatedPassword)
        assertTrue(completed.activeConnectionDetails?.servers?.isNotEmpty() == true)
    }
}
