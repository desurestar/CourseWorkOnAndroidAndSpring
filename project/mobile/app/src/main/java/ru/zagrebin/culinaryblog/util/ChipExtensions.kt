package ru.zagrebin.culinaryblog.util

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import ru.zagrebin.culinaryblog.R

private const val RED_LUMINANCE_WEIGHT = 0.299
private const val GREEN_LUMINANCE_WEIGHT = 0.587
private const val BLUE_LUMINANCE_WEIGHT = 0.114

fun Chip.applyInfoStyle() {
    isCheckable = false
    isClickable = false
    chipBackgroundColor =
        ContextCompat.getColorStateList(context, R.color.recipe_primary_light)
    setTextColor(ContextCompat.getColor(context, R.color.recipe_primary))
    shapeAppearanceModel = shapeAppearanceModel
        .toBuilder()
        .setAllCornerSizes(resources.getDimension(R.dimen.chip_corner_radius))
        .build()
}

fun Chip.applyTagStyle(colorHex: String?) {
    val parsedColor = ColorUtils.hexToColor(colorHex)
    val background = parsedColor
        ?: ContextCompat.getColor(context, R.color.recipe_primary_light)
    chipBackgroundColor = ColorStateList.valueOf(background)
    val luminance = (RED_LUMINANCE_WEIGHT * Color.red(background) +
        GREEN_LUMINANCE_WEIGHT * Color.green(background) +
        BLUE_LUMINANCE_WEIGHT * Color.blue(background)) / 255
    val defaultText = ContextCompat.getColor(context, R.color.recipe_primary)
    val textColor = parsedColor?.let {
        if (luminance < 0.5) Color.WHITE else Color.BLACK
    } ?: defaultText
    setTextColor(textColor)
    shapeAppearanceModel = shapeAppearanceModel
        .toBuilder()
        .setAllCornerSizes(resources.getDimension(R.dimen.chip_corner_radius))
        .build()
}
