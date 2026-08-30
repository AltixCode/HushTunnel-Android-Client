package com.v2ray.ang.ui.brand

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay
import java.util.Locale

class HomeActivity : BaseComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) LauncherManager.startServiceFromToggle(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadPlans()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkVpnState()
    }

    private fun handleConnectToggle(isRunning: Boolean) {
        if (isRunning) {
            LauncherManager.stopService(this)
            viewModel.setVpnRunning(false)
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) {
            LauncherManager.startServiceFromToggle(this)
        } else {
            requestVpnPermission.launch(intent)
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        HomeScreen(
            state = state,
            isLoading = isLoading,
            onRefresh = viewModel::refresh,
            onConnectToggle = { handleConnectToggle(state.isRunning) },
            onSelectSubscription = viewModel::selectSubscription,
            onBuyPlan = { planId, gateway ->
                viewModel.buyPlan(planId, gateway) { checkoutUrl -> Utils.openUri(this, checkoutUrl) }
            },
            onRenewPlan = { subId, planId, gateway ->
                viewModel.renewSubscription(subId, planId, gateway) { checkoutUrl -> Utils.openUri(this, checkoutUrl) }
            },
            onDismissCheckoutMessage = viewModel::dismissCheckoutMessage,
            onLogout = {
                viewModel.logout()
                startActivity(
                    Intent(this@HomeActivity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            },
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onConnectToggle: () -> Unit,
    onSelectSubscription: (String) -> Unit,
    onBuyPlan: (planId: String, gateway: String) -> Unit,
    onRenewPlan: (subId: String, planId: String, gateway: String) -> Unit,
    onDismissCheckoutMessage: () -> Unit,
    onLogout: () -> Unit,
) {
    var checkoutTargetSubId by remember { mutableStateOf<String?>(null) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showOrdersDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val currentLang = LocaleHelper.getCurrentLanguageTag()
    val activeSubs = state.subscriptions.filter { it.isActive }
    val selectedSub = state.subscriptions.firstOrNull { it.id == state.selectedSubscriptionId }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.email,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showLanguageDialog = true }) {
                        Text(
                            LocaleHelper.supportedLanguages.firstOrNull { it.code == currentLang }?.nativeName
                                ?: stringResource(R.string.brand_language)
                        )
                    }

                    TextButton(onClick = { showOrdersDialog = true }) {
                        Text(stringResource(R.string.brand_orders_title))
                    }

                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.brand_refresh))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Prominent Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isRunning) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = if (state.isRunning) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                    shape = CircleShape,
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (state.isRunning) stringResource(R.string.brand_connected) else stringResource(R.string.brand_disconnected),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (state.isRunning) stringResource(R.string.brand_vpn_active_hint) else stringResource(R.string.brand_tap_to_connect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.isRunning) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock_24dp),
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Connect / Disconnect Circle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val buttonColor = if (state.isRunning) Color(0xFFE11D48) else MaterialTheme.colorScheme.primary
                val borderPulseColor = if (state.isRunning) Color(0xFFE11D48).copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

                Box(
                    modifier = Modifier
                        .size(174.dp)
                        .background(borderPulseColor, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = onConnectToggle,
                        enabled = state.hasServer,
                        modifier = Modifier.size(150.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = Color.White,
                        ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_qu_start_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = Color.White,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state.isRunning) stringResource(R.string.brand_disconnect) else stringResource(R.string.brand_connect),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = if (state.isRunning) stringResource(R.string.brand_tap_to_disconnect) else stringResource(R.string.brand_tap_to_connect),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            // Connection Status / Server hint
            if (!state.hasServer) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.brand_no_active_sub),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.brand_no_active_sub_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Messages & Alerts
            state.checkoutMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isPollingOrder) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(text = it, modifier = Modifier.weight(1f))
                    }
                }
                LaunchedEffect(it) {
                    delay(8000)
                    onDismissCheckoutMessage()
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Subscriptions Cards
            if (state.subscriptions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.brand_all_subscriptions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                state.subscriptions.forEach { sub ->
                    val isSelectedTarget = (sub.id == state.selectedSubscriptionId)
                    SubscriptionCard(
                        subscription = sub,
                        isSelectedTarget = isSelectedTarget,
                        onSelectTarget = { onSelectSubscription(sub.id) },
                        onRenew = {
                            checkoutTargetSubId = sub.id
                            showCheckoutDialog = true
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buy a plan button
            Button(
                onClick = {
                    checkoutTargetSubId = null
                    showCheckoutDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (activeSubs.isNotEmpty()) stringResource(R.string.brand_buy_another_plan) else stringResource(R.string.brand_buy_plan)
                )
            }

            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.brand_logout))
            }
        }
    }

    // Checkout / Renew Dialog with Gateway Picker
    if (showCheckoutDialog) {
        CheckoutPlanDialog(
            isRenew = (checkoutTargetSubId != null),
            plans = state.plans,
            gateways = state.gateways,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { selectedPlanId, selectedGateway ->
                showCheckoutDialog = false
                val subId = checkoutTargetSubId
                if (subId != null) {
                    onRenewPlan(subId, selectedPlanId, selectedGateway)
                } else {
                    onBuyPlan(selectedPlanId, selectedGateway)
                }
            },
        )
    }

    // Orders History Dialog
    if (showOrdersDialog) {
        OrdersDialog(
            orders = state.orders,
            onDismiss = { showOrdersDialog = false },
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.brand_change_language)) },
            text = {
                Column {
                    LocaleHelper.supportedLanguages.forEach { lang ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (currentLang == lang.code),
                                onClick = {
                                    showLanguageDialog = false
                                    LocaleHelper.setLanguage(lang.code)
                                },
                            )
                            TextButton(onClick = {
                                showLanguageDialog = false
                                LocaleHelper.setLanguage(lang.code)
                            }) {
                                Text("${lang.nativeName} (${lang.displayName})")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.brand_close))
                }
            },
        )
    }
}

@Composable
fun SubscriptionCard(
    subscription: SubscriptionInfo,
    isSelectedTarget: Boolean,
    onSelectTarget: () -> Unit,
    onRenew: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectedTarget) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = subscription.planName, style = MaterialTheme.typography.titleMedium)
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            if (subscription.isActive) stringResource(R.string.brand_active) else stringResource(R.string.brand_inactive)
                        )
                    },
                )
            }

            Text(
                text = stringResource(R.string.brand_expires_on, subscription.expiryDate.take(10)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            // Traffic Usage Progress Bar
            val totalBytes = subscription.totalBytes
            val usedBytes = subscription.usedBytes
            val progress = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

            val usedGbStr = formatBytes(usedBytes)
            val totalGbStr = if (totalBytes > 0L) formatBytes(totalBytes) else stringResource(R.string.brand_usage_unlimited, "")

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(R.string.brand_data_usage), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (totalBytes > 0L) stringResource(R.string.brand_usage_format, usedGbStr, totalGbStr) else stringResource(R.string.brand_usage_unlimited, usedGbStr),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                if (totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelectedTarget) {
                    Text(
                        text = "✓ " + stringResource(R.string.brand_active_for_vpn),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (subscription.isActive) {
                    OutlinedButton(onClick = onSelectTarget) {
                        Text(stringResource(R.string.brand_set_active_vpn))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(onClick = onRenew) {
                    Text(stringResource(R.string.brand_renew))
                }
            }
        }
    }
}

@Composable
fun CheckoutPlanDialog(
    isRenew: Boolean,
    plans: List<PlanInfo>,
    gateways: GatewayInfo,
    onDismiss: () -> Unit,
    onConfirm: (planId: String, gateway: String) -> Unit,
) {
    var selectedPlanId by remember(plans) { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var selectedGateway by remember { mutableStateOf("MANUAL") }

    val gatewayOptions = buildList {
        if (gateways.revolut) add("REVOLUT" to R.string.brand_gateway_revolut)
        if (gateways.cryptomus) add("CRYPTOMUS" to R.string.brand_gateway_cryptomus)
        if (gateways.nowpayments) add("NOWPAYMENTS" to R.string.brand_gateway_nowpayments)
        add("MANUAL" to R.string.brand_gateway_manual)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isRenew) stringResource(R.string.brand_renew_plan) else stringResource(R.string.brand_choose_plan))
        },
        text = {
            if (plans.isEmpty()) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = stringResource(R.string.brand_choose_plan),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    items(plans) { plan ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (selectedPlanId == plan.id),
                                onClick = { selectedPlanId = plan.id },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(plan.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "$${plan.priceUsd} · ${plan.durationDays} days · " +
                                        if (plan.trafficLimitGb > 0) "${plan.trafficLimitGb} GB" else "Unlimited",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.brand_select_gateway),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    items(gatewayOptions) { (gatewayValue, labelRes) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (selectedGateway == gatewayValue),
                                onClick = { selectedGateway = gatewayValue },
                            )
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPlanId.isNotBlank()) onConfirm(selectedPlanId, selectedGateway)
                },
                enabled = selectedPlanId.isNotBlank(),
            ) {
                Text(stringResource(R.string.brand_continue_payment))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brand_cancel))
            }
        },
    )
}

@Composable
fun OrdersDialog(
    orders: List<OrderItem>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_orders_title)) },
        text = {
            if (orders.isEmpty()) {
                Text(stringResource(R.string.brand_orders_empty))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(orders) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(order.planName, style = MaterialTheme.typography.titleSmall)
                                    Text("$${order.amountUsd}", style = MaterialTheme.typography.titleSmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${order.gateway} · ${order.createdAt.take(10)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        order.status,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (order.status.equals("PAID", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brand_close))
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes.toDouble() / (1024 * 1024)
        String.format(Locale.US, "%.0f MB", mb)
    }
}
