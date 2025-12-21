package ru.zagrebin.culinaryblog.util

import ru.zagrebin.culinaryblog.di.NetworkModule.BASE_URL
import java.net.URI

private val baseOrigin: String by lazy {
    val parsed = runCatching { URI(BASE_URL) }.getOrNull()
    if (parsed != null && !parsed.scheme.isNullOrBlank() && !parsed.authority.isNullOrBlank()) {
        "${parsed.scheme}://${parsed.authority}"
    } else {
        val cleaned = BASE_URL.trim()
        val parts = cleaned.split("://", limit = 2)
        val scheme = if (parts.size == 2 && parts[0].isNotBlank()) parts[0] else "https"
        val remainder = if (parts.size == 2) parts[1] else parts[0]
        val authority = remainder.substringBefore("/")
        if (authority.isNotBlank()) "$scheme://$authority" else cleaned.trimEnd('/')
    }
}

fun resolveUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    val normalizedPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return baseOrigin + normalizedPath
}
