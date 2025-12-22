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
import ru.zagrebin.culinaryblog.model.IngredientItem

data class AdminIngredientsState(
    val isLoading: Boolean = false,
    val items: List<IngredientItem> = emptyList(),
    val error: String? = null,
    val search: String? = null
)

@HiltViewModel
class AdminIngredientsViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminIngredientsState())
    val state: StateFlow<AdminIngredientsState> = _state

    fun load(search: String? = _state.value.search) {
        _state.value = _state.value.copy(isLoading = true, error = null, search = search)
        viewModelScope.launch {
            val res = repository.getIngredients(search?.takeIf { it.isNotBlank() })
            if (res.isSuccess) {
                _state.value = _state.value.copy(isLoading = false, items = res.getOrNull().orEmpty())
            } else {
                _state.value = _state.value.copy(isLoading = false, error = res.exceptionOrNull()?.message, items = emptyList())
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch {
            val res = repository.createIngredient(name)
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
            val res = repository.deleteIngredient(id)
            if (res.isSuccess) {
                _state.update { current -> current.copy(items = current.items.filterNot { it.id == id }) }
            } else {
                _state.update { it.copy(error = res.exceptionOrNull()?.message) }
            }
        }
    }
}
