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
import ru.zagrebin.culinaryblog.databinding.FragmentArticlesBinding
import ru.zagrebin.culinaryblog.viewmodel.ArticlesViewModel

@AndroidEntryPoint
class ArticlesFragment : Fragment() {
    private var _binding: FragmentArticlesBinding? = null
    private val binding get() = _binding!!
    val articlesViewModel: ArticlesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = PostCardAdapter(emptyList()) { /* TODO: handle click */ }
        binding.articlesRecyclerView.adapter = adapter
        binding.articlesSwipeRefresh.setOnRefreshListener {
            articlesViewModel.loadArticles()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            articlesViewModel.articles.collect { list ->
                binding.articlesSwipeRefresh.isRefreshing = false
                (binding.articlesRecyclerView.adapter as? PostCardAdapter)?.let {
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
