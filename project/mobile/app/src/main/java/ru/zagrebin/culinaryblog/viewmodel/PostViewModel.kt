package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.repository.OfflineCacheException
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostDraft
import javax.inject.Inject

data class PostsUiState(
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val posts: List<PostCard> = emptyList(),
    val nextPage: Int? = 1,
    val error: String? = null,
    val likedIds: Set<Long> = emptySet(),
    val drafts: List<PostDraft> = emptyList(),
    val offline: Boolean = false
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
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isAppending = false,
            error = null,
            nextPage = 1
        )
        viewModelScope.launch {
            val likedIds = repository.getLikedPostIds().getOrDefault(emptySet())
            val drafts = repository.getDrafts().getOrDefault(emptyList())
            val cached = repository.getCachedPosts()
            if (cached.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    posts = cached,
                    likedIds = likedIds,
                    drafts = drafts,
                    offline = false
                )
            }
            val res = repository.getPublishedPosts()
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                _uiState.value = PostsUiState(
                    isLoading = false,
                    isAppending = false,
                    posts = page.items,
                    nextPage = page.nextPage,
                    likedIds = likedIds,
                    drafts = drafts,
                    offline = false
                )
            } else {
                val cachedFallback = when (val ex = res.exceptionOrNull()) {
                    is OfflineCacheException -> ex.cached
                    else -> if (cached.isNotEmpty()) cached else repository.getCachedPosts()
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAppending = false,
                    posts = cachedFallback,
                    nextPage = null,
                    error = res.exceptionOrNull()?.message ?: "Unknown",
                    likedIds = likedIds,
                    drafts = drafts,
                    offline = true
                )
            }
        }
    }

    fun loadNextPage() {
        var targetPage: Int? = null
        _uiState.update { state ->
            if (state.isLoading || state.isAppending || state.nextPage == null) {
                state
            } else {
                targetPage = state.nextPage
                state.copy(isAppending = true, error = null)
            }
        }

        val pageToLoad = targetPage ?: return
        viewModelScope.launch {
            val res = repository.getPublishedPosts(page = pageToLoad)
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                _uiState.update { current ->
                    current.copy(
                        isAppending = false,
                        posts = current.posts + page.items,
                        nextPage = page.nextPage
                    )
                }
            } else {
                _uiState.update { current ->
                    current.copy(
                        isAppending = false,
                        error = res.exceptionOrNull()?.message ?: "Unknown"
                    )
                }
            }
        }
    }

    fun refreshDrafts() {
        viewModelScope.launch {
            val drafts = repository.getDrafts().getOrDefault(emptyList())
            _uiState.update { it.copy(drafts = drafts) }
        }
    }
}
