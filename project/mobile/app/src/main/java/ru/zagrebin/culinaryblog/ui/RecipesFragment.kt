package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.databinding.FragmentRecipesBinding
import ru.zagrebin.culinaryblog.viewmodel.RecipesViewModel

@AndroidEntryPoint
class RecipesFragment : Fragment() {
    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = _binding!!
    val recipesViewModel: RecipesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = PostCardAdapter(emptyList()) { /* TODO: handle click */ }
        binding.recipesRecyclerView.adapter = adapter
        binding.recipesSwipeRefresh.setOnRefreshListener {
            recipesViewModel.loadRecipes()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            recipesViewModel.recipes.collect { list ->
                binding.recipesSwipeRefresh.isRefreshing = false
                (binding.recipesRecyclerView.adapter as? PostCardAdapter)?.let {
                    it.items = list
                    it.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
