package com.v2ray.ang.ui.brand

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun StorePurchaseDialog(
    kind: StoreProductKind,
    onDismiss: () -> Unit,
    onPurchaseComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<StoreProductOption>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var purchasingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val unavailable = stringResource(R.string.brand_iap_unavailable)

    fun load() {
        scope.launch {
            loading = true
            message = null
            try {
                val token = AuthStore.getToken() ?: error("Signed out")
                val config = ApiClient.iapConfig(token)
                StorePurchaseManager.configure(
                    context = context,
                    config = config,
                    onReady = {
                        StorePurchaseManager.loadProducts(
                            config = config,
                            onSuccess = {
                                products = it.filter { product -> product.kind == kind }
                                loading = false
                                if (products.isEmpty()) message = unavailable
                            },
                            onError = { message = it; loading = false },
                        )
                    },
                    onError = { message = it; loading = false },
                )
            } catch (error: Exception) {
                message = error.message ?: unavailable
                loading = false
            }
        }
    }

    LaunchedEffect(kind) { load() }

    AlertDialog(
        onDismissRequest = { if (purchasingId == null) onDismiss() },
        title = {
            Text(
                if (kind == StoreProductKind.SUBSCRIPTION) stringResource(R.string.brand_iap_subscriptions)
                else stringResource(R.string.brand_iap_add_funds)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (kind == StoreProductKind.SUBSCRIPTION) stringResource(R.string.brand_iap_auto_renew_note)
                    else stringResource(R.string.brand_iap_wallet_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(products, key = { it.id }) { product ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                onClick = {
                                    val activity = context.findActivity()
                                    if (activity == null) {
                                        message = unavailable
                                    } else {
                                        purchasingId = product.id
                                        message = null
                                        StorePurchaseManager.purchase(
                                            activity = activity,
                                            productId = product.id,
                                            onSuccess = {
                                                message = context.getString(R.string.brand_iap_purchase_processing)
                                                purchasingId = null
                                                scope.launch {
                                                    delay(1800)
                                                    onPurchaseComplete()
                                                    onDismiss()
                                                }
                                            },
                                            onCancelled = { purchasingId = null },
                                            onError = { message = it; purchasingId = null },
                                        )
                                    }
                                },
                                enabled = purchasingId == null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(product.title, fontWeight = FontWeight.Bold)
                                        if (product.description.isNotBlank()) {
                                            Text(product.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Text(product.formattedPrice, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                if (kind == StoreProductKind.SUBSCRIPTION && StorePurchaseManager.supported) {
                    OutlinedButton(
                        onClick = {
                            loading = true
                            StorePurchaseManager.restore(
                                onSuccess = { loading = false; message = context.getString(R.string.brand_iap_restored); onPurchaseComplete() },
                                onError = { loading = false; message = it },
                            )
                        },
                        enabled = !loading && purchasingId == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.brand_iap_restore)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = purchasingId == null) { Text(stringResource(R.string.brand_close)) }
        },
    )
}

@Composable
fun AccountSettingsDialog(
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val requiredConfirmation = stringResource(R.string.brand_account_delete_phrase)

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.brand_account_settings)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.brand_privacy_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.brand_privacy_summary), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.brand_privacy_stored), style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.brand_privacy_policy))
                        }
                        OutlinedButton(onClick = { Utils.openUri(context, AppConfig.APP_TERMS) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.brand_terms_conditions))
                        }
                    }
                }
                if (StorePurchaseManager.supported) {
                    item {
                        OutlinedButton(
                            onClick = {
                                StorePurchaseManager.managementUrl { uri ->
                                    if (uri != null) context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    else error = context.getString(R.string.brand_iap_no_managed_subscription)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.brand_iap_manage_subscription)) }
                    }
                }
                item {
                    Text(stringResource(R.string.brand_account_delete_title), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.brand_account_delete_warning), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.brand_account_subscription_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.brand_current_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.brand_account_type_delete, requiredConfirmation)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        deleting = true
                        error = null
                        try {
                            val token = AuthStore.getToken() ?: error("Signed out")
                            ApiClient.deleteAccount(token, password)
                            AuthStore.clear()
                            onDeleted()
                        } catch (cause: Exception) {
                            error = cause.message ?: context.getString(R.string.brand_account_delete_failed)
                            deleting = false
                        }
                    }
                },
                enabled = !deleting && password.isNotBlank() && confirmation == requiredConfirmation,
            ) {
                if (deleting) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                else Text(stringResource(R.string.brand_account_delete_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(R.string.brand_cancel)) } },
    )
}
