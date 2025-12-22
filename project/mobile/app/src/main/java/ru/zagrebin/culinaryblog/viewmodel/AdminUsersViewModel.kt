package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.repository.AdminRepository
import ru.zagrebin.culinaryblog.model.AdminUser
import ru.zagrebin.culinaryblog.model.PaginatedResult

data class AdminUsersState(
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val items: List<AdminUser> = emptyList(),
    val nextPage: Int? = 1,
    val error: String? = null,
    val search: String? = null
)

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersState())
    val state: StateFlow<AdminUsersState> = _state

    fun load(search: String? = _state.value.search) {
        _state.value = _state.value.copy(isLoading = true, error = null, search = search, nextPage = 1)
        viewModelScope.launch {
            val res = repository.getUsers(page = 1, search = search?.takeIf { it.isNotBlank() })
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = page.items,
                    nextPage = page.nextPage
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = emptyList(),
                    nextPage = null,
                    error = res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun loadMore() {
        val next = _state.value.nextPage ?: return
        if (_state.value.isAppending) return
        _state.update { it.copy(isAppending = true, error = null) }
        viewModelScope.launch {
            val res = repository.getUsers(page = next, search = _state.value.search)
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                _state.update { current ->
                    current.copy(
                        isAppending = false,
                        items = current.items + page.items,
                        nextPage = page.nextPage
                    )
                }
            } else {
                _state.update { current -> current.copy(isAppending = false, error = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun toggleAdmin(id: Long, isAdmin: Boolean) {
        viewModelScope.launch {
            val res = repository.updateUserRole(id, if (isAdmin) "admin" else "user")
            if (res.isSuccess) {
                val user = res.getOrNull() ?: return@launch
                _state.update { current ->
                    current.copy(items = current.items.map { if (it.id == user.id) user else it })
                }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val res = repository.deleteUser(id)
            if (res.isSuccess) {
                _state.update { current -> current.copy(items = current.items.filterNot { it.id == id }) }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }
}
