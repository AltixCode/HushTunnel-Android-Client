package com.v2ray.ang.ui.brand

import android.app.Application
import com.v2ray.ang.R
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.ui.main.MainRepository
import com.v2ray.ang.ui.main.MainServiceEvent
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ResellerUiState(
    val isRunning: Boolean = false,
    val overview: ResellerOverview = ResellerOverview(0.0, 0, null, null),
    val customers: List<ResellerCustomer> = emptyList(),
    val subscriptions: List<ResellerSubscription> = emptyList(),
    val personalSubscriptions: List<SubscriptionInfo> = emptyList(),
    val orders: List<ResellerOrder> = emptyList(),
    val deposits: List<ResellerDeposit> = emptyList(),
    val transactions: List<WalletTransactionItem> = emptyList(),
    val subResellers: List<SubResellerItem> = emptyList(),
    val plans: List<PlanInfo> = emptyList(),
    val gateways: GatewayInfo = GatewayInfo(),
    val selectedTab: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val pendingOrderForEmail: String? = null,
    val activeConnectionDetails: ResellerConnectionDetails? = null,
)

class ResellerHomeViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ResellerUiState())
    val uiState: StateFlow<ResellerUiState> = _uiState.asStateFlow()
    private val mainRepository = MainRepository(app)

    init {
        viewModelScope.launch {
            mainRepository.mainServiceEvent.collect { event ->
                when (event) {
                    MainServiceEvent.StateRunning,
                    MainServiceEvent.StateStartSuccess -> _uiState.update { it.copy(isRunning = true) }
                    MainServiceEvent.StateNotRunning,
                    MainServiceEvent.StateStopSuccess,
                    MainServiceEvent.StateStartFailure -> _uiState.update { it.copy(isRunning = false) }
                    else -> {}
                }
            }
        }
        refresh()
    }

    fun checkVpnState() {
        mainRepository.requestServiceState()
    }

    override fun onCleared() {
        mainRepository.close()
        super.onCleared()
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun refresh() {
        val token = AuthStore.getToken()
        if (token == null) {
            _uiState.update { it.copy(error = app.getString(R.string.brand_error_signed_out)) }
            return
        }

        launchLoading {
            try {
                val overview = try { ApiClient.resellerOverview(token) } catch (_: Exception) { ResellerOverview(0.0, 0, null, null) }
                val customers = try { ApiClient.resellerCustomers(token) } catch (_: Exception) { emptyList() }
                val subscriptions = try { ApiClient.resellerSubscriptions(token) } catch (_: Exception) { emptyList() }
                val personalSubscriptions = try { ApiClient.me(token).subscriptions } catch (_: Exception) { emptyList() }
                val orders = try { ApiClient.resellerOrders(token) } catch (_: Exception) { emptyList() }
                val deposits = try { ApiClient.resellerDeposits(token) } catch (_: Exception) { emptyList() }
                val transactions = try { ApiClient.getWalletTransactions(token) } catch (_: Exception) { emptyList() }
                val plans = try { ApiClient.plans() } catch (_: Exception) { emptyList() }
                val gateways = try { ApiClient.gateways() } catch (_: Exception) { GatewayInfo() }
                val subResellers = try { ApiClient.resellerSubResellers(token) } catch (_: Exception) { emptyList() }

                _uiState.update {
                    it.copy(
                        overview = overview,
                        customers = customers,
                        subscriptions = subscriptions,
                        personalSubscriptions = personalSubscriptions,
                        orders = orders,
                        deposits = deposits,
                        transactions = transactions,
                        subResellers = subResellers,
                        plans = plans,
                        gateways = gateways,
                        error = null,
                    )
                }
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    AuthStore.clear()
                    _uiState.update { it.copy(error = app.getString(R.string.brand_error_signed_out)) }
                } else {
                    _uiState.update { it.copy(error = e.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun createCustomer(email: String, customPassword: String? = null) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val (customer, password) = ApiClient.createResellerCustomer(token, email, customPassword)
                val overview = try { ApiClient.resellerOverview(token) } catch (_: Exception) { _uiState.value.overview }
                val customers = try { ApiClient.resellerCustomers(token) } catch (_: Exception) { _uiState.value.customers }
                _uiState.update {
                    it.copy(
                        overview = overview,
                        customers = customers,
                        message = app.getString(R.string.brand_reseller_customer_created, password),
                        error = null,
                        pendingOrderForEmail = customer.email,
                    )
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun createOrderForCustomer(
        customerEmail: String,
        planId: String,
    ) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val result = ApiClient.createResellerOrder(token, customerEmail, planId)
                val overview = try { ApiClient.resellerOverview(token) } catch (_: Exception) { _uiState.value.overview }
                val orders = try { ApiClient.resellerOrders(token) } catch (_: Exception) { _uiState.value.orders }
                val subscriptions = try { ApiClient.resellerSubscriptions(token) } catch (_: Exception) { _uiState.value.subscriptions }
                val msg = if (result.generatedPassword != null) {
                    app.getString(R.string.brand_reseller_customer_created, result.generatedPassword)
                } else {
                    app.getString(R.string.brand_order_paid_success)
                }
                _uiState.update {
                    it.copy(
                        overview = overview,
                        orders = orders,
                        subscriptions = subscriptions,
                        message = msg,
                        error = null,
                        pendingOrderForEmail = null,
                        activeConnectionDetails = ResellerConnectionDetails(
                            title = customerEmail,
                            planName = result.planName ?: app.getString(R.string.brand_all_subscriptions),
                            subscriptionUrl = result.subscriptionUrl,
                            vlessLink = result.vlessLink,
                            generatedPassword = result.generatedPassword,
                            amountUsd = result.amountUsd,
                            servers = result.servers,
                        ),
                    )
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun openConnectionDetails(details: ResellerConnectionDetails) {
        _uiState.update { it.copy(activeConnectionDetails = details) }
    }

    fun dismissConnectionDetails() {
        _uiState.update { it.copy(activeConnectionDetails = null) }
    }

    fun dismissPendingOrder() {
        _uiState.update { it.copy(pendingOrderForEmail = null) }
    }

    fun createDeposit(amountUsd: Double, gateway: String, onCheckoutUrl: (String) -> Unit) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val (_, checkoutUrl) = ApiClient.createResellerDeposit(token, amountUsd, gateway)
                if (checkoutUrl != null) {
                    onCheckoutUrl(checkoutUrl)
                } else {
                    _uiState.update { it.copy(message = app.getString(R.string.brand_order_awaiting)) }
                }
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun extendSubscription(id: String, days: Int = 30) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.extendResellerSubscription(token, id, days)
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun toggleSubscription(id: String, enable: Boolean) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.toggleResellerSubscription(token, id, enable)
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun resetSubscriptionUuid(id: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.resetResellerSubscriptionUuid(token, id)
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun resetSubscriptionTraffic(id: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.resetResellerSubscriptionTraffic(token, id)
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun revokeSubscription(id: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.revokeResellerSubscription(token, id)
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun logout() {
        AuthStore.clear()
    }

    
    fun updateCustomerPassword(customerId: String, newPassword: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.updateCustomerPassword(token, customerId, newPassword)
                _uiState.update { it.copy(message = "Customer password updated successfully", error = null) }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update password") }
            }
        }
    }

    fun resetCustomerPassword(customerId: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val newPassword = ApiClient.resetCustomerPassword(token, customerId)
                _uiState.update { it.copy(message = "Password reset: $newPassword", error = null) }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to reset password") }
            }
        }
    }

    fun deleteCustomer(customerId: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.deleteCustomer(token, customerId)
                _uiState.update { it.copy(message = "Customer deleted", error = null) }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete customer") }
            }
        }
    }

    fun changePassword(currentPassword: String?, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.changePassword(token, currentPassword, newPassword)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to change password")
            }
        }
    }

    fun buyPersonalSubscription(planId: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.createSelfSubscription(token, planId)
                _uiState.update { it.copy(message = app.getString(R.string.brand_order_paid_success), error = null) }
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun renewPersonalSubscription(subscriptionId: String, planId: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                ApiClient.createSelfSubscription(token, planId, subscriptionId)
                _uiState.update { it.copy(message = app.getString(R.string.brand_order_paid_success), error = null) }
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun createSubReseller(email: String, initialBalanceUsd: Double) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val (_, password) = ApiClient.createSubReseller(token, email, initialBalanceUsd)
                _uiState.update {
                    it.copy(
                        message = app.getString(R.string.brand_reseller_customer_created, password),
                        error = null,
                    )
                }
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    fun transferFunds(
        recipientEmail: String,
        amountUsd: Double,
        description: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val res = ApiClient.transferFunds(token, recipientEmail, amountUsd, description)
                val newBal = res.optDouble("newBalance", -1.0)
                val msg = if (newBal >= 0) {
                    app.getString(R.string.brand_transfer_success_with_balance, String.format(java.util.Locale.US, "%.2f", newBal))
                } else {
                    app.getString(R.string.brand_transfer_success)
                }
                _uiState.update { it.copy(message = msg, error = null) }
                onSuccess?.invoke()
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }
}
