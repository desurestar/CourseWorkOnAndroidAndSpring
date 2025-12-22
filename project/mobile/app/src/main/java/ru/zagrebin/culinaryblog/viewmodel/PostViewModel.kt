package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.repository.OfflineCacheException
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostFilters
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
    private var activeFilters: PostFilters? = null
    private var activePostType: String? = DEFAULT_POST_TYPE
    private var currentUserId: Long? = null
    private var currentLoadJob: Job? = null

    init {
        loadPosts(postType = DEFAULT_POST_TYPE)
    }

    fun loadAllPosts() = loadPosts(filters = null, postType = null, allowAnyType = true)

    fun loadPosts(
        filters: PostFilters? = activeFilters,
        postType: String? = activePostType,
        allowAnyType: Boolean = false
    ) {
        currentLoadJob?.cancel()
        val params = resolveLoadParams(filters, postType, allowAnyType)
        activeFilters = params.normalizedFilters
        activePostType = params.resolvedPostType
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isAppending = false,
            error = null,
            nextPage = 1
        )
        currentLoadJob = viewModelScope.launch {
            val likedIds = repository.getLikedPostIds().getOrDefault(emptySet())
            val drafts = loadDrafts(currentUserId)
            val cached = repository.getCachedPosts()
            val cachedFiltered = if (params.skipTypeFilters) {
                cached
            } else {
                PostFilters.filter(cached, params.normalizedFilters, params.targetType)
            }
            if (cachedFiltered.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    posts = cachedFiltered,
                    likedIds = likedIds,
                    drafts = drafts,
                    offline = false
                )
            }
            val res = repository.getPublishedPosts(filters = params.normalizedFilters)
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                val mergedLikedIds = likedIds + page.items.filter { it.liked }.map { it.id }.toSet()
                _uiState.value = PostsUiState(
                    isLoading = false,
                    isAppending = false,
                    posts = page.items,
                    nextPage = page.nextPage,
                    likedIds = mergedLikedIds,
                    drafts = drafts,
                    offline = false
                )
            } else {
                val cachedFallback = when (val ex = res.exceptionOrNull()) {
                    is OfflineCacheException -> ex.cached
                    else -> if (cachedFiltered.isNotEmpty()) cachedFiltered else repository.getCachedPosts()
                }
                val mergedLikedIds = likedIds + cachedFallback.filter { it.liked }.map { it.id }.toSet()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAppending = false,
                    posts = if (params.skipTypeFilters) cachedFallback else PostFilters.filter(cachedFallback, params.normalizedFilters, params.targetType),
                    nextPage = null,
                    error = res.exceptionOrNull()?.message ?: "Unknown",
                    likedIds = mergedLikedIds,
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
            val res = repository.getPublishedPosts(page = pageToLoad, filters = activeFilters)
            if (res.isSuccess) {
                val page = res.getOrDefault(PaginatedResult(emptyList(), null))
                _uiState.update { current ->
                    val mergedLikedIds = current.likedIds + page.items.filter { it.liked }.map { it.id }.toSet()
                    current.copy(
                        isAppending = false,
                        posts = current.posts + page.items,
                        nextPage = page.nextPage,
                        likedIds = mergedLikedIds
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

    fun refreshDrafts(sync: Boolean = false) {
        viewModelScope.launch {
            val drafts = loadDrafts(currentUserId, sync)
            _uiState.update { it.copy(drafts = drafts) }
        }
    }

    fun setCurrentUser(userId: Long?) {
        if (currentUserId == userId) return
        currentUserId = userId
        if (userId == null) {
            _uiState.update { it.copy(drafts = emptyList()) }
            return
        }
        refreshDrafts(sync = true)
    }

    companion object {
        private const val DEFAULT_POST_TYPE = PostFilters.RECIPE_POST_TYPE
    }

    private data class LoadParams(
        val skipTypeFilters: Boolean,
        val normalizedFilters: PostFilters?,
        val targetType: String,
        val resolvedPostType: String?
    )

    private fun resolveLoadParams(
        filters: PostFilters?,
        postType: String?,
        allowAnyType: Boolean
    ): LoadParams {
        val skipTypeFilters = shouldSkipTypeFiltering(allowAnyType, postType, filters)
        val resolvedPostType = if (skipTypeFilters) null else postType ?: DEFAULT_POST_TYPE
        val targetType = resolvedPostType ?: DEFAULT_POST_TYPE
        val normalizedFilters = if (skipTypeFilters) {
            null
        } else {
            (filters ?: PostFilters(postType = targetType)).normalizedForType(targetType)
        }
        return LoadParams(skipTypeFilters, normalizedFilters, targetType, resolvedPostType)
    }

    private fun shouldSkipTypeFiltering(
        allowAnyType: Boolean,
        postType: String?,
        filters: PostFilters?
    ): Boolean = allowAnyType && postType == null && filters == null

    private suspend fun loadDrafts(authorId: Long?, sync: Boolean = false): List<PostDraft> {
        if (authorId == null) return emptyList()
        if (sync) repository.syncDrafts(authorId)
        return repository.getDrafts(authorId).getOrDefault(emptyList())
    }
}
