package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.model.PostCard
import javax.inject.Inject

data class PostsUiState(
    val isLoading: Boolean = false,
    val posts: List<PostCard> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class PostViewModel @Inject constructor(
    private val repository: PostRepository
): ViewModel() {

    private val _uiState = MutableStateFlow(PostsUiState(isLoading = true))
    val uiState: StateFlow<PostsUiState> = _uiState

    init {
        loadPosts()
    }

    fun loadPosts() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val res = repository.getPublishedPosts()
            if (res.isSuccess) {
                _uiState.value = PostsUiState(isLoading = false, posts = res.getOrDefault(emptyList()))
            } else {
                _uiState.value = PostsUiState(isLoading = false, error = res.exceptionOrNull()?.message ?: "Unknown")
            }
        }
    }
}