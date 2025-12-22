package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.FragmentAdminPostsBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminPostBinding
import ru.zagrebin.culinaryblog.viewmodel.AdminPostsViewModel

@AndroidEntryPoint
class AdminPostsFragment : Fragment() {

    private var _binding: FragmentAdminPostsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminPostsViewModel by viewModels()
    private val statuses = listOf("draft", "published", "archived")
    private val statusLabels by lazy {
        listOf(
            getString(R.string.admin_status_draft),
            getString(R.string.admin_status_published),
            getString(R.string.admin_status_archived)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPostsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSearchPosts.setOnClickListener {
            viewModel.load(binding.adminPostSearch.text?.toString()?.trim().orEmpty())
        }
        binding.buttonLoadMorePosts.setOnClickListener { viewModel.loadMore() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }

        if (savedInstanceState == null) {
            viewModel.load()
        }
    }

    private fun render(state: ru.zagrebin.culinaryblog.viewmodel.AdminPostsState) {
        binding.adminPostProgress.isVisible = state.isLoading || state.isAppending
        binding.adminPostError.isVisible = state.error != null
        binding.adminPostError.text = state.error ?: ""
        binding.buttonLoadMorePosts.isVisible = state.nextPage != null

        val container = binding.adminPostsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        state.items.forEach { post ->
            val itemBinding = ItemAdminPostBinding.inflate(inflater, container, false)
            itemBinding.adminPostTitle.text = post.title.ifBlank { getString(R.string.card_title_placeholder) }
            val meta = buildString {
                append("#${post.id}")
                append(" • ")
                append(post.postType.ifBlank { "?" })
                append(" • ")
                append(post.createdAt.ifBlank { "" })
                if (post.authorUsername.isNotBlank()) {
                    append(" • ")
                    append(post.authorUsername)
                }
            }
            itemBinding.adminPostMeta.text = meta
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statusLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemBinding.adminPostStatus.adapter = adapter
            val currentIndex = statuses.indexOf(post.status.lowercase())
            itemBinding.adminPostStatus.setSelection(if (currentIndex >= 0) currentIndex else 0, false)
            itemBinding.adminPostStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    val selected = statuses[position]
                    if (!selected.equals(post.status, ignoreCase = true)) {
                        viewModel.updateStatus(post.id, selected)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            itemBinding.buttonDeletePost.setOnClickListener {
                viewModel.delete(post.id)
            }
            container.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
