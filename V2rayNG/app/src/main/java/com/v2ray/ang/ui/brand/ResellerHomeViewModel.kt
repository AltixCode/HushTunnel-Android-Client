package com.v2ray.ang.ui.brand

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ResellerUiState(
    val overview: ResellerOverview = ResellerOverview(0.0, 0, null, null),
    val customers: List<ResellerCustomer> = emptyList(),
    val subscriptions: List<ResellerSubscription> = emptyList(),
    val personalSubscriptions: List<SubscriptionInfo> = emptyList(),
    val orders: List<ResellerOrder> = emptyList(),
    val deposits: List<ResellerDeposit> = emptyList(),
    val subResellers: List<SubResellerItem> = emptyList(),
    val plans: List<PlanInfo> = emptyList(),
    val gateways: GatewayInfo = GatewayInfo(),
    val selectedTab: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

class ResellerHomeViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ResellerUiState())
    val uiState: StateFlow<ResellerUiState> = _uiState.asStateFlow()

    init {
        refresh()
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

    fun createCustomer(email: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val (_, password) = ApiClient.createResellerCustomer(token, email)
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

    fun createOrderForCustomer(customerEmail: String, planId: String) {
        val token = AuthStore.getToken() ?: return
        launchLoading {
            try {
                val (_, generatedPassword) = ApiClient.createResellerOrder(token, customerEmail, planId)
                val msg = if (generatedPassword != null) {
                    app.getString(R.string.brand_reseller_customer_created, generatedPassword)
                } else {
                    app.getString(R.string.brand_order_paid_success)
                }
                _uiState.update { it.copy(message = msg, error = null) }
                refresh()
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = app.getString(R.string.brand_error_connection)) }
            }
        }
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
}
