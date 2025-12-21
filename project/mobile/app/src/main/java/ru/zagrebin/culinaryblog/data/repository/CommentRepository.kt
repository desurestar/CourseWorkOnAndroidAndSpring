package ru.zagrebin.culinaryblog.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.zagrebin.culinaryblog.model.Comment

@Singleton
class CommentRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson
) {

    private val prefs = context.getSharedPreferences("comments_storage", Context.MODE_PRIVATE)
    private val comments = mutableMapOf<Long, MutableStateFlow<List<Comment>>>()
    private val idGenerator = AtomicLong(1_000)
    private val listType = object : TypeToken<List<Comment>>() {}.type

    fun getComments(postId: Long): StateFlow<List<Comment>> = getFlow(postId)

    fun addComment(
        postId: Long,
        author: String,
        message: String,
        parentId: Long? = null
    ): Comment = addComment(postId, author, message, parentId, null)

    fun addComment(
        postId: Long,
        author: String,
        message: String,
        parentId: Long? = null,
        avatarUrl: String? = null
    ): Comment {
        val flow = getFlow(postId)
        val newComment = Comment(
            id = idGenerator.getAndIncrement(),
            postId = postId,
            authorName = author,
            avatarUrl = avatarUrl,
            message = message,
            createdAt = OffsetDateTime.now().toString(),
            parentId = parentId
        )
        flow.value = flow.value + newComment
        persist(postId, flow.value)
        return newComment
    }

    fun updateComment(postId: Long, commentId: Long, newMessage: String): Boolean {
        val flow = getFlow(postId)
        var updated = false
        flow.value = flow.value.map { comment ->
            if (comment.id == commentId) {
                updated = true
                comment.copy(message = newMessage)
            } else {
                comment
            }
        }
        if (updated) {
            persist(postId, flow.value)
        }
        return updated
    }

    fun deleteComment(postId: Long, commentId: Long): Boolean {
        val flow = getFlow(postId)
        val items = flow.value
        if (items.isEmpty()) return false
        val toRemove = collectWithChildren(items, commentId)
        if (toRemove.isEmpty()) return false
        flow.value = items.filterNot { it.id in toRemove }
        persist(postId, flow.value)
        return true
    }

    private fun collectWithChildren(items: List<Comment>, rootId: Long): Set<Long> {
        if (items.none { it.id == rootId }) return emptySet()
        val result = mutableSetOf<Long>()
        fun dfs(id: Long) {
            result.add(id)
            items.filter { it.parentId == id }.forEach { dfs(it.id) }
        }
        dfs(rootId)
        return result
    }

    private fun getFlow(postId: Long): MutableStateFlow<List<Comment>> {
        return comments.getOrPut(postId) {
            val initial = loadComments(postId)
            updateIdGenerator(initial)
            MutableStateFlow(initial)
        }
    }

    private fun loadComments(postId: Long): List<Comment> {
        val raw = prefs.getString(key(postId), null) ?: return seedComments(postId)
        return runCatching { gson.fromJson<List<Comment>>(raw, listType) }.getOrElse { seedComments(postId) }
    }

    private fun persist(postId: Long, items: List<Comment>) {
        prefs.edit { putString(key(postId), gson.toJson(items, listType)) }
        updateIdGenerator(items)
    }

    private fun key(postId: Long): String = "comments_$postId"

    private fun updateIdGenerator(items: List<Comment>) {
        val maxId = items.maxOfOrNull { it.id } ?: return
        idGenerator.updateAndGet { current -> maxOf(current, maxId + 1) }
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
