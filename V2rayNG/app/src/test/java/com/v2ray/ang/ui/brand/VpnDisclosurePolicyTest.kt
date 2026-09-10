package com.v2ray.ang.ui.brand

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnDisclosurePolicyTest {
    @Test
    fun newAndOutdatedConsentMustPrompt() {
        assertTrue(VpnDisclosurePolicy.shouldPrompt(0))
        assertTrue(VpnDisclosurePolicy.shouldPrompt(VpnDisclosurePolicy.CURRENT_VERSION - 1))
    }

    @Test
    fun currentOrNewerConsentDoesNotPrompt() {
        assertFalse(VpnDisclosurePolicy.shouldPrompt(VpnDisclosurePolicy.CURRENT_VERSION))
        assertFalse(VpnDisclosurePolicy.shouldPrompt(VpnDisclosurePolicy.CURRENT_VERSION + 1))
    }

    @Test
    fun tunnelStartRequiresBothAuthenticationAndDisclosureConsent() {
        assertFalse(VpnDisclosurePolicy.canStartTunnel(isLoggedIn = false, hasAcceptedDisclosure = false))
        assertFalse(VpnDisclosurePolicy.canStartTunnel(isLoggedIn = false, hasAcceptedDisclosure = true))
        assertFalse(VpnDisclosurePolicy.canStartTunnel(isLoggedIn = true, hasAcceptedDisclosure = false))
        assertTrue(VpnDisclosurePolicy.canStartTunnel(isLoggedIn = true, hasAcceptedDisclosure = true))
    }
}
