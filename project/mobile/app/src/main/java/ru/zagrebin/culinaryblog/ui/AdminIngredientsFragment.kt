package ru.zagrebin.culinaryblog.ui

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
import ru.zagrebin.culinaryblog.databinding.FragmentAdminIngredientsBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminIngredientBinding
import ru.zagrebin.culinaryblog.viewmodel.AdminIngredientsViewModel

@AndroidEntryPoint
class AdminIngredientsFragment : Fragment() {

    private var _binding: FragmentAdminIngredientsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminIngredientsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminIngredientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSearchIngredients.setOnClickListener {
            viewModel.load(binding.adminIngredientSearch.text?.toString()?.trim())
        }
        binding.buttonAddIngredient.setOnClickListener {
            val name = binding.adminIngredientName.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                viewModel.add(name)
                binding.adminIngredientName.text = null
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

    private fun render(state: ru.zagrebin.culinaryblog.viewmodel.AdminIngredientsState) {
        binding.adminIngredientsProgress.isVisible = state.isLoading
        binding.adminIngredientsError.isVisible = state.error != null
        binding.adminIngredientsError.text = state.error ?: ""

        val container = binding.adminIngredientsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        state.items.forEach { ingredient ->
            val itemBinding = ItemAdminIngredientBinding.inflate(inflater, container, false)
            itemBinding.adminIngredientName.text = ingredient.name
            itemBinding.buttonDeleteIngredient.setOnClickListener { viewModel.delete(ingredient.id) }
            container.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
