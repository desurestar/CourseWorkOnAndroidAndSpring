package ru.zagrebin.culinaryblog.util

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Utility functions for converting between Color integers and #RRGGBB hex strings.
 * Ensures compatibility with server format (VARCHAR(7), no alpha channel).
 */
object ColorUtils {

    /**
     * Converts a hex color string to a Color integer.
     * Accepts formats: #RRGGBB or #AARRGGBB
     * @param hex The hex color string (e.g., "#FF5733" or "#80FF5733")
     * @return Color integer, or null if parsing fails
     */
    @ColorInt
    fun hexToColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Converts a Color integer to a #RRGGBB hex string (7 characters).
     * Strips alpha channel to maintain server compatibility.
     * @param color The color integer
     * @return Hex string in format #RRGGBB (e.g., "#FF5733")
     */
    fun colorToHex(@ColorInt color: Int): String {
        // Extract RGB components, ignoring alpha
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return String.format("#%02X%02X%02X", red, green, blue)
    }

    /**
     * Validates if a hex color string is in the correct #RRGGBB format.
     * @param hex The hex string to validate
     * @return true if valid #RRGGBB format, false otherwise
     */
    fun isValidHexColor(hex: String?): Boolean {
        if (hex.isNullOrBlank()) return false
        // Match #RRGGBB format (exactly 7 characters)
        return hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))
    }

    /**
     * Normalizes a hex color string to #RRGGBB format.
     * If the string is #AARRGGBB, strips the alpha channel.
     * @param hex The hex string to normalize
     * @return Normalized #RRGGBB string, or null if invalid
     */
    fun normalizeHexColor(hex: String?): String? {
        if (hex.isNullOrBlank()) return null
        val trimmed = hex.trim()
        
        // If already in #RRGGBB format, return as-is
        if (isValidHexColor(trimmed)) {
            return trimmed.uppercase()
        }
        
        // If in #AARRGGBB format, strip alpha
        if (trimmed.matches(Regex("^#[0-9A-Fa-f]{8}$"))) {
            return "#${trimmed.substring(3)}".uppercase()
        }
        
        return null
    }
}
