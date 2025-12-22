package ru.zagrebin.culinaryblog

import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import ru.zagrebin.culinaryblog.model.TagItem
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel

class PostViewModelTest {

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
    fun `loadAllPosts keeps mixed types when previous recipe load finishes later`() = runTest(dispatcher) {
        val recipe = samplePost(1L, "recipe")
        val article = samplePost(2L, "article")
        val repository = FakePostRepository(
            publishedResponses = listOf(
                ResponseSpec(delayMillis = 50, posts = listOf(recipe)), // initial load
                ResponseSpec(delayMillis = 0, posts = listOf(recipe, article)) // loadAllPosts
            )
        )

        val viewModel = PostViewModel(repository)

        viewModel.loadAllPosts()
        advanceUntilIdle()

        val posts = viewModel.uiState.value.posts
        assertEquals(2, posts.size)
        assertTrue(posts.any { it.postType == "article" })
    }

    private fun samplePost(id: Long, type: String) = PostCard(
        id = id,
        postType = type,
        status = "published",
        title = "title-$id",
        excerpt = "excerpt-$id",
        content = null,
        coverUrl = null,
        authorId = 10L,
        authorName = "Author",
        authorAvatarUrl = null,
        tags = emptySet(),
        likesCount = 0,
        liked = false,
        publishedAt = "2024-01-01T00:00:00Z",
        viewsCount = 0,
        calories = null,
        cookingTimeMinutes = null
    )
}

private data class ResponseSpec(
    val delayMillis: Long = 0,
    val posts: List<PostCard> = emptyList()
)

private class FakePostRepository(
    publishedResponses: List<ResponseSpec>,
    private val cachedPosts: List<PostCard> = emptyList()
) : PostRepository {

    private val responses = ArrayDeque(publishedResponses)

    override suspend fun getPublishedPosts(
        page: Int,
        pageSize: Int,
        filters: PostFilters?
    ): Result<PaginatedResult<PostCard>> {
        val spec = responses.removeFirstOrNull() ?: ResponseSpec()
        if (spec.delayMillis > 0) delay(spec.delayMillis)
        return Result.success(PaginatedResult(spec.posts, null))
    }

    override suspend fun getCachedPosts(): List<PostCard> = cachedPosts

    override suspend fun getPost(id: Long): Result<PostFull> =
        Result.failure(NotImplementedError("getPost not used"))

    override suspend fun getTags(search: String?): Result<List<TagItem>> =
        Result.failure(NotImplementedError("getTags not used"))

    override suspend fun getIngredients(search: String?): Result<List<IngredientItem>> =
        Result.failure(NotImplementedError("getIngredients not used"))

    override suspend fun uploadImage(type: String, fileName: String, content: ByteArray, mimeType: String): Result<String> =
        Result.failure(NotImplementedError("uploadImage not used"))

    override suspend fun createPost(request: PostCreateRequest): Result<PostCard> =
        Result.failure(NotImplementedError("createPost not used"))

    override suspend fun updatePost(postId: Long, request: PostUpdateRequest): Result<PostFull> =
        Result.failure(NotImplementedError("updatePost not used"))

    override suspend fun deletePost(postId: Long): Result<Unit> =
        Result.failure(NotImplementedError("deletePost not used"))

    override suspend fun saveDraft(request: PostCreateRequest): Result<PostDraft> =
        Result.failure(NotImplementedError("saveDraft not used"))

    override suspend fun updateDraft(id: Long, request: PostCreateRequest): Result<PostDraft> =
        Result.failure(NotImplementedError("updateDraft not used"))

    override suspend fun deleteDraft(id: Long): Result<Unit> =
        Result.failure(NotImplementedError("deleteDraft not used"))

    override suspend fun getDrafts(): Result<List<PostDraft>> = Result.success(emptyList())

    override suspend fun getDraft(id: Long): Result<PostDraft> =
        Result.failure(NotImplementedError("getDraft not used"))

    override suspend fun like(postId: Long): Result<Unit> =
        Result.failure(NotImplementedError("like not used"))

    override suspend fun unlike(postId: Long): Result<Unit> =
        Result.failure(NotImplementedError("unlike not used"))

    override suspend fun getLikedPostIds(): Result<Set<Long>> = Result.success(emptySet())
}
