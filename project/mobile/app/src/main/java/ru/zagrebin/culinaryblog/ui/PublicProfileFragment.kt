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
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.FragmentPublicProfileBinding
import ru.zagrebin.culinaryblog.formatDisplayDate
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.UserProfile
import ru.zagrebin.culinaryblog.ui.buildUserListDialog
import ru.zagrebin.culinaryblog.util.renderAvatar
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
    private var email: String? = null
    private var avatarUrl: String? = null
    private var subscribed: Boolean = false
    private var usersDialog: AlertDialog? = null
    private var followersCount: Int = 0
    private var followingCount: Int = 0
    private var followers: List<UserProfile> = emptyList()
    private var following: List<UserProfile> = emptyList()
    private var relationsLoaded: Boolean = false

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
        renderSubscription()
        renderCounters()
        observePosts()
        loadUserProfile()
        loadSubscriptionStatus()

        binding.buttonSubscribe.setOnClickListener { toggleSubscription() }
        binding.buttonClose.setOnClickListener {
            (activity as? Host)?.onPublicProfileClose() ?: activity?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.buttonFollowers.setOnClickListener { showFollowersDialog() }
        binding.buttonFollowing.setOnClickListener { showFollowingDialog() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usersDialog?.dismiss()
        usersDialog = null
        _binding = null
    }

    fun updateUser(userId: Long?, displayName: String?, subscribed: Boolean?) {
        val userChanged = this.userId != userId
        this.userId = userId
        displayName?.let { this.displayName = it }
        subscribed?.let { this.subscribed = it }
        if (userChanged) {
            email = null
            avatarUrl = null
            followersCount = 0
            followingCount = 0
            followers = emptyList()
            following = emptyList()
            relationsLoaded = false
        }
        if (_binding != null) {
            renderHeader()
            renderSubscription()
            renderCounters()
            renderPosts(postViewModel.uiState.value)
            loadUserProfile()
            loadSubscriptionStatus()
        }
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

    private fun renderFollowers(users: List<UserProfile>) {
        renderCounters()
    }

    private fun renderFollowing(users: List<UserProfile>) {
        renderCounters()
    }

    private fun showFollowersDialog() {
        showUserListDialog(
            getString(R.string.profile_followers),
            followers,
            followersCount
        )
    }

    private fun showFollowingDialog() {
        showUserListDialog(
            getString(R.string.profile_following),
            following,
            followingCount
        )
    }

    private fun showUserListDialog(title: String, users: List<UserProfile>?, count: Int) {
        usersDialog?.dismiss()
        usersDialog = buildUserListDialog(title, users, count, ::renderUserList) {
            usersDialog = null
        }.also { it.show() }
    }

    private fun renderUserList(container: LinearLayout, users: List<UserProfile>, count: Int, meta: String) {
        container.removeAllViews()
        if (users.isEmpty()) {
            val stub = TextView(requireContext())
            val suffix = if (count > 0) " ($count)" else ""
            stub.text = "$meta: ${getString(R.string.profile_empty)}$suffix"
            container.addView(stub)
            return
        }
        users.forEach { user ->
            val view = layoutInflater.inflate(R.layout.item_profile_mini, container, false)
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.username
                ?: getString(R.string.profile_user_stub)
            val metaText = user.email?.takeIf { it.isNotBlank() }
                ?: user.username
                ?: getString(R.string.profile_email_stub)
            view.findViewById<TextView>(R.id.miniProfileName).text = name
            view.findViewById<TextView>(R.id.miniProfileMeta).text = metaText
            view.findViewById<TextView>(R.id.miniProfileAvatar).text = name.firstOrNull()?.uppercase() ?: "U"
            view.setOnClickListener { openUser(user) }
            container.addView(view)
        }
    }

    private fun renderHeader() {
        val name = displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_user_stub)
        binding.publicName.text = name
        val emailValue = email?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_email_stub)
        binding.publicEmail.text = emailValue
        renderAvatar(binding.publicAvatarImage, binding.publicAvatar, avatarUrl, name)
    }

    private fun renderSubscription() {
        binding.buttonSubscribe.text =
            if (subscribed) getString(R.string.profile_unsubscribe) else getString(R.string.profile_subscribe)
    }

    private fun renderCounters() {
        binding.publicFollowersCount.text = followersCount.toString()
        binding.publicFollowingCount.text = followingCount.toString()
    }

    private fun loadSubscriptionStatus() {
        val id = userId ?: return
        if (tokenStorage.getToken().isNullOrBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = profileRepository.getSubscription(id)
            result.onSuccess {
                subscribed = it.subscribed
                followersCount = it.followersCount
                followingCount = it.followingCount
                renderCounters()
                renderSubscription()
                loadRelations()
            }
        }
    }

    private fun loadUserProfile() {
        val id = userId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = profileRepository.getUserProfile(id)
            result.onSuccess { profile ->
                followersCount = profile.followersCount
                followingCount = profile.followingCount
                email = profile.email
                avatarUrl = profile.avatarUrl
                if (!profile.displayName.isNullOrBlank()) {
                    displayName = profile.displayName
                }
                renderHeader()
                renderCounters()
                loadRelations()
            }
        }
    }

    private fun loadRelations(force: Boolean = false) {
        val id = userId ?: return
        if (!force && relationsLoaded) return
        relationsLoaded = true
        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.getFollowers(id).onSuccess {
                followers = it
                followersCount = it.size
                renderCounters()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.getFollowing(id).onSuccess {
                following = it
                followingCount = it.size
                renderCounters()
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
                    followersCount = it.followersCount
                    followingCount = it.followingCount
                    renderCounters()
                    renderSubscription()
                    loadRelations(true)
                    Toast.makeText(requireContext(), R.string.profile_subscription_updated, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.error_loading, Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.buttonSubscribe.isEnabled = true
            }
        }
    }

    private fun openUser(user: UserProfile) {
        val id = user.id ?: return
        (activity as? Host)?.onOpenUserProfile(id, user.displayName ?: user.username, null)
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(requireContext(), PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
    }

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
