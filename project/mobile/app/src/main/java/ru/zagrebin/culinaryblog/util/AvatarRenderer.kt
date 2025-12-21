package ru.zagrebin.culinaryblog.util

import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import coil.load
import coil.transform.CircleCropTransformation
import ru.zagrebin.culinaryblog.R

fun renderAvatar(imageView: ImageView, initialsView: TextView, avatarUrl: String?, displayName: String?) {
    val resolved = avatarUrl?.takeIf { it.isNotBlank() }
    if (resolved != null) {
        initialsView.isVisible = false
        imageView.isVisible = true
        imageView.load(resolved) {
            placeholder(R.drawable.bg_avatar_placeholder)
            error(R.drawable.bg_avatar_placeholder)
            transformations(CircleCropTransformation())
        }
    } else {
        imageView.isVisible = false
        initialsView.isVisible = true
        initialsView.text = displayName?.firstOrNull()?.uppercase() ?: "?"
    }
}
