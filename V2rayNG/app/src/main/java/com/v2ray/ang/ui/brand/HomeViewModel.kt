package com.v2ray.ang.ui.brand

import android.app.Application
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.main.MainRepository
import com.v2ray.ang.ui.main.MainServiceEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val email: String = "",
    val subscriptions: List<SubscriptionInfo> = emptyList(),
    val plans: List<PlanInfo> = emptyList(),
    val isRunning: Boolean = false,
    val hasServer: Boolean = false,
    val error: String? = null,
    val checkoutMessage: String? = null,
)

class HomeViewModel(application: Application) : BaseViewModel(application) {

    private val mainRepository = MainRepository(app)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            mainRepository.mainServiceEvent.collect { event ->
                when (event) {
                    MainServiceEvent.StateRunning -> _uiState.update { it.copy(isRunning = true) }
                    MainServiceEvent.StateNotRunning, MainServiceEvent.StateStopSuccess ->
                        _uiState.update { it.copy(isRunning = false) }
                    MainServiceEvent.StateStartFailure -> {
                        _uiState.update { it.copy(isRunning = false) }
                        toastError("Couldn't start the VPN")
                    }
                    else -> {}
                }
            }
        }
        refresh()
    }

    override fun onCleared() {
        mainRepository.close()
        super.onCleared()
    }

    /** Re-checks account/subscription status and (re)points the hidden v2ray subscription at the active one. */
    fun refresh() {
        val token = AuthStore.getToken()
        if (token == null) {
            _uiState.update { it.copy(error = "Signed out") }
            return
        }

        launchLoading {
            try {
                val me = ApiClient.me(token)
                _uiState.update { it.copy(email = me.email, subscriptions = me.subscriptions, error = null) }

                val active = me.subscriptions.firstOrNull { it.isActive }
                if (active != null) {
                    val provisioned = ProvisionHelper.provisionSubscription(active.subscriptionUrl)
                    _uiState.update { it.copy(hasServer = provisioned) }
                }
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    AuthStore.clear()
                    _uiState.update { it.copy(error = "Signed out") }
                } else {
                    _uiState.update { it.copy(error = e.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Couldn't reach the server — check your connection") }
            }
        }
    }

    fun loadPlans() {
        launchLoading {
            try {
                val plans = ApiClient.plans()
                _uiState.update { it.copy(plans = plans, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Couldn't load plans") }
            }
        }
    }

    /**
     * Places an order for [planId]. If a real payment gateway is configured server-side, the
     * returned checkout URL is opened in the browser (that page belongs to the payment
     * provider, not our own site, so it needs no session handoff). Otherwise the order sits
     * pending until an admin confirms it — [onCheckoutUrl] is only invoked in the former case.
     */
    fun buyPlan(planId: String, onCheckoutUrl: (String) -> Unit) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val result = ApiClient.checkout(token, planId, gateway = "MANUAL")
                if (result.checkoutUrl != null) {
                    onCheckoutUrl(result.checkoutUrl)
                    _uiState.update { it.copy(checkoutMessage = null) }
                } else {
                    _uiState.update {
                        it.copy(checkoutMessage = "Order placed — awaiting confirmation. Pull to refresh once it's paid.")
                    }
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Couldn't place the order — check your connection") }
            }
        }
    }

    fun dismissCheckoutMessage() {
        _uiState.update { it.copy(checkoutMessage = null) }
    }

    fun logout() {
        AuthStore.clear()
    }
}
