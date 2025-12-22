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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.databinding.FragmentAdminTagsBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminTagBinding
import ru.zagrebin.culinaryblog.viewmodel.AdminTagsViewModel

@AndroidEntryPoint
class AdminTagsFragment : Fragment() {

    private var _binding: FragmentAdminTagsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminTagsViewModel by viewModels()

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
                viewModel.add(name, binding.adminTagColor.text?.toString()?.trim())
                binding.adminTagName.setText("")
                binding.adminTagColor.setText("")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
