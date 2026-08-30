package com.v2ray.ang.ui.brand

import android.app.Application
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

    fun register(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.length < 8) {
            _uiState.value = _uiState.value.copy(error = "Enter an email and a password of at least 8 characters")
            return
        }

        launchLoading {
            try {
                val (token, savedEmail) = ApiClient.register(email, password)
                AuthStore.saveSession(token, savedEmail)
                onSuccess()
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Couldn't reach the server — check your connection")
            }
        }
    }
}
