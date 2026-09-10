package com.v2ray.ang.ui.brand

import android.app.Activity
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils

object VpnDisclosurePolicy {
    const val CURRENT_VERSION = 1

    fun shouldPrompt(acceptedVersion: Int): Boolean = acceptedVersion < CURRENT_VERSION

    fun canStartTunnel(isLoggedIn: Boolean, hasAcceptedDisclosure: Boolean): Boolean =
        isLoggedIn && hasAcceptedDisclosure
}

object VpnDisclosureStore {
    private const val ID = "BRAND_VPN_DISCLOSURE"
    private val storage by lazy { MMKV.mmkvWithID(ID, MMKV.MULTI_PROCESS_MODE) }

    private fun key(userId: String) = "accepted_version_$userId"

    fun isAccepted(userId: String? = AuthStore.getUserId()): Boolean {
        if (userId.isNullOrBlank()) return false
        return !VpnDisclosurePolicy.shouldPrompt(storage.decodeInt(key(userId), 0))
    }

    fun accept(userId: String? = AuthStore.getUserId()) {
        if (!userId.isNullOrBlank()) storage.encode(key(userId), VpnDisclosurePolicy.CURRENT_VERSION)
    }

    fun revoke(userId: String? = AuthStore.getUserId()) {
        if (!userId.isNullOrBlank()) storage.removeValueForKey(key(userId))
    }
}

class VpnDisclosureViewModel : ViewModel() {
    fun accept(onAccepted: () -> Unit) {
        VpnDisclosureStore.accept()
        onAccepted()
    }

    fun decline(onDeclined: () -> Unit) {
        VpnDisclosureStore.revoke()
        onDeclined()
    }
}

class VpnDisclosureActivity : BaseComponentActivity() {
    private val viewModel: VpnDisclosureViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        VpnDisclosureScreen(
            onPrivacy = { Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY) },
            onTerms = { Utils.openUri(this, AppConfig.APP_TERMS) },
            onAccept = { viewModel.accept(::openAuthenticatedHome) },
            onDecline = {
                viewModel.decline {
                    AuthStore.clear()
                    startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
            },
        )
    }

    private fun openAuthenticatedHome() {
        val destination = homeActivityForRole(AuthStore.getRole())
        startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        fun destinationForAuthenticatedRole(role: String?): Class<out Activity> {
            return if (VpnDisclosureStore.isAccepted()) homeActivityForRole(role) else VpnDisclosureActivity::class.java
        }

        private fun homeActivityForRole(role: String?): Class<out Activity> {
            return if (role.equals("RESELLER", ignoreCase = true)) ResellerHomeActivity::class.java else HomeActivity::class.java
        }
    }
}

@Composable
fun VpnDisclosureScreen(
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.brand_vpn_disclosure_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.brand_vpn_disclosure_intro), style = MaterialTheme.typography.bodyLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.brand_vpn_disclosure_core), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.brand_vpn_disclosure_processing), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.brand_vpn_disclosure_data), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.brand_vpn_disclosure_monetization), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.brand_vpn_disclosure_choice), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPrivacy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.brand_privacy_policy)) }
                TextButton(onClick = onTerms, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.brand_terms_conditions)) }
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.brand_vpn_disclosure_accept)) }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.brand_vpn_disclosure_decline)) }
        }
    }
}
