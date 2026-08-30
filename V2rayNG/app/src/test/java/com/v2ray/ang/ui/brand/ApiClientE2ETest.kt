package com.v2ray.ang.ui.brand

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ApiClientE2ETest {

    @Test
    fun testFullUserLifecycleE2E() = runBlocking {
        val uniqueEmail = "test_${UUID.randomUUID().toString().take(8)}@altixcode.com"
        val password = "SecurePassword123!"

        println("1. Testing Registration for: $uniqueEmail")
        val registerResult = ApiClient.register(uniqueEmail, password)
        assertNotNull("Registration token should not be null", registerResult.token)
        assertEquals(uniqueEmail, registerResult.email)
        assertEquals("USER", registerResult.role)
        println("-> Registered successfully! Role: ${registerResult.role}")

        println("2. Testing Login with correct credentials")
        val loginResult = ApiClient.login(uniqueEmail, password)
        assertNotNull("Login token should not be null", loginResult.token)
        assertEquals(uniqueEmail, loginResult.email)
        assertEquals("USER", loginResult.role)
        println("-> Logged in successfully!")

        val token = loginResult.token

        println("3. Testing /api/mobile/me")
        val me = ApiClient.me(token)
        assertEquals(uniqueEmail, me.email)
        assertEquals("USER", me.role)
        println("-> Profile verified! Subscriptions count: ${me.subscriptions.size}")

        println("4. Testing /api/mobile/plans")
        val plans = ApiClient.plans()
        println("-> Fetched ${plans.size} available plans")
        assertTrue("Expected at least one active plan on backend", plans.isNotEmpty())
        val selectedPlan = plans.first()
        println("-> Selected plan: ${selectedPlan.name} ($${selectedPlan.priceUsd}, ${selectedPlan.durationDays}d)")

        println("5. Testing /api/mobile/gateways")
        val gateways = ApiClient.gateways()
        println("-> Gateways: Cryptomus=${gateways.cryptomus}, NOWPayments=${gateways.nowpayments}, Revolut=${gateways.revolut}")

        println("6. Testing /api/mobile/checkout (Buy Plan)")
        val checkout = ApiClient.checkout(token, selectedPlan.id, gateway = "MANUAL")
        assertNotNull("Order ID should not be null", checkout.orderId)
        println("-> Order created successfully with ID: ${checkout.orderId}")

        println("7. Testing /api/mobile/orders/:id polling")
        val orderStatus = ApiClient.orderStatus(token, checkout.orderId)
        assertEquals(checkout.orderId, orderStatus.id)
        println("-> Order status: ${orderStatus.status}")

        println("8. Testing Invalid Login (wrong password)")
        try {
            ApiClient.login(uniqueEmail, "WrongPassword999!")
            fail("Expected ApiException for wrong password")
        } catch (e: ApiException) {
            println("-> Caught expected error for invalid password: ${e.message} (HTTP ${e.statusCode})")
            assertEquals(401, e.statusCode)
        }

        println("9. Testing Duplicate Registration")
        try {
            ApiClient.register(uniqueEmail, password)
            fail("Expected ApiException for duplicate email")
        } catch (e: ApiException) {
            println("-> Caught expected error for duplicate email: ${e.message} (HTTP ${e.statusCode})")
            assertTrue(e.statusCode >= 400)
        }

        println("10. Full E2E User Flow Passed 100%!")
    }
}
