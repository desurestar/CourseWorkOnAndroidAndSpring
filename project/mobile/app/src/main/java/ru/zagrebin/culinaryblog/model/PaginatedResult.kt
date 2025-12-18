package ru.zagrebin.culinaryblog.model

data class PaginatedResult<T>(
    val items: List<T>,
    val nextPage: Int?
)
