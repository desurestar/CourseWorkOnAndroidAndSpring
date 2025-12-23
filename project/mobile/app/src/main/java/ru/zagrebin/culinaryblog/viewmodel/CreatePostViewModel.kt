package ru.zagrebin.culinaryblog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostUpdateRequest
import ru.zagrebin.culinaryblog.model.STATUS_DRAFT
import ru.zagrebin.culinaryblog.model.TagItem

data class CreateFormState(
    val tags: List<TagItem> = emptyList(),
    val ingredients: List<IngredientItem> = emptyList(),
    val loadingTags: Boolean = false,
    val loadingIngredients: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val created: PostCard? = null,
    val draftSaved: PostDraft? = null,
    val updatedPostId: Long? = null
)

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateFormState())
    val state: StateFlow<CreateFormState> = _state

    var authorId: Long = 1L
        private set

    companion object {
        const val GENERIC_ERROR_KEY = "GENERIC_CREATE_ERROR"
    }

    fun setAuthorId(id: Long) {
        authorId = id
    }

    fun loadTags(search: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loadingTags = true, error = null) }
            val res = repository.getTags(search)
            _state.update {
                it.copy(
                    loadingTags = false,
                    tags = res.getOrDefault(emptyList()),
                    error = res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun loadIngredients(search: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loadingIngredients = true, error = null) }
            val res = repository.getIngredients(search)
            _state.update {
                it.copy(
                    loadingIngredients = false,
                    ingredients = res.getOrDefault(emptyList()),
                    error = res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun uploadImage(
        type: String,
        fileName: String,
        content: ByteArray,
        mimeType: String,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.uploadImage(type, fileName, content, mimeType)
            onResult(res)
        }
    }

    fun createPost(request: PostCreateRequest) {
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, created = null, draftSaved = null) }
            val res = repository.createPost(request)
            _state.update {
                if (res.isSuccess) {
                    it.copy(submitting = false, created = res.getOrNull())
                } else {
                    val draft = repository.saveDraft(request).getOrNull()
                    val errorMessage = when {
                        request.status == STATUS_DRAFT && draft != null -> null
                        else -> res.exceptionOrNull()?.message ?: GENERIC_ERROR_KEY
                    }
                    it.copy(
                        submitting = false,
                        error = errorMessage,
                        draftSaved = draft
                    )
                }
            }
        }
    }

    fun updatePost(postId: Long, request: PostUpdateRequest) {
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, updatedPostId = null) }
            val res = repository.updatePost(postId, request)
            _state.update {
                if (res.isSuccess) {
                    it.copy(submitting = false, updatedPostId = postId)
                } else {
                    it.copy(submitting = false, error = res.exceptionOrNull()?.message ?: GENERIC_ERROR_KEY)
                }
            }
        }
    }

    suspend fun getPost(id: Long): Result<PostFull> = repository.getPost(id)

    suspend fun saveDraft(request: PostCreateRequest, draftId: Long? = null): Result<PostDraft> =
        if (draftId != null) repository.updateDraft(draftId, request) else repository.saveDraft(request)

    suspend fun deleteDraft(id: Long): Result<Unit> = repository.deleteDraft(id)

    fun clearCreated() {
        _state.update { it.copy(created = null) }
    }

    fun clearUpdated() {
        _state.update { it.copy(updatedPostId = null) }
    }

    suspend fun getDraft(id: Long): Result<PostDraft> = repository.getDraft(id)
}
