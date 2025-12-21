package ru.zagrebin.culinaryblog.util

import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import ru.zagrebin.culinaryblog.R

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
