package ru.zagrebin.culinaryblog.ui

import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.zagrebin.culinaryblog.databinding.DialogUserListBinding
import ru.zagrebin.culinaryblog.model.UserProfile

fun Fragment.buildUserListDialog(
    title: String,
    users: List<UserProfile>?,
    count: Int,
    render: (LinearLayout, List<UserProfile>, Int, String) -> Unit,
    onDismiss: () -> Unit
): AlertDialog {
    val dialogBinding = DialogUserListBinding.inflate(layoutInflater)
    render(dialogBinding.dialogUserList, users ?: emptyList(), count, title)
    return MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setView(dialogBinding.root)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener { onDismiss() }
        .create()
}
