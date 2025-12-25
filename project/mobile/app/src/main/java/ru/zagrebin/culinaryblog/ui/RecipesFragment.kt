package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ru.zagrebin.culinaryblog.databinding.FragmentRecipesBinding
import ru.zagrebin.culinaryblog.viewmodel.RecipesViewModel

class RecipesFragment : Fragment() {
    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = _binding!!
    private val recipesViewModel: RecipesViewModel by viewModels()

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
        recipesViewModel.recipes.observe(viewLifecycleOwner) { list ->
            binding.recipesSwipeRefresh.isRefreshing = false
            adapter.apply {
                // обновить данные адаптера
                (this as? PostCardAdapter)?.let {
                    it.items = list
                    notifyDataSetChanged()
                }
            }
        }
        recipesViewModel.loadRecipes()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
