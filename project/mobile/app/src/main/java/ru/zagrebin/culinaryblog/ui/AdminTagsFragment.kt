package ru.zagrebin.culinaryblog.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.FragmentAdminTagsBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminTagBinding
import ru.zagrebin.culinaryblog.viewmodel.AdminTagsViewModel

@AndroidEntryPoint
class AdminTagsFragment : Fragment() {

    private var _binding: FragmentAdminTagsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminTagsViewModel by viewModels()
    private var selectedColor: String? = null
    private val defaultColorInt by lazy { ContextCompat.getColor(requireContext(), R.color.admin_tag_color_placeholder) }
    private val presetColors by lazy {
        listOf(
            ColorPreset("#F44336", R.string.admin_color_red),
            ColorPreset("#FF9800", R.string.admin_color_orange),
            ColorPreset("#FFEB3B", R.string.admin_color_yellow),
            ColorPreset("#4CAF50", R.string.admin_color_green),
            ColorPreset("#009688", R.string.admin_color_teal),
            ColorPreset("#2196F3", R.string.admin_color_blue),
            ColorPreset("#9C27B0", R.string.admin_color_purple),
            ColorPreset("#9E9E9E", R.string.admin_color_gray)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSearchTags.setOnClickListener {
            viewModel.load(binding.adminTagSearch.text?.toString()?.trim())
        }
        binding.buttonAddTag.setOnClickListener {
            val name = binding.adminTagName.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                viewModel.add(name, normalizeColor(selectedColor))
                binding.adminTagName.setText("")
                applySelectedColor(null)
            }
        }
        binding.buttonPickColor.setOnClickListener { showColorPickerDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }

        applySelectedColor(null)
        if (savedInstanceState == null) {
            viewModel.load()
        }
    }

    private fun render(state: ru.zagrebin.culinaryblog.viewmodel.AdminTagsState) {
        binding.adminTagsProgress.isVisible = state.isLoading
        binding.adminTagsError.isVisible = state.error != null
        binding.adminTagsError.text = state.error ?: ""

        val container = binding.adminTagsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        state.items.forEach { tag ->
            val itemBinding = ItemAdminTagBinding.inflate(inflater, container, false)
            itemBinding.adminTagName.text = tag.name
            val color = tag.color?.takeIf { it.isNotBlank() }
            val parsed = try { Color.parseColor(color ?: "#DDDDDD") } catch (e: Exception) { Color.parseColor("#DDDDDD") }
            itemBinding.adminTagColorDot.setBackgroundColor(parsed)
            itemBinding.buttonDeleteTag.setOnClickListener { viewModel.delete(tag.id) }
            container.addView(itemBinding.root)
        }
    }

    private fun showColorPickerDialog() {
        val labels = buildColorLabels()
        val selectedIndex = presetColors.indexOfFirst { it.hex.equals(selectedColor, ignoreCase = true) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_tags_color_picker_title)
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, which ->
                applySelectedColor(presetColors[which].hex)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.admin_tags_color_clear) { dialog, _ ->
                applySelectedColor(null)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildColorLabels(): List<String> =
        presetColors.map { getString(R.string.admin_tags_color_item, getString(it.labelRes), it.hex.uppercase()) }

    private fun applySelectedColor(color: String?) {
        selectedColor = normalizeColor(color)
        val previewColor = parseColorOrDefault(selectedColor)
        binding.adminTagColorPreview.setBackgroundColor(previewColor)
        val label = presetColors.firstOrNull { it.hex.equals(selectedColor, ignoreCase = true) }
            ?.let { getString(it.labelRes) }
        binding.buttonPickColor.text = label ?: selectedColor ?: getString(R.string.admin_tags_color_default)
    }

    private fun normalizeColor(color: String?): String? = color?.trim()?.ifEmpty { null }

    private fun parseColorOrDefault(raw: String?): Int {
        return try {
            raw?.let { Color.parseColor(it) } ?: defaultColorInt
        } catch (e: Exception) {
            defaultColorInt
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ColorPreset(val hex: String, val labelRes: Int)
}
