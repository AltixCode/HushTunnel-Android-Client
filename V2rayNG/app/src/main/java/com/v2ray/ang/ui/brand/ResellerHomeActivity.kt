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
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
            onCreateCustomer = viewModel::createCustomer,
            onCreateOrder = viewModel::createOrderForCustomer,
            onOpenConnectionDetails = viewModel::openConnectionDetails,
            onDismissConnectionDetails = viewModel::dismissConnectionDetails,
            onDismissPendingOrder = viewModel::dismissPendingOrder,
            onCreateDeposit = { _, _ ->
                Utils.openUri(this, "https://www.hushtunnel.com")
            },
            onCreateSubReseller = viewModel::createSubReseller,
            onTransferFunds = viewModel::transferFunds,
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
    onOpenConnectionDetails: (ResellerConnectionDetails) -> Unit,
    onDismissConnectionDetails: () -> Unit,
    onDismissPendingOrder: () -> Unit,
    onCreateDeposit: (amount: Double, gateway: String) -> Unit,
    onCreateSubReseller: (email: String, initialBalanceUsd: Double) -> Unit,
    onTransferFunds: (recipientEmail: String, amountUsd: Double, description: String?, onSuccess: (() -> Unit)?) -> Unit,
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
    var showTransferFundsDialog by remember { mutableStateOf(false) }
    var transferInitialEmail by remember { mutableStateOf("") }
    var transferLockRecipient by remember { mutableStateOf(false) }
    var selectedSubResellerForDetail by remember { mutableStateOf<SubResellerItem?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showBuyPersonalDialog by remember { mutableStateOf(false) }
    var renewPersonalSubId by remember { mutableStateOf<String?>(null) }
    var selectedCustomerForDetail by remember { mutableStateOf<ResellerCustomer?>(null) }
    var activeConnectionDetails by remember { mutableStateOf<ConnectionDialogData?>(null) }

    val currentLang = LocaleHelper.getCurrentLanguageTag()
    val tabs = listOf(
        stringResource(R.string.brand_reseller_tab_overview),
        stringResource(R.string.brand_reseller_tab_customers),
        stringResource(R.string.brand_reseller_tab_subscriptions),
        stringResource(R.string.brand_reseller_tab_orders),
        stringResource(R.string.brand_reseller_tab_deposits),
        stringResource(R.string.brand_reseller_tab_subresellers),
    )

    val context = androidx.compose.ui.platform.LocalContext.current
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
                    isLoading = isLoading,
                    onSubClick = { sub ->
                        onOpenConnectionDetails(
                            ResellerConnectionDetails(
                                title = sub.customerEmail,
                                planName = sub.planName,
                                subscriptionUrl = sub.subscriptionUrl,
                                vlessLink = sub.vlessLink,
                                expiryDate = sub.expiryDate,
                                status = if (sub.isActive) "ACTIVE" else "INACTIVE",
                                servers = sub.servers,
                            )
                        )
                    },
                )
                3 -> ResellerOrdersTab(
                    orders = state.orders,
                    onNewOrderClick = { showAddOrderDialog = true },
                    onOrderClick = { order ->
                        onOpenConnectionDetails(
                            ResellerConnectionDetails(
                                title = order.customerEmail,
                                planName = order.planName,
                                subscriptionUrl = order.subscriptionUrl,
                                vlessLink = order.vlessLink,
                                amountUsd = order.amountUsd,
                                status = order.status,
                                servers = order.servers,
                            )
                        )
                    },
                )
                4 -> ResellerTransactionsTab(
                    transactions = state.transactions,
                    currentBalanceUsd = state.overview.balanceUsd,
                    onTransferFundsClick = {
                        transferInitialEmail = ""
                        transferLockRecipient = false
                        showTransferFundsDialog = true
                    },
                )
                5 -> ResellerSubResellersTab(
                    subResellers = state.subResellers,
                    onAddSubResellerClick = { showAddSubResellerDialog = true },
                    onSubResellerClick = { subReseller -> selectedSubResellerForDetail = subReseller },
                    onAddFundsClick = { subReseller ->
                        transferInitialEmail = subReseller.email
                        transferLockRecipient = true
                        showTransferFundsDialog = true
                    },
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
                onCreateCustomer(email.trim(), pwd)
            },
        )
    }

    val isOrderDialogOpen = showAddOrderDialog || !state.pendingOrderForEmail.isNullOrBlank()
    val orderDialogEmail = if (!state.pendingOrderForEmail.isNullOrBlank()) state.pendingOrderForEmail else prefilledOrderEmail

    if (isOrderDialogOpen) {
        AddResellerOrderDialog(
            plans = state.plans,
            customers = state.customers,
            initialEmail = orderDialogEmail.orEmpty(),
            onDismiss = {
                showAddOrderDialog = false
                prefilledOrderEmail = ""
                onDismissPendingOrder()
            },
            onConfirm = { email, planId ->
                showAddOrderDialog = false
                val targetEmail = email.ifBlank { orderDialogEmail.orEmpty() }
                prefilledOrderEmail = ""
                onCreateOrder(targetEmail, planId)
                onDismissPendingOrder()
            },
        )
    }

    state.activeConnectionDetails?.let { details ->
        ResellerConnectionDetailDialog(
            data = ConnectionDialogData(
                title = details.title,
                planName = details.planName,
                subscriptionUrl = details.subscriptionUrl,
                vlessLink = details.vlessLink,
                generatedPassword = details.generatedPassword,
                amountUsd = details.amountUsd,
                expiryDate = details.expiryDate,
                status = details.status,
                servers = details.servers,
            ),
            onDismiss = onDismissConnectionDetails,
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

    if (showTransferFundsDialog) {
        TransferFundsDialog(
            currentBalance = state.overview.balanceUsd,
            initialRecipientEmail = transferInitialEmail,
            lockRecipient = transferLockRecipient,
            onDismiss = { showTransferFundsDialog = false },
            onConfirm = { recipientEmail, amount, desc ->
                showTransferFundsDialog = false
                onTransferFunds(recipientEmail, amount, desc, null)
            },
        )
    }

    selectedSubResellerForDetail?.let { subReseller ->
        SubResellerDetailDialog(
            subReseller = subReseller,
            currentBalance = state.overview.balanceUsd,
            onDismiss = { selectedSubResellerForDetail = null },
            onAddFundsClick = { targetReseller ->
                transferInitialEmail = targetReseller.email
                transferLockRecipient = true
                showTransferFundsDialog = true
            },
        )
    }

    selectedCustomerForDetail?.let { customer ->
        CustomerDetailDialog(
            customer = customer,
            subscriptions = state.subscriptions.filter { it.customerEmail == customer.email },
            onDismiss = { selectedCustomerForDetail = null },
            onChangePassword = { newPassword -> onUpdateCustomerPassword(customer.id, newPassword) },
            onResetPassword = { onResetCustomerPassword(customer.id) },
            onDelete = {
                onDeleteCustomer(customer.id)
                selectedCustomerForDetail = null
            },
            onViewConnection = { sub ->
                onOpenConnectionDetails(
                    ResellerConnectionDetails(
                        title = sub.customerEmail,
                        planName = sub.planName,
                        subscriptionUrl = sub.subscriptionUrl,
                        vlessLink = sub.vlessLink,
                        expiryDate = sub.expiryDate,
                        status = if (sub.isActive) "ACTIVE" else "INACTIVE",
                        servers = sub.servers,
                    )
                )
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
                            text = stringResource(R.string.brand_expires_on, JalaliDateUtils.formatDateWithShamsi(latestSub.expiryDate)),
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
                            text = stringResource(R.string.brand_created_date, JalaliDateUtils.formatDateWithShamsi(c.createdAt)),
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
    isLoading: Boolean = false,
    onSubClick: (ResellerSubscription) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    // Tracks which (subscriptionId, action) is in flight so only the tapped
    // button shows a spinner, not every button on every row.
    var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Disabling a subscription cuts a real customer's access and resetting the
    // UUID breaks their existing VLESS link/QR immediately — both need explicit
    // confirmation before firing. (subId, customerEmail, actionKey)
    var confirmTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(isLoading) {
        if (!isLoading) pendingAction = null
    }

    val filtered = remember(subscriptions, searchQuery) {
        if (searchQuery.isBlank()) subscriptions
        else subscriptions.filter {
            it.customerEmail.contains(searchQuery.trim(), ignoreCase = true) ||
            it.planName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    confirmTarget?.let { (subId, email, actionKey) ->
        val (titleRes, messageRes) = if (actionKey == "reset_uuid") {
            R.string.brand_confirm_reset_uuid_title to R.string.brand_confirm_reset_uuid_message
        } else {
            R.string.brand_confirm_disable_sub_title to R.string.brand_confirm_disable_sub_message
        }
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes, email)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    if (actionKey == "reset_uuid") {
                        pendingAction = subId to "reset_uuid"
                        onResetUuid(subId)
                    } else {
                        pendingAction = subId to "toggle"
                        onToggle(subId, false)
                    }
                }) {
                    Text(stringResource(R.string.brand_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) {
                    Text(stringResource(R.string.brand_cancel))
                }
            },
        )
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
            items(filtered) { sub ->
                Card(
                    onClick = { onSubClick(sub) },
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
                            text = stringResource(R.string.brand_sub_expires_format, sub.planName, JalaliDateUtils.formatDateWithShamsi(sub.expiryDate)),
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
                            TextButton(
                                onClick = {
                                    pendingAction = sub.id to "extend"
                                    onExtend(sub.id)
                                },
                                enabled = !isLoading,
                            ) {
                                if (pendingAction == sub.id to "extend") {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.brand_reseller_extend))
                                }
                            }
                            TextButton(
                                onClick = {
                                    if (sub.isActive) {
                                        confirmTarget = Triple(sub.id, sub.customerEmail, "toggle_disable")
                                    } else {
                                        pendingAction = sub.id to "toggle"
                                        onToggle(sub.id, true)
                                    }
                                },
                                enabled = !isLoading,
                            ) {
                                if (pendingAction == sub.id to "toggle") {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.brand_reseller_toggle))
                                }
                            }
                            TextButton(
                                onClick = { confirmTarget = Triple(sub.id, sub.customerEmail, "reset_uuid") },
                                enabled = !isLoading,
                            ) {
                                if (pendingAction == sub.id to "reset_uuid") {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.brand_reseller_reset_uuid))
                                }
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
    onOrderClick: (ResellerOrder) -> Unit = {},
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
                    onClick = { onOrderClick(o) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOrderClick(o) },
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
                            Text(text = "${o.planName} · ${JalaliDateUtils.formatDateWithShamsi(o.createdAt)}", style = MaterialTheme.typography.bodySmall)
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
    onSubResellerClick: (SubResellerItem) -> Unit,
    onAddFundsClick: (SubResellerItem) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(subResellers, searchQuery) {
        if (searchQuery.isBlank()) subResellers
        else subResellers.filter { it.email.contains(searchQuery.trim(), ignoreCase = true) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.brand_search_subresellers)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        item {
            Button(onClick = onAddSubResellerClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(stringResource(R.string.brand_reseller_add_subreseller))
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.brand_reseller_no_subresellers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered) { r ->
                Card(
                    onClick = { onSubResellerClick(r) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSubResellerClick(r) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = r.email, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = stringResource(R.string.brand_price_usd_double, r.balanceUsd),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.brand_reseller_subreseller_stats,
                                    r.customerCount,
                                    r.subscriptionCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { onAddFundsClick(r) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.brand_add_funds), style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
                EmailDomainChipsRow(email = email, onEmailChange = { email = it })

                Spacer(modifier = Modifier.height(8.dp))

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

/**
 * Renders a wallet transaction's display text: when [WalletTransactionItem.descriptionKey] is a
 * recognized `tx.*` key, formats the matching localized string resource with its params (in the
 * same order as the server's English phrasing); otherwise falls back to the raw (English,
 * pre-i18n) [WalletTransactionItem.description] for legacy rows and free-text custom notes.
 */
@Composable
private fun renderTransactionDescription(tx: WalletTransactionItem): String {
    val p = tx.params
    return when (tx.descriptionKey) {
        "tx.transferOut" -> stringResource(R.string.brand_tx_transfer_out, p.email ?: "")
        "tx.transferIn" -> stringResource(R.string.brand_tx_transfer_in, p.email ?: "")
        "tx.deposit" -> stringResource(R.string.brand_tx_deposit, p.depositId ?: "")
        "tx.planPurchase" -> stringResource(R.string.brand_tx_plan_purchase, p.planName ?: "")
        "tx.orderPayment" -> stringResource(R.string.brand_tx_order_payment, p.orderId ?: "")
        "tx.personalSubscription" -> stringResource(R.string.brand_tx_personal_subscription, p.planName ?: "")
        "tx.personalRenewal" -> stringResource(R.string.brand_tx_personal_renewal, p.planName ?: "")
        "tx.createdAccountOrder" -> stringResource(R.string.brand_tx_created_account_order, p.email ?: "", p.planName ?: "")
        "tx.orderForCustomer" -> stringResource(R.string.brand_tx_order_for_customer, p.email ?: "", p.planName ?: "")
        "tx.subResellerInitialBalance" -> stringResource(R.string.brand_tx_sub_reseller_initial_balance, p.email ?: "")
        "tx.startupBalanceFromParent" -> stringResource(R.string.brand_tx_startup_balance_from_parent)
        else -> tx.description ?: ""
    }
}

@Composable
fun ResellerTransactionsTab(
    transactions: List<WalletTransactionItem>,
    currentBalanceUsd: Double,
    onTransferFundsClick: () -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                    Button(
                        onClick = onTransferFundsClick,
                        
                    ) {
                        Text(stringResource(R.string.brand_transfer_funds))
                    }
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

                        val displayDescription = renderTransactionDescription(tx)
                        if (displayDescription.isNotBlank()) {
                            Text(
                                text = displayDescription,
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
                                text = tx.createdAt.take(16).replace("T", " ") + (JalaliDateUtils.formatShamsiOnly(tx.createdAt)?.let { " ($it)" } ?: ""),
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
                EmailDomainChipsRow(email = email, onEmailChange = { email = it })

                Spacer(modifier = Modifier.height(8.dp))

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
    val effectivePlanId = selectedPlanId.ifBlank { plans.firstOrNull()?.id.orEmpty() }

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
                EmailDomainChipsRow(email = email, onEmailChange = { email = it })

                val matchingCustomers = remember(email, customers) {
                    if (email.isBlank()) customers.take(4)
                    else customers.filter { it.email.contains(email.trim(), ignoreCase = true) }
                }
                if (matchingCustomers.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.brand_reseller_tab_customers),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        matchingCustomers.forEach { cust ->
                            SuggestionChip(
                                onClick = { email = cust.email },
                                label = { Text(cust.email, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.brand_choose_plan), style = MaterialTheme.typography.titleSmall)

                LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    items(plans) { plan ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (effectivePlanId == plan.id),
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
                onClick = { if (email.isNotBlank() && effectivePlanId.isNotBlank()) onConfirm(email.trim(), effectivePlanId) },
                enabled = email.isNotBlank() && effectivePlanId.isNotBlank(),
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
    subscriptions: List<ResellerSubscription>,
    onDismiss: () -> Unit,
    onChangePassword: (String) -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
    onViewConnection: (ResellerSubscription) -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var showPasswordField by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(customer.email) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.brand_created_date, JalaliDateUtils.formatDateWithShamsi(customer.createdAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
                                        text = stringResource(R.string.brand_expires_on, JalaliDateUtils.formatDateWithShamsi(sub.expiryDate)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (sub.isActive && !sub.subscriptionUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = { onViewConnection(sub) },
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


data class ConnectionDialogData(
    val title: String,
    val planName: String,
    val subscriptionUrl: String?,
    val vlessLink: String?,
    val generatedPassword: String? = null,
    val amountUsd: Double? = null,
    val expiryDate: String? = null,
    val status: String? = null,
    val servers: List<ResellerServerLink> = emptyList(),
)

/**
 * One server's connection card: flag/name/city row (with a DEFAULT badge when applicable), its own
 * QR code generated from that server's VLESS link, and a button to copy that same link. Each card owns
 * its own [server] value directly (no shared/mutable index state), so the QR and copy action can never
 * drift to the wrong server when rendered in a list.
 */
@Composable
fun ServerConnectionCard(server: ResellerServerLink) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val qrCodeBitmap = remember(server.vlessLink) {
        if (server.vlessLink.isNotBlank()) QRCodeDecoder.createQRCode(server.vlessLink) else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = server.flag, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = server.city ?: server.countryCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (server.isDefault) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = stringResource(R.string.brand_default_server),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            if (qrCodeBitmap != null) {
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(180.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                )
            }

            Button(
                onClick = {
                    Utils.setClipboard(context, server.vlessLink)
                    context.toast(R.string.brand_copied_to_clipboard)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.brand_copy_vless))
            }
        }
    }
}

@Composable
fun ResellerConnectionDetailDialog(
    data: ConnectionDialogData,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Defensive fallback only: every reseller order/subscription endpoint now returns `servers`,
    // but if some caller ever hands us a bare vlessLink with no per-server breakdown, still show something.
    val fallbackQrCodeBitmap = remember(data.servers, data.vlessLink) {
        if (data.servers.isEmpty() && !data.vlessLink.isNullOrBlank()) QRCodeDecoder.createQRCode(data.vlessLink) else null
    }
    val subQrCodeBitmap = remember(data.subscriptionUrl) {
        if (!data.subscriptionUrl.isNullOrBlank()) QRCodeDecoder.createQRCode(data.subscriptionUrl) else null
    }
    var showAdvanced by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = data.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = data.planName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (data.generatedPassword != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = stringResource(R.string.brand_reseller_new_account_creds), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Email: ${data.title}", style = MaterialTheme.typography.bodySmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Password: ${data.generatedPassword}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    Utils.setClipboard(context, data.generatedPassword)
                                    context.toast(R.string.brand_copied_to_clipboard)
                                }) {
                                    Text(stringResource(R.string.brand_copy), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Subscription URL is an aggregate link that works across every server when scanned by a
                // real client, so it's the primary thing shown here — not the per-server links below.
                if (!data.subscriptionUrl.isNullOrBlank()) {
                    if (subQrCodeBitmap != null) {
                        Image(
                            bitmap = subQrCodeBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            Utils.setClipboard(context, data.subscriptionUrl)
                            context.toast(R.string.brand_copied_to_clipboard)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.brand_copy_sub_link))
                    }

                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (showAdvanced) R.string.brand_hide_advanced_links else R.string.brand_show_advanced_links
                            )
                        )
                    }
                }

                if (showAdvanced || data.subscriptionUrl.isNullOrBlank()) {
                    if (!data.subscriptionUrl.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.brand_advanced_links_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (data.servers.isNotEmpty()) {
                        data.servers.forEach { server ->
                            ServerConnectionCard(server = server)
                        }
                    } else if (!data.vlessLink.isNullOrBlank()) {
                        val fallbackVlessLink = data.vlessLink
                        if (fallbackQrCodeBitmap != null) {
                            Image(
                                bitmap = fallbackQrCodeBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(200.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                            )
                        }
                        Button(
                            onClick = {
                                Utils.setClipboard(context, fallbackVlessLink)
                                context.toast(R.string.brand_copied_to_clipboard)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.brand_copy_vless))
                        }
                    }
                }

                if (data.expiryDate != null) {
                    Text(
                        text = stringResource(R.string.brand_expires_on, JalaliDateUtils.formatDateWithShamsi(data.expiryDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.brand_done))
            }
        }
    )
}

@Composable
fun TransferFundsDialog(
    currentBalance: Double,
    initialRecipientEmail: String = "",
    lockRecipient: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (recipientEmail: String, amountUsd: Double, description: String?) -> Unit,
) {
    var recipientEmail by remember(initialRecipientEmail) { mutableStateOf(initialRecipientEmail) }
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val isOverBalance = amount > currentBalance
    val remainingBalance = (currentBalance - amount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brand_transfer_funds)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.brand_transfer_to_user),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = recipientEmail,
                    onValueChange = { if (!lockRecipient) recipientEmail = it },
                    label = { Text(stringResource(R.string.brand_recipient_email)) },
                    readOnly = lockRecipient,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!lockRecipient) {
                    EmailDomainChipsRow(email = recipientEmail, onEmailChange = { recipientEmail = it })
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(stringResource(R.string.brand_amount_usd)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { amountStr = String.format(java.util.Locale.US, "%.2f", currentBalance) }) {
                            Text(stringResource(R.string.brand_max))
                        }
                    }
                )

                // Quick amount chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 25, 50).forEach { quickVal ->
                        OutlinedButton(
                            onClick = {
                                val newAmount = quickVal.toDouble().coerceAtMost(currentBalance)
                                amountStr = String.format(java.util.Locale.US, "%.2f", newAmount)
                            },
                            enabled = currentBalance >= quickVal,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.brand_quick_amount_add, quickVal), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.brand_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Balance summary card underneath inputs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.brand_available_balance, currentBalance),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (amount > 0.0) {
                            Text(
                                text = stringResource(R.string.brand_remaining_balance, remainingBalance),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOverBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isOverBalance) {
                    Text(
                        text = stringResource(R.string.brand_error_insufficient_balance),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipientEmail.isNotBlank() && amount > 0.0 && !isOverBalance) {
                        onConfirm(recipientEmail.trim().lowercase(), amount, description.ifBlank { null })
                    }
                },
                enabled = recipientEmail.isNotBlank() && amount > 0.0 && !isOverBalance,
            ) {
                Text(stringResource(R.string.brand_confirm_transfer))
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
fun SubResellerDetailDialog(
    subReseller: SubResellerItem,
    currentBalance: Double,
    onDismiss: () -> Unit,
    onAddFundsClick: (SubResellerItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = subReseller.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.brand_sub_reseller_partner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.brand_reseller_balance),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.brand_price_usd_double, subReseller.balanceUsd),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = stringResource(R.string.brand_reseller_tab_customers), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "${subReseller.customerCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = stringResource(R.string.brand_reseller_tab_subscriptions), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "${subReseller.subscriptionCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (subReseller.createdAt.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.brand_created_date, JalaliDateUtils.formatDateWithShamsi(subReseller.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onAddFundsClick(subReseller)
                }
            ) {
                Text(stringResource(R.string.brand_add_funds))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brand_close))
            }
        }
    )
}

@Composable
fun EmailDomainChipsRow(
    email: String,
    onEmailChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val domains = listOf("@gmail.com", "@yahoo.com", "@outlook.com", "@icloud.com", "@proton.me", "@hotmail.com")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        domains.forEach { domain ->
            SuggestionChip(
                onClick = {
                    val clean = email.trim()
                    val atIdx = clean.indexOf('@')
                    val newEmail = if (atIdx == -1) clean + domain else clean.substring(0, atIdx) + domain
                    onEmailChange(newEmail)
                },
                label = { Text(domain, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
