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
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.FragmentAdminTagsBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminTagBinding
import ru.zagrebin.culinaryblog.util.ColorUtils
import ru.zagrebin.culinaryblog.viewmodel.AdminTagsViewModel

@AndroidEntryPoint
class AdminTagsFragment : Fragment() {

    private var _binding: FragmentAdminTagsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminTagsViewModel by viewModels()
    private var selectedColor: String? = null
    private var defaultColorInt: Int = 0

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
        defaultColorInt = requireContext().getColor(R.color.admin_tag_color_placeholder)
        binding.buttonSearchTags.setOnClickListener {
            viewModel.load(binding.adminTagSearch.text?.toString()?.trim())
        }
        binding.buttonAddTag.setOnClickListener {
            val name = binding.adminTagName.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                viewModel.add(name, selectedColor)
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
            val parsed = ColorUtils.hexToColor(color) ?: defaultColorInt
            itemBinding.adminTagColorDot.setBackgroundColor(parsed)
            itemBinding.buttonDeleteTag.setOnClickListener { viewModel.delete(tag.id) }
            container.addView(itemBinding.root)
        }
    }

    private fun showColorPickerDialog() {
        // Get initial color - use current selected color or default to white
        val initialColor = ColorUtils.hexToColor(selectedColor) ?: Color.WHITE
        
        ColorPickerDialogBuilder
            .with(requireContext())
            .setTitle(getString(R.string.admin_tags_color_picker_title))
            .initialColor(initialColor)
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .setPositiveButton(android.R.string.ok) { _, selectedColorInt, _ ->
                // Convert selected color to #RRGGBB format (stripping alpha)
                val hexColor = ColorUtils.colorToHex(selectedColorInt)
                applySelectedColor(hexColor)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.admin_tags_color_clear) { _, _ ->
                applySelectedColor(null)
            }
            .build()
            .show()
    }

    private fun applySelectedColor(color: String?) {
        // Normalize the color to #RRGGBB format
        selectedColor = ColorUtils.normalizeHexColor(color)
        val previewColor = ColorUtils.hexToColor(selectedColor) ?: defaultColorInt
        binding.adminTagColorPreview.setBackgroundColor(previewColor)
        binding.buttonPickColor.text = selectedColor ?: getString(R.string.admin_tags_color_default)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
