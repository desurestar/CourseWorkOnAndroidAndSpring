package ru.zagrebin.culinaryblog.util

import ru.zagrebin.culinaryblog.di.BASE_URL
import java.net.URI

private val baseOrigin: String by lazy {
    try {
        val uri = URI(BASE_URL)
        val authority = uri.authority ?: return@lazy BASE_URL.trimEnd('/')
        "${uri.scheme}://$authority"
    } catch (_: Exception) {
        BASE_URL.trimEnd('/')
    }
}

fun resolveUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()
    val lower = trimmed.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return trimmed
    }
    val normalizedPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return baseOrigin + normalizedPath
}
