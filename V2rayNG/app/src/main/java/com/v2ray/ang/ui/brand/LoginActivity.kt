package com.v2ray.ang.ui.brand

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

class LoginActivity : BaseComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        LoginScreen(
            state = state,
            isLoading = isLoading,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onLoginClick = {
                viewModel.login { role ->
                    val destination = if (role.equals("RESELLER", ignoreCase = true)) {
                        ResellerHomeActivity::class.java
                    } else {
                        HomeActivity::class.java
                    }
                    startActivity(
                        Intent(this@LoginActivity, destination)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    finish()
                }
            },
            onRegisterClick = {
                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            },
        )
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLang = LocaleHelper.getCurrentLanguageTag()

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val responsivePadding = if (config.screenWidthDp > 500) ((config.screenWidthDp - 480) / 2).dp else 24.dp

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsivePadding, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showLanguageDialog = true }) {
                    Text(
                        LocaleHelper.supportedLanguages.firstOrNull { it.code == currentLang }?.nativeName
                            ?: stringResource(R.string.brand_language)
                    )
                }
            }

            Text(text = stringResource(R.string.brand_welcome_back), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.brand_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.brand_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.brand_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = onLoginClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.brand_login_btn))
                }
            }

            TextButton(
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !isLoading,
            ) {
                Text(stringResource(R.string.brand_no_account))
            }
        }
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
