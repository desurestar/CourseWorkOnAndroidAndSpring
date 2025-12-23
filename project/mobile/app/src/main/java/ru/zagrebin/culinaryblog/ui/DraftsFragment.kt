package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
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
import ru.zagrebin.culinaryblog.model.PostCard
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
                postViewModel.uiState.collectLatest { state -> 
                    renderDrafts(state.serverDrafts, state.drafts, state.offline)
                }
            }
        }
    }

    private fun renderDrafts(serverDrafts: List<PostCard>, localDrafts: List<PostDraft>, offline: Boolean) {
        binding.draftsList.removeAllViews()
        
        val allEmpty = serverDrafts.isEmpty() && localDrafts.isEmpty()
        binding.draftsEmpty.isVisible = allEmpty
        
        if (allEmpty) return
        
        // Show server drafts section
        if (serverDrafts.isNotEmpty()) {
            addSectionHeader(getString(R.string.server_drafts_header))
            serverDrafts.forEach { draft ->
                addServerDraftCard(draft)
            }
        }
        
        // Show local drafts section with sync state
        if (localDrafts.isNotEmpty()) {
            addSectionHeader(getString(R.string.local_drafts_header))
            localDrafts.forEach { draft ->
                addLocalDraftCard(draft, offline)
            }
        }
    }
    
    private fun addSectionHeader(title: String) {
        val headerView = layoutInflater.inflate(R.layout.item_section_header, binding.draftsList, false)
        headerView.findViewById<TextView>(R.id.sectionTitle).text = title
        binding.draftsList.addView(headerView)
    }
    
    private fun addServerDraftCard(draft: PostCard) {
        val view = layoutInflater.inflate(R.layout.item_post_mini, binding.draftsList, false)
        view.findViewById<TextView>(R.id.miniPostTitle).text =
            draft.title.ifBlank { getString(R.string.card_title_placeholder) }
        view.findViewById<TextView>(R.id.miniPostExcerpt).text =
            draft.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
        view.findViewById<TextView>(R.id.miniPostMeta).text =
            draft.publishedAt ?: getString(R.string.published_unknown)
        view.findViewById<TextView>(R.id.miniPostLikes).text =
            getString(R.string.likes_format, draft.likesCount)
        val coverUrl = draft.coverUrl?.takeIf { it.isNotBlank() }
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
        view.setOnClickListener { openServerDraft(draft) }
        binding.draftsList.addView(view)
    }
    
    private fun addLocalDraftCard(draft: PostDraft, offline: Boolean) {
        val card = draft.toCard()
        val view = layoutInflater.inflate(R.layout.item_post_mini, binding.draftsList, false)
        
        val titleText = card.title.ifBlank { getString(R.string.card_title_placeholder) }
        val syncIndicator = when (draft.syncState) {
            "SYNCED" -> " ✓"
            "IN_SYNC" -> " ⟳"
            "FAILED" -> " ⚠"
            else -> if (offline) " ⏸" else " ⋯"
        }
        view.findViewById<TextView>(R.id.miniPostTitle).text = titleText + syncIndicator
        
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

    private fun openDraft(draft: PostDraft) {
        val intent = Intent(requireContext(), CreatePostActivity::class.java)
            .putExtra(CreatePostActivity.EXTRA_DRAFT_ID, draft.id)
            .putExtra(CreatePostActivity.EXTRA_AUTHOR_ID, draft.request.authorId)
        startActivity(intent)
    }

    private fun openServerDraft(draft: PostCard) {
        // Server draft ids come from backend and should be positive longs
        if (draft.id < MIN_DRAFT_ID) {
            Log.w(TAG, "Cannot open server draft with invalid id: ${draft.id}")
            return
        }
        val intent = Intent(requireContext(), CreatePostActivity::class.java)
            .putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, draft.id)
        val authorId = draft.authorId
        if (authorId != null) {
            intent.putExtra(CreatePostActivity.EXTRA_AUTHOR_ID, authorId)
        } else {
            Log.w(TAG, "Server draft ${draft.id} missing authorId, will use logged-in user")
        }
        startActivity(intent)
    }

    private companion object {
        const val TAG = "DraftsFragment"
        // Minimum acceptable server draft identifier
        const val MIN_DRAFT_ID = 1L
    }
}
