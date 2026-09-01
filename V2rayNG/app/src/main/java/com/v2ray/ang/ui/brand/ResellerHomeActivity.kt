package com.v2ray.ang.ui.brand

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.horizontalScroll

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay
import java.util.Locale

class ResellerHomeActivity : BaseComponentActivity() {

    private val viewModel: ResellerHomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkVpnState()
        viewModel.refresh()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        ResellerHomeScreen(
            state = state,
            isLoading = isLoading,
            onRefresh = viewModel::refresh,
            onSetTab = viewModel::setTab,
            onOpenVpnClient = {
                startActivity(Intent(this@ResellerHomeActivity, HomeActivity::class.java))
            },
            onBuyPersonalPlan = viewModel::buyPersonalSubscription,
            onRenewPersonalPlan = viewModel::renewPersonalSubscription,
            onCreateCustomer = { email, pwd ->
                viewModel.createCustomer(email, pwd) { em, pw ->
                    // Handled via state or message
                }
            },
            onCreateOrder = viewModel::createOrderForCustomer,
            onCreateDeposit = { _, _ ->
                Utils.openUri(this, "https://www.hushtunnel.com")
            },
            onCreateSubReseller = viewModel::createSubReseller,
            onExtendSub = viewModel::extendSubscription,
            onToggleSub = viewModel::toggleSubscription,
            onResetSubUuid = viewModel::resetSubscriptionUuid,
            onResetSubTraffic = viewModel::resetSubscriptionTraffic,
            onRevokeSub = viewModel::revokeSubscription,
            onUpdateCustomerPassword = viewModel::updateCustomerPassword,
            onResetCustomerPassword = viewModel::resetCustomerPassword,
            onDeleteCustomer = viewModel::deleteCustomer,
            onChangePassword = { currentPwd, newPwd ->
                viewModel.changePassword(
                    currentPwd,
                    newPwd,
                    onSuccess = { viewModel.refresh() },
                    onError = { }
                )
            },
            onDismissMessage = viewModel::dismissMessage,
            onLogout = {
                viewModel.logout()
                startActivity(
                    Intent(this@ResellerHomeActivity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            },
        )
    }
}

@Composable
fun ResellerHomeScreen(
    state: ResellerUiState,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSetTab: (Int) -> Unit,
    onOpenVpnClient: () -> Unit,
    onBuyPersonalPlan: (String) -> Unit,
    onRenewPersonalPlan: (String, String) -> Unit,
    onCreateCustomer: (String, String?) -> Unit,
    onCreateOrder: (customerEmail: String, planId: String) -> Unit,
    onCreateDeposit: (amount: Double, gateway: String) -> Unit,
    onCreateSubReseller: (email: String, initialBalanceUsd: Double) -> Unit,
    onExtendSub: (String) -> Unit,
    onToggleSub: (String, Boolean) -> Unit,
    onResetSubUuid: (String) -> Unit,
    onResetSubTraffic: (String) -> Unit,
    onRevokeSub: (String) -> Unit,
    onUpdateCustomerPassword: (String, String) -> Unit,
    onResetCustomerPassword: (String) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onChangePassword: (String?, String) -> Unit,
    onDismissMessage: () -> Unit,
    onLogout: () -> Unit,
) {
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showAddOrderDialog by remember { mutableStateOf(false) }
    var prefilledOrderEmail by remember { mutableStateOf("") }
    var showAddDepositDialog by remember { mutableStateOf(false) }
    var showAddSubResellerDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showBuyPersonalDialog by remember { mutableStateOf(false) }
    var renewPersonalSubId by remember { mutableStateOf<String?>(null) }
    var selectedCustomerForDetail by remember { mutableStateOf<ResellerCustomer?>(null) }

    val currentLang = LocaleHelper.getCurrentLanguageTag()
    val tabs = listOf(
        stringResource(R.string.brand_reseller_tab_overview),
        stringResource(R.string.brand_reseller_tab_customers),
        stringResource(R.string.brand_reseller_tab_subscriptions),
        stringResource(R.string.brand_reseller_tab_orders),
        stringResource(R.string.brand_reseller_tab_deposits),
        stringResource(R.string.brand_reseller_tab_subresellers),
    )

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val responsivePadding = if (config.screenWidthDp > 640) ((config.screenWidthDp - 640) / 2).dp else 16.dp

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .padding(horizontal = responsivePadding, vertical = 16.dp),
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showLanguageDialog = true },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = "🌐 " + (LocaleHelper.supportedLanguages.firstOrNull { it.code == currentLang }?.nativeName
                            ?: stringResource(R.string.brand_language)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "🔄 " + stringResource(R.string.brand_refresh),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    TextButton(onClick = onLogout) {
                        Text(
                            text = stringResource(R.string.brand_logout),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onOpenVpnClient,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "🛡️ " + stringResource(R.string.brand_open_vpn_dashboard),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reseller Balance & Account Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.brand_reseller_portal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.brand_balance_discount_format, state.overview.balanceUsd, state.overview.discountPct),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showChangePasswordDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.brand_change_password), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Modern Responsive Tab Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(tabs) { index, title ->
                    val isSelected = (state.selectedTab == index)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSetTab(index) },
                        label = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications
            state.message?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(text = it, modifier = Modifier.padding(12.dp))
                }
                LaunchedEffect(it) {
                    delay(10000)
                    onDismissMessage()
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Tab Content
            when (state.selectedTab) {
                0 -> ResellerOverviewTab(
                    overview = state.overview,
                    personalSubscriptions = state.personalSubscriptions,
                    isRunning = state.isRunning,
                    onOpenVpnClient = onOpenVpnClient,
                    onOpenBuyPersonal = {
                        renewPersonalSubId = null
                        showBuyPersonalDialog = true
                    },
                    onOpenRenewPersonal = { subId ->
                        renewPersonalSubId = subId
                        showBuyPersonalDialog = true
                    },
                    onOpenAddCustomer = { showAddCustomerDialog = true },
                    onOpenAddReseller = { showAddSubResellerDialog = true },
                    onOpenAddOrder = { showAddOrderDialog = true },
                    onOpenAddDeposit = { showAddDepositDialog = true },
                )
                1 -> ResellerCustomersTab(
                    customers = state.customers,
                    onAddCustomerClick = { showAddCustomerDialog = true },
                    onCustomerClick = { customer -> selectedCustomerForDetail = customer },
                )
                2 -> ResellerSubscriptionsTab(
                    subscriptions = state.subscriptions,
                    onExtend = onExtendSub,
                    onToggle = onToggleSub,
                    onResetUuid = onResetSubUuid,
                    onResetTraffic = onResetSubTraffic,
                    onRevoke = onRevokeSub,
                )
                3 -> ResellerOrdersTab(
                    orders = state.orders,
                    onNewOrderClick = { showAddOrderDialog = true },
                )
                4 -> ResellerTransactionsTab(
                    transactions = state.transactions,
                    currentBalanceUsd = state.overview.balanceUsd,
                )
                5 -> ResellerSubResellersTab(
                    subResellers = state.subResellers,
                    onAddSubResellerClick = { showAddSubResellerDialog = true },
                )
            }
        }
    }


    if (showBuyPersonalDialog) {
        BuyPersonalPlanDialog(
            plans = state.plans,
            balanceUsd = state.overview.balanceUsd,
            discountPct = state.overview.discountPct,
            isRenew = (renewPersonalSubId != null),
            onDismiss = { showBuyPersonalDialog = false },
            onConfirm = { planId ->
                showBuyPersonalDialog = false
                val targetSubId = renewPersonalSubId
                if (targetSubId != null) {
                    onRenewPersonalPlan(targetSubId, planId)
                } else {
                    onBuyPersonalPlan(planId)
                }
            },
        )
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { email, pwd ->
                showAddCustomerDialog = false
                onCreateCustomer(email, pwd)
            },
        )
    }

    if (showAddOrderDialog) {
        AddResellerOrderDialog(
            plans = state.plans,
            customers = state.customers,
            initialEmail = prefilledOrderEmail,
            onDismiss = {
                showAddOrderDialog = false
                prefilledOrderEmail = ""
            },
            onConfirm = { email, planId ->
                showAddOrderDialog = false
                onCreateOrder(email, planId)
            },
        )
    }

    if (showAddDepositDialog) {
        AddDepositDialog(
            gateways = state.gateways,
            onDismiss = { showAddDepositDialog = false },
            onConfirm = { amount, gateway ->
                showAddDepositDialog = false
                onCreateDeposit(amount, gateway)
            },
        )
    }

    if (showAddSubResellerDialog) {
        AddSubResellerDialog(
            balanceUsd = state.overview.balanceUsd,
            onDismiss = { showAddSubResellerDialog = false },
            onConfirm = { email, initialBalance ->
                showAddSubResellerDialog = false
                onCreateSubReseller(email, initialBalance)
            },
        )
    }

    selectedCustomerForDetail?.let { customer ->
        CustomerDetailDialog(
            customer = customer,
            onDismiss = { selectedCustomerForDetail = null },
            onChangePassword = { newPassword -> onUpdateCustomerPassword(customer.id, newPassword) },
            onResetPassword = { onResetCustomerPassword(customer.id) },
            onDelete = {
                onDeleteCustomer(customer.id)
                selectedCustomerForDetail = null
            },
        )
    }

    if (showChangePasswordDialog) {
        ResellerChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { currentPwd, newPwd ->
                showChangePasswordDialog = false
                onChangePassword(currentPwd, newPwd)
            },
        )
    }

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
fun ResellerOverviewTab(
    overview: ResellerOverview,
    personalSubscriptions: List<SubscriptionInfo>,
    isRunning: Boolean = false,
    onOpenVpnClient: () -> Unit,
    onOpenBuyPersonal: () -> Unit,
    onOpenRenewPersonal: (String) -> Unit,
    onOpenAddCustomer: () -> Unit,
    onOpenAddReseller: () -> Unit,
    onOpenAddOrder: () -> Unit,
    onOpenAddDeposit: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestSub = personalSubscriptions.firstOrNull()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Prominent Personal VPN Connection Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🛡️ " + stringResource(R.string.brand_my_personal_vpn),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (latestSub != null) {
                            SuggestionChip(
                                onClick = onOpenVpnClient,
                                label = { 
                                    Text(
                                        text = if (isRunning) "🟢 " + stringResource(R.string.brand_connected) else "⚪ " + stringResource(R.string.brand_active), 
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (latestSub != null) {
                        Text(
                            text = stringResource(R.string.brand_plan_name_label, latestSub.planName),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.brand_expires_on, latestSub.expiryDate.take(10)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onOpenVpnClient,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (isRunning) "🛡️ " + stringResource(R.string.brand_open_vpn_dashboard) else stringResource(R.string.brand_connect))
                            }
                            OutlinedButton(
                                onClick = { onOpenRenewPersonal(latestSub.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.brand_renew))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.brand_personal_vpn_desc, overview.discountPct),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        Button(
                            onClick = onOpenBuyPersonal,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("🛒 " + stringResource(R.string.brand_buy_personal_vpn))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.brand_reseller_balance), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = stringResource(R.string.brand_price_usd_double, overview.balanceUsd),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.brand_reseller_discount)}: ${overview.discountPct}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    val nextTierMin = overview.nextTierMinBalance
                    val nextTierPct = overview.nextTierDiscountPct
                    if (nextTierMin != null && nextTierPct != null) {
                        Text(
                            text = stringResource(R.string.brand_reseller_next_tier, nextTierMin, nextTierPct),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onOpenAddOrder, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.brand_reseller_buy_for_customer))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onOpenAddCustomer, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.brand_reseller_add_customer))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onOpenAddReseller, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.brand_reseller_add_subreseller))
            }

            Spacer(modifier = Modifier.height(8.dp))


        }
    }
}

@Composable
fun ResellerCustomersTab(
    customers: List<ResellerCustomer>,
    onAddCustomerClick: () -> Unit,
    onCustomerClick: (ResellerCustomer) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter { it.email.contains(searchQuery.trim(), ignoreCase = true) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.brand_search_customers)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        item {
            Button(onClick = onAddCustomerClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(stringResource(R.string.brand_reseller_add_customer))
            }
        }

        if (customers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.brand_no_customers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered) { c ->
                Card(
                    onClick = { onCustomerClick(c) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = c.email, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.brand_created_date, c.createdAt.take(10)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResellerSubscriptionsTab(
    subscriptions: List<ResellerSubscription>,
    onExtend: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onResetUuid: (String) -> Unit,
    onResetTraffic: (String) -> Unit,
    onRevoke: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(subscriptions, searchQuery) {
        if (searchQuery.isBlank()) subscriptions
        else subscriptions.filter {
            it.customerEmail.contains(searchQuery.trim(), ignoreCase = true) ||
            it.planName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.brand_search_subscriptions)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (subscriptions.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.brand_no_subscriptions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(subscriptions) { sub ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = sub.customerEmail, style = MaterialTheme.typography.titleSmall)
                            SuggestionChip(
                                onClick = {},
                                label = { Text(if (sub.isActive) stringResource(R.string.brand_active) else stringResource(R.string.brand_inactive)) },
                            )
                        }

                        Text(
                            text = stringResource(R.string.brand_sub_expires_format, sub.planName, sub.expiryDate.take(10)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )

                        val totalBytes = sub.totalBytes
                        val usedBytes = sub.usedBytes
                        val progress = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        if (totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(4.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = { onExtend(sub.id) }) {
                                Text(stringResource(R.string.brand_reseller_extend))
                            }
                            TextButton(onClick = { onToggle(sub.id, !sub.isActive) }) {
                                Text(stringResource(R.string.brand_reseller_toggle))
                            }
                            TextButton(onClick = { onResetUuid(sub.id) }) {
                                Text(stringResource(R.string.brand_reseller_reset_uuid))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResellerOrdersTab(
    orders: List<ResellerOrder>,
    onNewOrderClick: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(orders, searchQuery) {
        if (searchQuery.isBlank()) orders
        else orders.filter {
            it.customerEmail.contains(searchQuery.trim(), ignoreCase = true) ||
            it.planName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.brand_search_orders)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        item {
            Button(onClick = onNewOrderClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(stringResource(R.string.brand_reseller_buy_for_customer))
            }
        }

        if (orders.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.brand_no_orders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered) { o ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = o.customerEmail, style = MaterialTheme.typography.titleSmall)
                            Text(text = stringResource(R.string.brand_price_usd_format, o.amountUsd.toString()), style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = "${o.planName} · ${o.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall)
                            Text(text = o.status, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResellerSubResellersTab(
    subResellers: List<SubResellerItem>,
    onAddSubResellerClick: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Button(onClick = onAddSubResellerClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(stringResource(R.string.brand_reseller_add_subreseller))
            }
        }

        if (subResellers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.brand_reseller_no_subresellers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(subResellers) { r ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = r.email, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.brand_price_usd_double, r.balanceUsd),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.brand_reseller_subreseller_stats,
                                r.customerCount,
                                r.subscriptionCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddSubResellerDialog(
    balanceUsd: Double,
    onDismiss: () -> Unit,
    onConfirm: (email: String, initialBalanceUsd: Double) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var initialBalanceStr by remember { mutableStateOf("0") }
    val initialBalance = initialBalanceStr.toDoubleOrNull() ?: 0.0
    val overBudget = initialBalance > balanceUsd

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_reseller_add_subreseller)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.brand_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text(stringResource(R.string.brand_reseller_subreseller_initial_balance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.brand_reseller_balance) + ": " + stringResource(R.string.brand_price_usd_double, balanceUsd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (overBudget) {
                    Text(
                        text = stringResource(R.string.brand_error_insufficient_balance),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (email.isNotBlank() && !overBudget) onConfirm(email.trim(), initialBalance) },
                enabled = email.isNotBlank() && !overBudget,
            ) {
                Text(stringResource(R.string.brand_reseller_add_subreseller))
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
fun ResellerTransactionsTab(
    transactions: List<WalletTransactionItem>,
    currentBalanceUsd: Double,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            (it.description ?: "").contains(searchQuery.trim(), ignoreCase = true) ||
            it.type.contains(searchQuery.trim(), ignoreCase = true) ||
            (it.counterpartEmail ?: "").contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.brand_reseller_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.brand_price_usd_double, currentBalanceUsd),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.brand_search_transactions)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.brand_no_transactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered) { tx ->
                val isCredit = tx.type in listOf("TRANSFER_IN", "DEPOSIT") || (tx.amountUsd > 0 && tx.balanceAfter > tx.balanceBefore)
                val sign = if (isCredit) "+" else "-"
                val amountColor = if (isCredit) androidx.compose.ui.graphics.Color(0xFF10B981) else MaterialTheme.colorScheme.error

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tx.type.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "$sign " + stringResource(R.string.brand_price_usd_double, tx.amountUsd),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                            )
                        }

                        if (!tx.description.isNullOrBlank()) {
                            Text(
                                text = tx.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        if (!tx.counterpartEmail.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.brand_counterpart, tx.counterpartEmail ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.brand_transfer_balance_change, tx.balanceBefore, tx.balanceAfter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = tx.createdAt.take(16).replace("T", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, password: String?) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var isCustomPassword by remember { mutableStateOf(false) }
    var customPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_reseller_add_customer)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.brand_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isCustomPassword) {
                        Button(
                            onClick = { isCustomPassword = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.brand_auto_generate))
                        }
                        OutlinedButton(
                            onClick = { isCustomPassword = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.brand_custom))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isCustomPassword = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.brand_auto_generate))
                        }
                        Button(
                            onClick = { isCustomPassword = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.brand_custom))
                        }
                    }
                }

                if (isCustomPassword) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPassword,
                        onValueChange = { customPassword = it },
                        label = { Text(stringResource(R.string.brand_password_min_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        val pwd = if (isCustomPassword && customPassword.length >= 6) customPassword else null
                        onConfirm(email.trim(), pwd)
                    }
                },
                enabled = email.isNotBlank() && (!isCustomPassword || customPassword.length >= 6),
            ) {
                Text(stringResource(R.string.brand_reseller_add_customer))
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
fun AddResellerOrderDialog(
    plans: List<PlanInfo>,
    customers: List<ResellerCustomer>,
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onConfirm: (customerEmail: String, planId: String) -> Unit,
) {
    var email by remember(initialEmail, customers) {
        mutableStateOf(if (initialEmail.isNotBlank()) initialEmail else customers.firstOrNull()?.email.orEmpty())
    }
    var selectedPlanId by remember(plans) { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_reseller_buy_for_customer)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.brand_reseller_customer_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(R.string.brand_choose_plan), style = MaterialTheme.typography.titleSmall)

                LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    items(plans) { plan ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (selectedPlanId == plan.id),
                                onClick = { selectedPlanId = plan.id },
                            )
                            Text(
                                text = stringResource(R.string.brand_plan_price_parenthesis, plan.name, plan.priceUsd),
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
                onClick = { if (email.isNotBlank() && selectedPlanId.isNotBlank()) onConfirm(email.trim(), selectedPlanId) },
                enabled = email.isNotBlank() && selectedPlanId.isNotBlank(),
            ) {
                Text(stringResource(R.string.brand_reseller_buy_for_customer))
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
fun AddDepositDialog(
    gateways: GatewayInfo,
    onDismiss: () -> Unit,
    onConfirm: (amountUsd: Double, gateway: String) -> Unit,
) {
    var amountStr by remember { mutableStateOf("50") }
    var selectedGateway by remember { mutableStateOf("MANUAL") }

    val gatewayOptions = buildList {
        if (gateways.revolut) add("REVOLUT" to R.string.brand_gateway_revolut)
        if (gateways.cryptomus) add("CRYPTOMUS" to R.string.brand_gateway_cryptomus)
        if (gateways.nowpayments) add("NOWPAYMENTS" to R.string.brand_gateway_nowpayments)
        add("MANUAL" to R.string.brand_gateway_manual)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_reseller_add_deposit)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(stringResource(R.string.brand_reseller_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(R.string.brand_select_gateway), style = MaterialTheme.typography.titleSmall)

                gatewayOptions.forEach { (gatewayValue, labelRes) ->
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
        },
        confirmButton = {
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { if (amount > 0.0) onConfirm(amount, selectedGateway) },
                enabled = amount > 0.0,
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
fun ResellerChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text(stringResource(R.string.brand_current_password)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.brand_new_password)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.brand_confirm_password)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.length < 6) {
                        error = context.getString(R.string.brand_password_min_length)
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        error = context.getString(R.string.brand_password_mismatch)
                        return@Button
                    }
                    onConfirm(if (currentPassword.isBlank()) null else currentPassword, newPassword)
                }
            ) {
                Text(stringResource(R.string.brand_change_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brand_cancel))
            }
        }
    )
}

@Composable
fun CustomerDetailDialog(
    customer: ResellerCustomer,
    onDismiss: () -> Unit,
    onChangePassword: (String) -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var showPasswordField by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ResellerCustomerDetail?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(customer.id) {
        val token = AuthStore.getToken() ?: return@LaunchedEffect
        detail = try {
            ApiClient.resellerCustomerDetails(token, customer.id)
        } catch (_: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(customer.email) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.brand_created_date, customer.createdAt.take(10)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val subscriptions = detail?.subscriptions
                if (subscriptions != null) {
                    if (subscriptions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.brand_reseller_no_subscriptions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            subscriptions.forEach { sub ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = sub.planName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(R.string.brand_expires_on, sub.expiryDate.take(10)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (sub.isActive && sub.subscriptionUrl.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedButton(
                                                    onClick = { qrCodeBitmap = QRCodeDecoder.createQRCode(sub.subscriptionUrl) },
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.brand_view_connection),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        Utils.setClipboard(context, sub.subscriptionUrl)
                                                        context.toast(R.string.brand_copied_to_clipboard)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.brand_copy_sub_link),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showPasswordField) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.brand_new_password)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            if (newPassword.length >= 6) {
                                onChangePassword(newPassword)
                            }
                        },
                        enabled = newPassword.length >= 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.brand_save_new_password))
                    }
                } else {
                    Button(
                        onClick = { showPasswordField = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.brand_change_password))
                    }
                }

                OutlinedButton(
                    onClick = onResetPassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.brand_auto_gen_password))
                }

                OutlinedButton(
                    onClick = onDelete,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.brand_delete_customer))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brand_cancel))
            }
        }
    )

    QRCodeDialog(bitmap = qrCodeBitmap, onDismiss = { qrCodeBitmap = null })
}


@Composable
fun BuyPersonalPlanDialog(
    plans: List<PlanInfo>,
    balanceUsd: Double,
    discountPct: Int,
    isRenew: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id ?: "") }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()
    val rawPrice = selectedPlan?.priceUsd ?: 0.0
    val discountedPrice = Math.round(rawPrice * (1 - discountPct / 100.0) * 100.0) / 100.0
    val canAfford = (balanceUsd >= discountedPrice)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isRenew) stringResource(R.string.brand_renew_personal_vpn) else stringResource(R.string.brand_buy_personal_vpn))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.brand_reseller_balance_applied, balanceUsd, discountPct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                plans.forEach { plan ->
                    val isSelected = (plan.id == selectedPlanId)
                    val itemDiscounted = Math.round(plan.priceUsd * (1 - discountPct / 100.0) * 100.0) / 100.0
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        onClick = { selectedPlanId = plan.id },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = plan.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.brand_days, plan.durationDays) + " · " + (if (plan.trafficLimitGb > 0) "${plan.trafficLimitGb} GB" else stringResource(R.string.brand_unlimited)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.brand_price_usd_double, itemDiscounted),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (itemDiscounted < plan.priceUsd) {
                                    Text(
                                        text = stringResource(R.string.brand_price_usd_double, plan.priceUsd),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!canAfford) {
                    Text(
                        text = "Insufficient balance. Please deposit funds via web dashboard.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedPlan != null) onConfirm(selectedPlan.id) },
                enabled = canAfford && selectedPlan != null,
            ) {
                Text(stringResource(R.string.brand_confirm_with_price, discountedPrice))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brand_cancel))
            }
        },
    )
}
