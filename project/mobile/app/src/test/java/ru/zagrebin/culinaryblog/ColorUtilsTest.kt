package ru.zagrebin.culinaryblog

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zagrebin.culinaryblog.util.ColorUtils

/**
 * Unit tests for ColorUtils.
 */
class ColorUtilsTest {

    @Test
    fun hexToColor_validHexRRGGBB_returnsCorrectColor() {
        val hex = "#FF5733"
        val color = ColorUtils.hexToColor(hex)
        assertEquals(Color.parseColor(hex), color)
    }

    @Test
    fun hexToColor_validHexAARRGGBB_returnsCorrectColor() {
        val hex = "#80FF5733"
        val color = ColorUtils.hexToColor(hex)
        assertEquals(Color.parseColor(hex), color)
    }

    @Test
    fun hexToColor_invalidHex_returnsNull() {
        assertNull(ColorUtils.hexToColor("invalid"))
        assertNull(ColorUtils.hexToColor("#GG5733"))
        assertNull(ColorUtils.hexToColor("FF5733"))
    }

    @Test
    fun hexToColor_emptyOrNull_returnsNull() {
        assertNull(ColorUtils.hexToColor(null))
        assertNull(ColorUtils.hexToColor(""))
        assertNull(ColorUtils.hexToColor("   "))
    }

    @Test
    fun colorToHex_withAlpha_stripsAlphaChannel() {
        val color = Color.parseColor("#80FF5733")
        val hex = ColorUtils.colorToHex(color)
        assertEquals("#FF5733", hex)
    }

    @Test
    fun colorToHex_withoutAlpha_returnsCorrectHex() {
        val color = Color.parseColor("#FF5733")
        val hex = ColorUtils.colorToHex(color)
        assertEquals("#FF5733", hex)
    }

    @Test
    fun colorToHex_standardColors_returnsCorrectHex() {
        assertEquals("#FF0000", ColorUtils.colorToHex(Color.RED))
        assertEquals("#00FF00", ColorUtils.colorToHex(Color.GREEN))
        assertEquals("#0000FF", ColorUtils.colorToHex(Color.BLUE))
        assertEquals("#FFFFFF", ColorUtils.colorToHex(Color.WHITE))
        assertEquals("#000000", ColorUtils.colorToHex(Color.BLACK))
    }

    @Test
    fun isValidHexColor_validRRGGBB_returnsTrue() {
        assertTrue(ColorUtils.isValidHexColor("#FF5733"))
        assertTrue(ColorUtils.isValidHexColor("#000000"))
        assertTrue(ColorUtils.isValidHexColor("#FFFFFF"))
        assertTrue(ColorUtils.isValidHexColor("#aabbcc"))
    }

    @Test
    fun isValidHexColor_invalidFormats_returnsFalse() {
        assertFalse(ColorUtils.isValidHexColor("#80FF5733")) // 8 chars (AARRGGBB)
        assertFalse(ColorUtils.isValidHexColor("FF5733")) // Missing #
        assertFalse(ColorUtils.isValidHexColor("#FF573")) // Too short
        assertFalse(ColorUtils.isValidHexColor("#GG5733")) // Invalid chars
        assertFalse(ColorUtils.isValidHexColor(null))
        assertFalse(ColorUtils.isValidHexColor(""))
    }

    @Test
    fun normalizeHexColor_validRRGGBB_returnsUppercase() {
        assertEquals("#FF5733", ColorUtils.normalizeHexColor("#ff5733"))
        assertEquals("#AABBCC", ColorUtils.normalizeHexColor("#aabbcc"))
    }

    @Test
    fun normalizeHexColor_validAARRGGBB_stripsAlpha() {
        assertEquals("#FF5733", ColorUtils.normalizeHexColor("#80FF5733"))
        assertEquals("#AABBCC", ColorUtils.normalizeHexColor("#FFAABBCC"))
    }

    @Test
    fun normalizeHexColor_invalidFormats_returnsNull() {
        assertNull(ColorUtils.normalizeHexColor("invalid"))
        assertNull(ColorUtils.normalizeHexColor("#GG5733"))
        assertNull(ColorUtils.normalizeHexColor("FF5733"))
        assertNull(ColorUtils.normalizeHexColor(null))
        assertNull(ColorUtils.normalizeHexColor(""))
    }

    @Test
    fun normalizeHexColor_withSpaces_trimsAndNormalizes() {
        assertEquals("#FF5733", ColorUtils.normalizeHexColor("  #ff5733  "))
    }
}
