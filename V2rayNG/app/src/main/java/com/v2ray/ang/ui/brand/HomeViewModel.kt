package com.v2ray.ang.ui.brand

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.main.MainRepository
import com.v2ray.ang.ui.main.MainServiceEvent
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.core.CoreServiceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val email: String = "",
    val subscriptions: List<SubscriptionInfo> = emptyList(),
    val selectedSubscriptionId: String? = null,
    val servers: List<ServerNode> = emptyList(),
    val selectedServerId: String? = null,
    val plans: List<PlanInfo> = emptyList(),
    val gateways: GatewayInfo = GatewayInfo(),
    val orders: List<OrderItem> = emptyList(),
    val isRunning: Boolean = false,
    val hasServer: Boolean = false,
    val error: String? = null,
    val checkoutMessage: String? = null,
    val isPollingOrder: Boolean = false,
)

class HomeViewModel(application: Application) : BaseViewModel(application) {

    private val mainRepository = MainRepository(app)
    private var pollJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState(isRunning = CoreServiceManager.isRunning()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            mainRepository.mainServiceEvent.collect { event ->
                when (event) {
                    MainServiceEvent.StateRunning,
                    MainServiceEvent.StateStartSuccess -> _uiState.update { it.copy(isRunning = true) }
                    MainServiceEvent.StateNotRunning,
                    MainServiceEvent.StateStopSuccess -> _uiState.update { it.copy(isRunning = false) }
                    MainServiceEvent.StateStartFailure -> {
                        _uiState.update { it.copy(isRunning = false) }
                        toastError(app.getString(R.string.brand_error_vpn_start))
                    }
                    else -> {}
                }
            }
        }
        refresh()
    }

    fun checkVpnState() {
        val running = CoreServiceManager.isRunning()
        _uiState.update { it.copy(isRunning = running) }
    }

    fun setVpnRunning(running: Boolean) {
        _uiState.update { it.copy(isRunning = running) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        mainRepository.close()
        super.onCleared()
    }

    /** Re-checks account/subscription status and (re)points the hidden v2ray subscription at the active one. */
    fun refresh() {
        val token = AuthStore.getToken()
        if (token == null) {
            _uiState.update { it.copy(error = app.getString(R.string.brand_error_signed_out)) }
            return
        }

        launchLoading {
            try {
                val me = ApiClient.me(token)
                val activeSubs = me.subscriptions.filter { it.isActive }
                val savedSelectedId = AuthStore.getSelectedSubscriptionId()
                val selectedSub = activeSubs.firstOrNull { it.id == savedSelectedId }
                    ?: activeSubs.firstOrNull()
                    ?: me.subscriptions.firstOrNull()

                val selectedId = selectedSub?.id
                AuthStore.setSelectedSubscriptionId(selectedId)

                var provisioned = false
                if (selectedSub != null && selectedSub.isActive) {
                    provisioned = ProvisionHelper.provisionSubscription(selectedSub.subscriptionUrl)
                }

                val loadedPlans = try { ApiClient.plans() } catch (_: Exception) { emptyList() }
                val loadedGateways = try { ApiClient.gateways() } catch (_: Exception) { GatewayInfo() }
                val loadedOrders = try { ApiClient.orders(token) } catch (_: Exception) { emptyList() }

                val isRunningNow = CoreServiceManager.isRunning()

                val currentSelectedServer = _uiState.value.selectedServerId
                    ?: me.servers.firstOrNull { it.isDefault }?.id
                    ?: me.servers.firstOrNull()?.id

                _uiState.update {
                    it.copy(
                        email = me.email,
                        subscriptions = me.subscriptions,
                        selectedSubscriptionId = selectedId,
                        servers = me.servers,
                        selectedServerId = currentSelectedServer,
                        plans = loadedPlans,
                        gateways = loadedGateways,
                        orders = loadedOrders,
                        hasServer = provisioned,
                        isRunning = isRunningNow,
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

    fun selectSubscription(subscriptionId: String) {
        val target = _uiState.value.subscriptions.firstOrNull { it.id == subscriptionId } ?: return
        AuthStore.setSelectedSubscriptionId(target.id)
        _uiState.update { it.copy(selectedSubscriptionId = target.id) }

        if (target.isActive) {
            launchLoading {
                val provisioned = ProvisionHelper.provisionSubscription(target.subscriptionUrl)
                _uiState.update { it.copy(hasServer = provisioned) }
            }
        }
    }

    fun loadPlans() {
        launchLoading {
            try {
                val plans = ApiClient.plans()
                val gateways = ApiClient.gateways()
                _uiState.update { it.copy(plans = plans, gateways = gateways, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    /** Places a fresh plan order. */
    fun buyPlan(planId: String, gateway: String, onCheckoutUrl: (String) -> Unit) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val result = ApiClient.checkout(token, planId, gateway = gateway, subscriptionId = null)
                if (result.checkoutUrl != null) {
                    onCheckoutUrl(result.checkoutUrl)
                    _uiState.update { it.copy(checkoutMessage = null) }
                    startPollingOrder(token, result.orderId)
                } else {
                    _uiState.update {
                        it.copy(checkoutMessage = app.getString(R.string.brand_order_awaiting))
                    }
                    refresh()
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    /** Renews an existing subscription with [planId] and [gateway]. */
    fun renewSubscription(subscriptionId: String, planId: String, gateway: String, onCheckoutUrl: (String) -> Unit) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val result = ApiClient.checkout(token, planId, gateway = gateway, subscriptionId = subscriptionId)
                if (result.checkoutUrl != null) {
                    onCheckoutUrl(result.checkoutUrl)
                    _uiState.update { it.copy(checkoutMessage = null) }
                    startPollingOrder(token, result.orderId)
                } else {
                    _uiState.update {
                        it.copy(checkoutMessage = app.getString(R.string.brand_order_awaiting))
                    }
                    refresh()
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
    }

    private fun startPollingOrder(token: String, orderId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _uiState.update { it.copy(isPollingOrder = true) }
            for (attempt in 1..40) {
                delay(3000)
                try {
                    val status = ApiClient.orderStatus(token, orderId)
                    if (status.status.equals("PAID", ignoreCase = true)) {
                        _uiState.update {
                            it.copy(
                                isPollingOrder = false,
                                checkoutMessage = app.getString(R.string.brand_order_paid_success),
                            )
                        }
                        refresh()
                        return@launch
                    }
                } catch (_: Exception) {
                    // Continue polling
                }
            }
            _uiState.update { it.copy(isPollingOrder = false) }
        }
    }

    fun dismissCheckoutMessage() {
        _uiState.update { it.copy(checkoutMessage = null) }
    }

    fun logout() {
        AuthStore.clear()
    }

    
    fun switchServer(serverId: String, onReconnect: () -> Unit) {
        _uiState.update { it.copy(selectedServerId = serverId) }
        if (_uiState.value.isRunning) {
            onReconnect()
        }
    }

    fun changePassword(currentPassword: String?, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val token = AuthStore.getToken(app) ?: return
        viewModelScope.launch {
            try {
                isLoading.value = true
                ApiClient.changePassword(token, currentPassword, newPassword)
                isLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                isLoading.value = false
                onError(e.message ?: "Failed to change password")
            }
        }
    }
}
