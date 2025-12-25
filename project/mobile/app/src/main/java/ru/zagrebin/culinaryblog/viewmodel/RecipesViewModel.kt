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
class RecipesViewModel @Inject constructor(
    private val postRepository: PostRepository
) : ViewModel() {
    private val _recipes = MutableStateFlow<List<PostCard>>(emptyList())
    val recipes: StateFlow<List<PostCard>> = _recipes

    fun loadRecipes() {
        viewModelScope.launch {
            _recipes.value = postRepository.getPosts(postType = "Recipe")
        }
    }
}
