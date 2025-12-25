package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ArticlesViewModel @Inject constructor(
    private val postRepository: PostRepository
) : ViewModel() {
    private val _articles = MutableStateFlow<List<PostCard>>(emptyList())
    val articles: StateFlow<List<PostCard>> = _articles

    fun loadArticles() {
        viewModelScope.launch {
            val result = postRepository.getPublishedPosts(filters = ru.zagrebin.culinaryblog.model.PostFilters(postType = "Article"))
            _articles.value = result.getOrNull()?.items ?: emptyList()
        }
    }
}
