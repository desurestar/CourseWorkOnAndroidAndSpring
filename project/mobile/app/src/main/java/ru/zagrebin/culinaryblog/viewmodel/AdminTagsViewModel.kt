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
import ru.zagrebin.culinaryblog.model.TagItem

data class AdminTagsState(
    val isLoading: Boolean = false,
    val items: List<TagItem> = emptyList(),
    val error: String? = null,
    val search: String? = null
)

@HiltViewModel
class AdminTagsViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminTagsState())
    val state: StateFlow<AdminTagsState> = _state

    fun load(search: String? = _state.value.search) {
        _state.value = _state.value.copy(isLoading = true, error = null, search = search)
        viewModelScope.launch {
            val res = repository.getTags(search?.takeIf { it.isNotBlank() })
            if (res.isSuccess) {
                _state.value = _state.value.copy(isLoading = false, items = res.getOrNull().orEmpty())
            } else {
                _state.value = _state.value.copy(isLoading = false, error = res.exceptionOrNull()?.message, items = emptyList())
            }
        }
    }

    fun add(name: String, color: String?) {
        viewModelScope.launch {
            val res = repository.createTag(name, null, color)
            if (res.isSuccess) {
                val item = res.getOrNull() ?: return@launch
                _state.update { current -> current.copy(items = listOf(item) + current.items) }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val res = repository.deleteTag(id)
            if (res.isSuccess) {
                _state.update { current -> current.copy(items = current.items.filterNot { it.id == id }) }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }
}
