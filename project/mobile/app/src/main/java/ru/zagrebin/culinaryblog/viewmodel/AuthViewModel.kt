package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import ru.zagrebin.culinaryblog.data.remote.dto.AuthResponse
import ru.zagrebin.culinaryblog.data.repository.AuthRepository
import ru.zagrebin.culinaryblog.data.storage.TokenStorage

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val auth: AuthResponse) : UiState
    data class Error(val message: String) : UiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    val loginUsername = MutableStateFlow("")
    val loginPassword = MutableStateFlow("")

    val regUsername = MutableStateFlow("")
    val regEmail = MutableStateFlow("")
    val regPassword = MutableStateFlow("")
    val regConfirmPassword = MutableStateFlow("")

    private val _loginState = MutableStateFlow<UiState>(UiState.Idle)
    val loginState: StateFlow<UiState> get() = _loginState

    private val _registerState = MutableStateFlow<UiState>(UiState.Idle)
    val registerState: StateFlow<UiState> get() = _registerState

    fun validateLogin(): String? {
        val u = loginUsername.value.trim()
        val p = loginPassword.value
        if (u.isEmpty()) return "Введите имя пользователя или email"
        if (p.length < 6) return "Пароль должен быть минимум 6 символов"
        return null
    }

    fun login() {
        val err = validateLogin()
        if (err != null) {
            _loginState.value = UiState.Error(err)
            return
        }
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            val res = repo.login(loginUsername.value.trim(), loginPassword.value)
            res.fold(
                onSuccess = { response -> handleLoginResponse(response) },
                onFailure = { ex -> _loginState.value = UiState.Error(ex?.message ?: "Ошибка сети") }
            )
        }
    }

    private fun handleLoginResponse(response: Response<AuthResponse>) {
        if (response.isSuccessful) {
            val body = response.body()!!
            tokenStorage.saveToken(body.accessToken)
            _loginState.value = UiState.Success(body)
        } else {
            val msg = try {
                response.errorBody()?.string()
            } catch (_: Exception) {
                null
            } ?: "Ошибка сервера: ${response.code()}"
            _loginState.value = UiState.Error(msg)
        }
    }

    fun validateRegister(): String? {
        val u = regUsername.value.trim()
        val e = regEmail.value.trim()
        val p = regPassword.value
        val c = regConfirmPassword.value
        if (u.isEmpty()) return "Введите имя пользователя"
        if (e.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) return "Неверный email"
        if (p.length < 6) return "Пароль должен быть минимум 6 символов"
        if (p != c) return "Пароли не совпадают"
        return null
    }

    fun register() {
        val err = validateRegister()
        if (err != null) {
            _registerState.value = UiState.Error(err)
            return
        }
        viewModelScope.launch {
            _registerState.value = UiState.Loading
            val res = repo.register(regUsername.value.trim(), regEmail.value.trim(), regPassword.value)
            res.fold(
                onSuccess = { response -> handleRegisterResponse(response) },
                onFailure = { ex -> _registerState.value = UiState.Error(ex?.message ?: "Ошибка сети") }
            )
        }
    }

    private fun handleRegisterResponse(response: Response<AuthResponse>) {
        if (response.isSuccessful) {
            val body = response.body()!!
            tokenStorage.saveToken(body.accessToken)
            _registerState.value = UiState.Success(body)
        } else {
            val msg = try {
                response.errorBody()?.string()
            } catch (_: Exception) {
                null
            } ?: "Ошибка сервера: ${response.code()}"
            _registerState.value = UiState.Error(msg)
        }
    }
}
