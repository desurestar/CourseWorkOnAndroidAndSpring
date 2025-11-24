package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.Post
import java.time.LocalDateTime

fun PostDto.toDomain(): Post {
    return Post(
        id = id,
        title = title,
        author = author,
        summary = summary,
        content = content,
        imageUrl = imageUrl,
        tags = tags,
        likes = likes,
        createdAt = LocalDateTime.parse(createdAt)
    )
}