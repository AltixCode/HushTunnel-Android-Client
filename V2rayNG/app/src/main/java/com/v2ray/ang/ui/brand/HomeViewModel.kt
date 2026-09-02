package com.v2ray.ang.ui.brand

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.main.MainRepository
import com.v2ray.ang.ui.main.MainServiceEvent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID

internal fun resolveSelectedServerId(
    servers: List<ServerNode>,
    currentServerId: String?,
    savedServerId: String?,
): String? = servers.firstOrNull { it.id == currentServerId }?.id
    ?: servers.firstOrNull { it.id == savedServerId }?.id
    ?: servers.firstOrNull { it.isDefault }?.id
    ?: servers.firstOrNull()?.id

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
    val isTestingConnection: Boolean = false,
    val isSwitchingServer: Boolean = false,
    val testResult: ConnectionTestResult? = null,
)

data class ConnectionTestResult(
    val status: Status,
    val ip: String,
    val latencyMs: Long,
    val serverMatches: Boolean,
    val message: String,
) {
    enum class Status { SUCCESS, WARNING, ERROR }
}

class HomeViewModel(application: Application) : BaseViewModel(application) {

    private val mainRepository = MainRepository(app)
    private var pollJob: Job? = null
    private var serverSwitchTimeoutJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            mainRepository.mainServiceEvent.collect { event ->
                when (event) {
                    MainServiceEvent.StateRunning,
                    MainServiceEvent.StateStartSuccess -> {
                        serverSwitchTimeoutJob?.cancel()
                        _uiState.update { it.copy(isRunning = true, isSwitchingServer = false) }
                    }
                    MainServiceEvent.StateNotRunning,
                    MainServiceEvent.StateStopSuccess -> _uiState.update { it.copy(isRunning = false) }
                    MainServiceEvent.StateStartFailure -> {
                        serverSwitchTimeoutJob?.cancel()
                        _uiState.update { it.copy(isRunning = false, isSwitchingServer = false) }
                        toastError(app.getString(R.string.brand_error_vpn_start))
                    }
                    else -> {}
                }
            }
        }
        refresh()
    }

    /**
     * Re-syncs connect state on Activity resume. Does NOT check
     * `CoreServiceManager.isRunning()` directly — the VPN daemon runs in a
     * separate process (`android:process=":RunSoLibV2RayDaemon"`, see
     * AndroidManifest.xml), so that call only ever reflects the UI process's
     * own unused `coreController`, never the daemon's real state. This was
     * overwriting a correct "connected" state with a false "disconnected" on
     * every resume, with nothing to correct it afterward (the registration
     * handshake that keeps `isRunning` right only fires once, when
     * MainRepository is first constructed) — the real, reported bug: the
     * app showed Disconnected after backgrounding/foregrounding even though
     * the system-level VPN was still active. Fixed by re-asking the daemon
     * via the same cross-process handshake `MainRepository.init` uses; the
     * reply updates `isRunning` through the existing `mainServiceEvent`
     * collector below, which is correct.
     */
    fun checkVpnState() {
        mainRepository.requestServiceState()
    }

    fun setVpnRunning(running: Boolean) {
        _uiState.update { it.copy(isRunning = running) }
    }

    fun testConnection() {
        if (_uiState.value.isTestingConnection || !_uiState.value.isRunning) return
        val currentServer = _uiState.value.servers.firstOrNull { it.id == _uiState.value.selectedServerId }
            ?: _uiState.value.servers.firstOrNull { it.isDefault }
            ?: _uiState.value.servers.firstOrNull()
        val expectedHost = currentServer?.host ?: ""

        _uiState.update { it.copy(isTestingConnection = true, testResult = null) }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
                    // The VPN service must exclude its own package to avoid a
                    // routing loop. Force this diagnostic through the core's
                    // local proxy instead of testing the app's direct socket.
                    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, SettingsManager.getHttpPort())))
                    .proxyAuthenticator { _, response ->
                        val username = SettingsManager.getSocksUsername()
                        val password = SettingsManager.getSocksPassword()
                        if (username.isNullOrBlank() || password.isNullOrBlank() || response.request.header("Proxy-Authorization") != null) {
                            null
                        } else {
                            response.request.newBuilder()
                                .header("Proxy-Authorization", okhttp3.Credentials.basic(username, password))
                                .build()
                        }
                    }
                    .build()

                val nonce = UUID.randomUUID().toString()
                val request = okhttp3.Request.Builder()
                    .url("https://api.ipify.org?format=json&nonce=$nonce")
                    .header("Cache-Control", "no-cache")
                    .build()

                val response = client.newCall(request).execute()
                val elapsed = System.currentTimeMillis() - startTime
                val bodyString = response.body?.string() ?: ""

                try {
                    val appleReq = okhttp3.Request.Builder()
                        .url("https://www.apple.com/?hush_test=$nonce")
                        .header("Cache-Control", "no-cache")
                        .build()
                    client.newCall(appleReq).execute().close()
                } catch (_: Exception) {}

                if (!response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            testResult = ConnectionTestResult(
                                status = ConnectionTestResult.Status.ERROR,
                                ip = "",
                                latencyMs = 0,
                                serverMatches = false,
                                message = app.getString(R.string.brand_test_failed)
                            )
                        )
                    }
                    return@launch
                }

                val json = org.json.JSONObject(bodyString)
                val ip = json.optString("ip", "").trim()
                val matches = ip.equals(expectedHost.trim(), ignoreCase = true)

                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        testResult = if (matches) {
                            ConnectionTestResult(
                                status = ConnectionTestResult.Status.SUCCESS,
                                ip = ip,
                                latencyMs = elapsed,
                                serverMatches = true,
                                message = app.getString(R.string.brand_test_success, elapsed, ip)
                            )
                        } else {
                            ConnectionTestResult(
                                status = ConnectionTestResult.Status.WARNING,
                                ip = ip,
                                latencyMs = elapsed,
                                serverMatches = false,
                                message = app.getString(R.string.brand_test_unprotected, ip)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        testResult = ConnectionTestResult(
                            status = ConnectionTestResult.Status.ERROR,
                            ip = "",
                            latencyMs = 0,
                            serverMatches = false,
                            message = app.getString(R.string.brand_test_failed)
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        serverSwitchTimeoutJob?.cancel()
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

                val loadedPlans = try { ApiClient.plans() } catch (_: Exception) { emptyList() }
                val loadedGateways = try { ApiClient.gateways() } catch (_: Exception) { GatewayInfo() }
                val loadedOrders = try { ApiClient.orders(token) } catch (_: Exception) { emptyList() }

                val currentSelectedServer = resolveSelectedServerId(
                    servers = me.servers,
                    currentServerId = _uiState.value.selectedServerId,
                    savedServerId = AuthStore.getSelectedServerId(),
                )

                val activeServerObj = me.servers.firstOrNull { it.id == currentSelectedServer }

                var provisioned = false
                if (selectedSub != null && selectedSub.isActive) {
                    provisioned = ProvisionHelper.provisionSubscription(selectedSub.subscriptionUrl, activeServerObj)
                } else if (activeServerObj != null) {
                    ProvisionHelper.selectServerByNode(activeServerObj)
                }
                if (provisioned) AuthStore.setSelectedServerId(currentSelectedServer)

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
                val server = _uiState.value.servers.firstOrNull { it.id == _uiState.value.selectedServerId }
                val provisioned = ProvisionHelper.provisionSubscription(target.subscriptionUrl, server)
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

    
    fun prepareConnection(onReady: () -> Unit) {
        val state = _uiState.value
        val subscription = state.subscriptions.firstOrNull {
            it.id == state.selectedSubscriptionId && it.isActive
        } ?: state.subscriptions.firstOrNull { it.isActive }
        val server = state.servers.firstOrNull { it.id == state.selectedServerId }
        if (subscription == null || server == null) {
            toastError(R.string.brand_error_vpn_start)
            return
        }

        launchLoading {
            val provisioned = ProvisionHelper.provisionSubscription(subscription.subscriptionUrl, server)
            _uiState.update { it.copy(hasServer = provisioned) }
            if (provisioned) {
                AuthStore.setSelectedServerId(server.id)
                onReady()
            } else {
                toastError(R.string.brand_error_vpn_start)
            }
        }
    }

    fun switchServer(serverId: String, onReconnect: () -> Unit) {
        val targetServer = _uiState.value.servers.firstOrNull { it.id == serverId }
        val subscription = _uiState.value.subscriptions.firstOrNull {
            it.id == _uiState.value.selectedSubscriptionId && it.isActive
        } ?: _uiState.value.subscriptions.firstOrNull { it.isActive }
        if (targetServer == null || subscription == null) return

        val wasRunning = _uiState.value.isRunning
        _uiState.update { it.copy(isSwitchingServer = true, testResult = null) }
        launchLoading {
            val provisioned = ProvisionHelper.provisionSubscription(subscription.subscriptionUrl, targetServer)
            if (!provisioned) {
                _uiState.update { it.copy(isSwitchingServer = false) }
                toastError(R.string.brand_error_connection)
                return@launchLoading
            }
            AuthStore.setSelectedServerId(serverId)
            _uiState.update {
                it.copy(
                    selectedServerId = serverId,
                    hasServer = true,
                    isSwitchingServer = wasRunning,
                    testResult = null,
                )
            }
            if (wasRunning) {
                onReconnect()
                serverSwitchTimeoutJob?.cancel()
                serverSwitchTimeoutJob = viewModelScope.launch {
                    delay(20_000)
                    _uiState.update { it.copy(isSwitchingServer = false) }
                }
            } else {
                _uiState.update { it.copy(isSwitchingServer = false) }
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
}
