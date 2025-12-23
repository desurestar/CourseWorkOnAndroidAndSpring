package ru.zagrebin.culinaryblog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.PostFilters
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostUpdateRequest
import ru.zagrebin.culinaryblog.model.STATUS_DRAFT
import ru.zagrebin.culinaryblog.model.TagItem
import ru.zagrebin.culinaryblog.viewmodel.CreatePostViewModel

class CreatePostViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create draft offline skips error`() = runTest(dispatcher) {
        val repository = DraftOfflineRepository()
        val viewModel = CreatePostViewModel(repository)
        val request = PostCreateRequest(
            title = "Draft title",
            excerpt = "Excerpt",
            content = "Content",
            authorId = 1L,
            status = STATUS_DRAFT
        )

        viewModel.createPost(request)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.error)
        assertNotNull(state.draftSaved)
        assertEquals(1, repository.saveDraftCalls)
    }
}

private class DraftOfflineRepository : PostRepository {
    var saveDraftCalls = 0

    override suspend fun getPublishedPosts(page: Int, pageSize: Int, filters: PostFilters?): Result<PaginatedResult<PostCard>> =
        Result.failure(NotImplementedError("getPublishedPosts not used"))

    override suspend fun getCachedPosts(): List<PostCard> = emptyList()

    override suspend fun getPost(id: Long): Result<PostFull> =
        Result.failure(NotImplementedError("getPost not used"))

    override suspend fun getTags(search: String?): Result<List<TagItem>> =
        Result.failure(NotImplementedError("getTags not used"))

    override suspend fun getIngredients(search: String?): Result<List<IngredientItem>> =
        Result.failure(NotImplementedError("getIngredients not used"))

    override suspend fun uploadImage(type: String, fileName: String, content: ByteArray, mimeType: String): Result<String> =
        Result.failure(NotImplementedError("uploadImage not used"))

    override suspend fun createPost(request: PostCreateRequest): Result<PostCard> =
        Result.failure(RuntimeException("offline"))

    override suspend fun updatePost(postId: Long, request: PostUpdateRequest): Result<PostFull> =
        Result.failure(NotImplementedError("updatePost not used"))

    override suspend fun deletePost(postId: Long): Result<Unit> =
        Result.failure(NotImplementedError("deletePost not used"))

    override suspend fun saveDraft(request: PostCreateRequest): Result<PostDraft> {
        saveDraftCalls++
        return Result.success(PostDraft(id = 1L, updatedAt = 0L, request = request))
    }

    override suspend fun updateDraft(id: Long, request: PostCreateRequest): Result<PostDraft> =
        Result.failure(NotImplementedError("updateDraft not used"))

    override suspend fun deleteDraft(id: Long): Result<Unit> =
        Result.failure(NotImplementedError("deleteDraft not used"))

    override suspend fun getDrafts(authorId: Long?): Result<List<PostDraft>> = Result.success(emptyList())

    override suspend fun getDraft(id: Long): Result<PostDraft> =
        Result.failure(NotImplementedError("getDraft not used"))

    override suspend fun getServerDrafts(): Result<List<PostCard>> =
        Result.failure(NotImplementedError("getServerDrafts not used"))

    override suspend fun syncDrafts(authorId: Long): Result<Int> =
        Result.failure(NotImplementedError("syncDrafts not used"))

    override suspend fun clearDrafts(): Result<Unit> = Result.success(Unit)

    override suspend fun like(postId: Long): Result<Unit> =
        Result.failure(NotImplementedError("like not used"))

    override suspend fun unlike(postId: Long): Result<Unit> =
        Result.failure(NotImplementedError("unlike not used"))

    override suspend fun getLikedPostIds(): Result<Set<Long>> = Result.success(emptySet())
}
