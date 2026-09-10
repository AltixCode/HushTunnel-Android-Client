package com.v2ray.ang.ui.brand

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val error: String? = null,
)

class RegisterViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun register(onSuccess: (role: String) -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.length < 8) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.brand_error_invalid_credentials))
            return
        }

        launchLoading {
            try {
                val authResult = ApiClient.register(email, password)
                if (authResult.role.equals("ADMIN", ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(
                        error = app.getString(R.string.brand_admin_reject)
                    )
                    return@launchLoading
                }

                AuthStore.saveSession(authResult.token, authResult.userId, authResult.email, authResult.role)
                onSuccess(authResult.role)
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.brand_error_connection))
            }
        }
    }
}
