package ru.zagrebin.culinaryblog.data.repository

import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.zagrebin.culinaryblog.model.Comment

@Singleton
class CommentRepository @Inject constructor() {

    private val comments = mutableMapOf<Long, MutableStateFlow<List<Comment>>>()
    private val idGenerator = AtomicLong(1_000)

    fun getComments(postId: Long): StateFlow<List<Comment>> = getFlow(postId)

    fun addComment(postId: Long, author: String, message: String, parentId: Long? = null): Comment {
        val flow = getFlow(postId)
        val newComment = Comment(
            id = idGenerator.getAndIncrement(),
            postId = postId,
            authorName = author,
            message = message,
            createdAt = OffsetDateTime.now().toString(),
            parentId = parentId
        )
        flow.value = flow.value + newComment
        return newComment
    }

    private fun getFlow(postId: Long): MutableStateFlow<List<Comment>> {
        return comments.getOrPut(postId) { MutableStateFlow(seedComments(postId)) }
    }

    private fun seedComments(postId: Long): List<Comment> {
        if (postId % 2L == 0L) return emptyList()
        val root = Comment(
            id = idGenerator.getAndIncrement(),
            postId = postId,
            authorName = "Мария",
            message = "Спасибо за рецепт, получилось отлично!",
            createdAt = OffsetDateTime.now().minusDays(1).toString(),
            parentId = null
        )
        val child = Comment(
            id = idGenerator.getAndIncrement(),
            postId = postId,
            authorName = "Автор",
            message = "Рада, что пригодилось. Попробуйте ещё добавить базилик.",
            createdAt = OffsetDateTime.now().minusHours(10).toString(),
            parentId = root.id
        )
        return listOf(root, child)
    }
}
