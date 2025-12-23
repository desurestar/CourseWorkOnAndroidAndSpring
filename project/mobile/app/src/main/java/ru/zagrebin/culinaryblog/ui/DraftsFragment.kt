package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.FragmentDraftsBinding
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel

@AndroidEntryPoint
class DraftsFragment : Fragment(), RefreshableTab {

    private var _binding: FragmentDraftsBinding? = null
    private val binding get() = _binding!!
    private val postViewModel: PostViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDraftsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeDrafts()
    }

    override fun refreshContent() {
        // Safety: refresh can be called from Activity before fragment is attached
        if (!isAdded) return
        postViewModel.refreshDrafts(sync = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeDrafts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collectLatest { renderDrafts(it.drafts) }
            }
        }
    }

    private fun renderDrafts(drafts: List<PostDraft>) {
        binding.draftsList.removeAllViews()
        if (drafts.isEmpty()) {
            binding.draftsEmpty.isVisible = true
            return
        }
        binding.draftsEmpty.isVisible = false
        drafts.forEach { draft ->
            val card = draft.toCard()
            val view = layoutInflater.inflate(R.layout.item_post_mini, binding.draftsList, false)

            view.findViewById<TextView>(R.id.miniPostTitle).text =
                card.title.ifBlank { getString(R.string.card_title_placeholder) }
            view.findViewById<TextView>(R.id.miniPostExcerpt).text =
                card.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
            view.findViewById<TextView>(R.id.miniPostMeta).text =
                card.publishedAt ?: getString(R.string.published_unknown)
            view.findViewById<TextView>(R.id.miniPostLikes).text =
                getString(R.string.likes_format, card.likesCount)

            val coverUrl = card.coverUrl?.takeIf { it.isNotBlank() }
            val coverView = view.findViewById<ImageView>(R.id.miniPostCover)
            coverView.isVisible = coverUrl != null
            if (coverUrl != null) {
                coverView.load(coverUrl) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                    crossfade(true)
                }
            } else {
                coverView.setImageDrawable(null)
            }

            view.setOnClickListener { openDraft(draft) }
            binding.draftsList.addView(view)
        }
    }

    private fun openDraft(draft: PostDraft) {
        val intent = Intent(requireContext(), CreatePostActivity::class.java)
            .putExtra(CreatePostActivity.EXTRA_DRAFT_ID, draft.id)
            .putExtra(CreatePostActivity.EXTRA_AUTHOR_ID, draft.request.authorId)
        startActivity(intent)
    }
}