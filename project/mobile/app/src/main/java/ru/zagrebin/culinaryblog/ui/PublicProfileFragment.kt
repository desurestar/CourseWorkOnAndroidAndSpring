package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.databinding.FragmentPublicProfileBinding
import ru.zagrebin.culinaryblog.formatDisplayDate
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import javax.inject.Inject

@AndroidEntryPoint
class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!
    private val postViewModel: PostViewModel by viewModels()
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var tokenStorage: TokenStorage

    private var userId: Long? = null
    private var displayName: String? = null
    private var subscribed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            userId = bundle.getLong(ARG_USER_ID).takeIf { it > 0 }
            displayName = bundle.getString(ARG_DISPLAY_NAME)
            subscribed = bundle.getBoolean(ARG_SUBSCRIBED, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublicProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderHeader()
        setupTabs()
        renderSubscription()
        renderFollowers(followersStub())
        renderFollowing(followingStub())
        observePosts()
        loadSubscriptionStatus()

        binding.buttonSubscribe.setOnClickListener { toggleSubscription() }
        binding.buttonClose.setOnClickListener {
            (activity as? Host)?.onPublicProfileClose() ?: activity?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.publicTabs.getTabAt(0)?.select()
        showSection(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateUser(userId: Long?, displayName: String?, subscribed: Boolean?) {
        this.userId = userId
        this.displayName = displayName
        subscribed?.let { this.subscribed = it }
        if (_binding != null) {
            renderHeader()
            renderSubscription()
            renderFollowers(followersStub())
            renderFollowing(followingStub())
            renderPosts(postViewModel.uiState.value)
            binding.publicTabs.getTabAt(0)?.select()
            showSection(0)
            loadSubscriptionStatus()
        }
    }

    private fun setupTabs() {
        val tabs = binding.publicTabs
        tabs.removeAllTabs()
        tabs.addTab(tabs.newTab().setText(R.string.profile_followers))
        tabs.addTab(tabs.newTab().setText(R.string.profile_following))
        tabs.addTab(tabs.newTab().setText(R.string.profile_posts))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showSection(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showSection(position: Int) {
        binding.publicSectionFollowers.isVisible = position == 0
        binding.publicSectionFollowing.isVisible = position == 1
        binding.publicSectionPosts.isVisible = position == 2
    }

    private fun observePosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collectLatest { renderPosts(it) }
            }
        }
    }

    private fun renderPosts(state: PostsUiState) {
        val authorPosts = state.posts.filter { post ->
            userId?.let { post.authorId == it } ?: true
        }
        renderPostList(binding.publicPostsList, authorPosts)
        binding.publicEmpty.isVisible = authorPosts.isEmpty()
    }

    private fun renderPostList(container: LinearLayout, posts: List<PostCard>) {
        container.removeAllViews()
        if (posts.isEmpty()) {
            val stub = TextView(requireContext())
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        posts.forEach { post ->
            val view = layoutInflater.inflate(R.layout.item_post_mini, container, false)
            view.findViewById<TextView>(R.id.miniPostTitle).text =
                post.title.ifBlank { getString(R.string.card_title_placeholder) }
            view.findViewById<TextView>(R.id.miniPostExcerpt).text =
                post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
            view.findViewById<TextView>(R.id.miniPostMeta).text =
                formatDisplayDate(post.publishedAt) ?: getString(R.string.published_unknown)
            view.findViewById<TextView>(R.id.miniPostLikes).text =
                getString(R.string.likes_format, post.likesCount)
            val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
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
            view.setOnClickListener { openPost(post) }
            container.addView(view)
        }
    }

    private fun renderFollowers(items: List<ProfileListItem>) {
        renderSimpleList(binding.publicFollowersList, items, getString(R.string.profile_followers))
    }

    private fun renderFollowing(items: List<ProfileListItem>) {
        renderSimpleList(binding.publicFollowingList, items, getString(R.string.profile_following))
    }

    private fun renderSimpleList(container: LinearLayout, items: List<ProfileListItem>, meta: String) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val stub = TextView(requireContext())
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        items.forEach { user ->
            val view = layoutInflater.inflate(R.layout.item_profile_mini, container, false)
            view.findViewById<TextView>(R.id.miniProfileName).text = user.name
            view.findViewById<TextView>(R.id.miniProfileAvatar).text = user.name.firstOrNull()?.uppercase() ?: "?"
            view.findViewById<TextView>(R.id.miniProfileMeta).text = meta
            view.setOnClickListener { openUser(user) }
            container.addView(view)
        }
    }

    private fun renderHeader() {
        val name = displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_user_stub)
        binding.publicName.text = name
        binding.publicAvatar.text = name.firstOrNull()?.uppercase() ?: "U"
        binding.publicMeta.text =
            if (subscribed) getString(R.string.profile_following) else getString(R.string.nav_profile)
    }

    private fun renderSubscription() {
        binding.buttonSubscribe.text =
            if (subscribed) getString(R.string.profile_unsubscribe) else getString(R.string.profile_subscribe)
        binding.publicMeta.text =
            if (subscribed) getString(R.string.profile_following) else getString(R.string.nav_profile)
    }

    private fun loadSubscriptionStatus() {
        val id = userId ?: return
        if (tokenStorage.getToken().isNullOrBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = profileRepository.getSubscription(id)
            result.onSuccess {
                subscribed = it.subscribed
                renderSubscription()
            }
        }
    }

    private fun toggleSubscription() {
        val id = userId ?: return
        if (tokenStorage.getToken().isNullOrBlank()) {
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            binding.buttonSubscribe.isEnabled = false
            try {
                val result = if (subscribed) profileRepository.unsubscribe(id) else profileRepository.subscribe(id)
                result.onSuccess {
                    subscribed = it.subscribed
                    renderSubscription()
                    Toast.makeText(requireContext(), R.string.profile_subscription_updated, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.error_loading, Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.buttonSubscribe.isEnabled = true
            }
        }
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(requireContext(), PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
    }

    private fun openUser(user: ProfileListItem) {
        if (user.id <= 0) return
        (activity as? Host)?.onOpenUserProfile(user.id, user.name, user.subscribed)
    }

    private fun followersStub(): List<ProfileListItem> = listOf(
        ProfileListItem(2L, "Алексей"),
        ProfileListItem(3L, "Мария"),
        ProfileListItem(4L, "Владимир")
    )

    private fun followingStub(): List<ProfileListItem> = listOf(
        ProfileListItem(5L, "Иван", subscribed = true),
        ProfileListItem(6L, "Дарья", subscribed = true)
    )

    interface Host {
        fun onPublicProfileClose()
        fun onOpenUserProfile(userId: Long, displayName: String?, subscribed: Boolean?)
    }

    companion object {
        private const val ARG_USER_ID = "arg_user_id"
        private const val ARG_DISPLAY_NAME = "arg_display_name"
        private const val ARG_SUBSCRIBED = "arg_subscribed"

        fun newInstance(
            userId: Long?,
            displayName: String?,
            subscribed: Boolean?
        ): PublicProfileFragment {
            val fragment = PublicProfileFragment()
            fragment.arguments = Bundle().apply {
                userId?.let { putLong(ARG_USER_ID, it) }
                putString(ARG_DISPLAY_NAME, displayName)
                subscribed?.let { putBoolean(ARG_SUBSCRIBED, it) }
            }
            return fragment
        }
    }
}
