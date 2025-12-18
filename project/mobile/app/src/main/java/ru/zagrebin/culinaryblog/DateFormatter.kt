package ru.zagrebin.culinaryblog

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

fun formatDisplayDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return parseOffsetDate(raw) ?: parseLocalDate(raw) ?: fallbackFormat(raw)
}

private fun parseOffsetDate(raw: String): String? = try {
    OffsetDateTime.parse(raw).format(displayDateFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun parseLocalDate(raw: String): String? = try {
    LocalDate.parse(raw).format(displayDateFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun fallbackFormat(raw: String): String? {
    val datePart = raw.substringBefore("T").takeIf { it.isNotBlank() } ?: return null
    return datePart.replace("-", ".")
}
