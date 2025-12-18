package ru.zagrebin.culinaryblog.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateProfileRequest
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.model.UserProfile

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val user: UserProfile? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState

    val displayName = MutableStateFlow("")
    val email = MutableStateFlow("")
    val username = MutableStateFlow("")
    val avatarUrl = MutableStateFlow<String?>(null)

    init {
        loadProfile()
    }

    fun loadProfile() {
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }
        viewModelScope.launch {
            val res = repository.getProfile()
            res.fold(
                onSuccess = { user ->
                    updateFields(user)
                    _uiState.value = ProfileUiState(user = user, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Нет подключения. Проверьте интернет и попробуйте снова."
                        )
                    }
                }
            )
        }
    }

    fun saveProfile() {
        val request = createUpdateRequest() ?: return
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            sendUpdate(request, "Профиль обновлён")
        }
    }

    fun uploadAvatar(fileName: String, content: ByteArray, mimeType: String) {
        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            val res = repository.uploadAvatar(fileName, content, mimeType)
            res.fold(
                onSuccess = { url ->
                    avatarUrl.value = url
                    createUpdateRequest()?.let { request ->
                        sendUpdate(request.copy(avatarUrl = url), "Аватар обновлён")
                    } ?: _uiState.update { it.copy(isSaving = false) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = e.message ?: "Не удалось загрузить аватар",
                            message = null
                        )
                    }
                }
            )
        }
    }

    private fun createUpdateRequest(): UpdateProfileRequest? {
        val usernameValue = username.value.trim()
        if (usernameValue.isEmpty()) {
            _uiState.update { it.copy(error = "Введите имя пользователя", message = null) }
            return null
        }
        val emailValue = email.value.trim()
        if (emailValue.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            _uiState.update { it.copy(error = "Неверный email", message = null) }
            return null
        }
        return UpdateProfileRequest(
            username = usernameValue,
            email = emailValue.ifBlank { null },
            displayName = displayName.value.trim().ifBlank { null },
            avatarUrl = avatarUrl.value
        )
    }

    private suspend fun sendUpdate(request: UpdateProfileRequest, successMessage: String) {
        val res = repository.updateProfile(request)
        res.fold(
            onSuccess = { user ->
                updateFields(user)
                _uiState.value = ProfileUiState(user = user, isSaving = false, message = successMessage)
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Не удалось сохранить профиль",
                        message = null
                    )
                }
            }
        )
    }

    private fun updateFields(user: UserProfile) {
        displayName.value = user.displayName.orEmpty()
        email.value = user.email.orEmpty()
        username.value = user.username.orEmpty()
        avatarUrl.value = user.avatarUrl
    }
}
