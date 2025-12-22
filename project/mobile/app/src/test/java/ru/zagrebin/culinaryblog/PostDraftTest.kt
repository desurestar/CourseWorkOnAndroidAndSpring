package ru.zagrebin.culinaryblog

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft

class PostDraftTest {

    @Test
    fun `toCard resolves relative cover url`() {
        val request = PostCreateRequest(
            title = "Title",
            excerpt = "Excerpt",
            content = "Content",
            coverUrl = "/uploads/photo.jpg",
            authorId = 1L
        )

        val card = PostDraft(
            id = 10L,
            updatedAt = 0L,
            request = request
        ).toCard()

        assertEquals("http://192.168.4.103:8080/uploads/photo.jpg", card.coverUrl)
    }
}
