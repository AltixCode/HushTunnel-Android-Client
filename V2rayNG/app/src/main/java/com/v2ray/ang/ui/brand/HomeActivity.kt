package com.v2ray.ang.ui.brand

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay

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

    private fun handleConnectToggle(isRunning: Boolean) {
        if (isRunning) {
            LauncherManager.stopService(this)
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
            onBuyPlan = { planId ->
                viewModel.buyPlan(planId) { checkoutUrl -> Utils.openUri(this, checkoutUrl) }
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
    onBuyPlan: (String) -> Unit,
    onDismissCheckoutMessage: () -> Unit,
    onLogout: () -> Unit,
) {
    var showPlans by remember { mutableStateOf(false) }
    val activeSub = state.subscriptions.firstOrNull { it.isActive }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = state.email, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Refresh")
                    }
                }
            }

            Text(
                text = if (activeSub != null) "Subscription active" else "No active subscription",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onConnectToggle,
                    enabled = state.hasServer,
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(if (state.isRunning) "Disconnect" else "Connect")
                }
            }

            if (!state.hasServer) {
                Text(
                    text = "Buy a plan to get connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            activeSub?.let { sub ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = sub.planName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Expires ${sub.expiryDate.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            state.checkoutMessage?.let {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(text = it, modifier = Modifier.padding(16.dp))
                }
                LaunchedEffect(it) {
                    delay(6000)
                    onDismissCheckoutMessage()
                }
            }

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }

            Button(onClick = { showPlans = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (activeSub != null) "Renew / buy another plan" else "Buy a plan")
            }

            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Log out")
            }
        }
    }

    if (showPlans) {
        AlertDialog(
            onDismissRequest = { showPlans = false },
            title = { Text("Choose a plan") },
            text = {
                if (state.plans.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn {
                        items(state.plans) { plan ->
                            TextButton(
                                onClick = {
                                    showPlans = false
                                    onBuyPlan(plan.id)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(plan.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "$${plan.priceUsd} · ${plan.durationDays} days",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlans = false }) { Text("Cancel") }
            },
        )
    }
}
