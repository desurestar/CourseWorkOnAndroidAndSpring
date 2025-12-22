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
import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.PaginatedResult

data class AdminPostsState(
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val items: List<AdminPost> = emptyList(),
    val nextPage: Int? = 1,
    val error: String? = null,
    val search: String? = null
)

@HiltViewModel
class AdminPostsViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminPostsState())
    val state: StateFlow<AdminPostsState> = _state

    fun load(search: String? = _state.value.search) {
        _state.value = _state.value.copy(isLoading = true, error = null, search = search, nextPage = 1)
        viewModelScope.launch {
            val res = repository.getPosts(page = 1, search = search?.takeIf { it.isNotBlank() })
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = page.items,
                    nextPage = page.nextPage,
                    error = null
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
            val res = repository.getPosts(page = next, search = _state.value.search)
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
                _state.update { current ->
                    current.copy(isAppending = false, error = res.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun updateStatus(id: Long, status: String) {
        viewModelScope.launch {
            val res = repository.updatePostStatus(id, status)
            if (res.isSuccess) {
                _state.update { current ->
                    current.copy(items = current.items.map { if (it.id == id) it.copy(status = status) else it })
                }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val res = repository.deletePost(id)
            if (res.isSuccess) {
                _state.update { current -> current.copy(items = current.items.filterNot { it.id == id }) }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }
}
