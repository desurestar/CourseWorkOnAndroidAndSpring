package ru.zagrebin.culinaryblog.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import kotlin.math.max
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.MainActivity
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.formatDisplayDate
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityProfileBinding
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.UserProfile
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import ru.zagrebin.culinaryblog.viewmodel.ProfileUiState
import ru.zagrebin.culinaryblog.viewmodel.ProfileViewModel
import javax.inject.Inject
import java.io.ByteArrayOutputStream

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private enum class PostsSubTab { PUBLISHED, DRAFTS }

    private var _binding: ActivityProfileBinding? = null
    private val binding get() = _binding!!
    private val postViewModel: PostViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private var pendingAvatarBitmap: Bitmap? = null
    private var pendingAvatarUri: Uri? = null
    private var followersCount: Int = 0
    private var followingCount: Int = 0
    private var postsSubTab: PostsSubTab = PostsSubTab.PUBLISHED
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingAvatarUri = uri
            startAvatarCrop(uri)
        }
    }
    private val cropAvatarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val bitmap = (data?.extras?.get("data") as? Bitmap)
            ?: data?.data?.let { uri -> decodeBitmap(uri) }
        if (result.resultCode == Activity.RESULT_OK && bitmap != null) {
            handleCroppedAvatar(bitmap)
        } else {
            pendingAvatarUri?.let { uploadAvatar(it) }
        }
        pendingAvatarUri = null
    }
    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (tokenStorage.getToken().isNullOrBlank()) {
            val handled = (activity as? Host)?.let { it.onProfileRequiresAuth(); true } ?: false
            if (!handled) {
                startActivity(Intent(requireContext(), AuthActivity::class.java))
                if (activity !is MainActivity) activity?.finish()
            } else if (activity !is MainActivity) {
                activity?.finish()
            }
            return
        }

        if (activity is MainActivity) {
            binding.profileBottomNavigation.isVisible = false
        } else {
            setupBottomNavigation()
        }
        setupTabs()
        setupPostsSubTabs()
        setupActions()
        setEditingVisible(false)
        observeProfile()
        observeRelations()
        renderUserStub()
        observePosts()
    }

    override fun onResume() {
        super.onResume()
        postViewModel.refreshDrafts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        pendingAvatarBitmap = null
        pendingAvatarUri = null
    }

    private fun setupTabs() {
        val tabs = binding.profileTabs
        tabs.addTab(tabs.newTab().setText(R.string.profile_followers))
        tabs.addTab(tabs.newTab().setText(R.string.profile_following))
        tabs.addTab(tabs.newTab().setText(R.string.profile_posts))
        tabs.addTab(tabs.newTab().setText(R.string.profile_liked))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showSection(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        tabs.getTabAt(2)?.select()
    }

    private fun setupPostsSubTabs() {
        selectPostsSubTab(postsSubTab, rerender = false)
        binding.buttonPostsPublished.setOnClickListener { selectPostsSubTab(PostsSubTab.PUBLISHED) }
        binding.buttonPostsDrafts.setOnClickListener { selectPostsSubTab(PostsSubTab.DRAFTS) }
    }

    private fun selectPostsSubTab(target: PostsSubTab, rerender: Boolean = true) {
        postsSubTab = target
        binding.buttonPostsPublished.isEnabled = target != PostsSubTab.PUBLISHED
        binding.buttonPostsDrafts.isEnabled = target != PostsSubTab.DRAFTS
        val showingPostsTab = binding.profileTabs.selectedTabPosition == 2
        binding.sectionPosts.isVisible = showingPostsTab && target == PostsSubTab.PUBLISHED
        binding.sectionDrafts.isVisible = showingPostsTab && target == PostsSubTab.DRAFTS
        if (rerender) {
            renderPosts(postViewModel.uiState.value)
        }
    }

    private fun setupActions() {
        binding.buttonLogout.setOnClickListener {
            tokenStorage.clearToken()
            (activity as? Host)?.onProfileLogout() ?: activity?.finish()
        }
        binding.buttonChangeAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.buttonSaveProfile.setOnClickListener {
            profileViewModel.saveProfile()
        }
        binding.buttonEditProfile.setOnClickListener {
            setEditingVisible(true)
            binding.editDisplayName.requestFocus()
            binding.profileScroll.smoothScrollTo(0, binding.editDisplayName.top)
        }
        setupInputs()
        renderFollowers(emptyList())
        renderFollowing(emptyList())
    }

    private fun setEditingVisible(show: Boolean) {
        binding.editSection.isVisible = show
    }

    private fun setupInputs() {
        binding.editDisplayName.doAfterTextChanged {
            profileViewModel.displayName.value = it?.toString() ?: ""
        }
        binding.editUsername.doAfterTextChanged {
            profileViewModel.username.value = it?.toString() ?: ""
        }
        binding.editEmail.doAfterTextChanged {
            profileViewModel.email.value = it?.toString() ?: ""
        }
    }

    private fun observeProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.uiState.collectLatest { renderProfile(it) }
            }
        }
    }

    private fun observeRelations() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.followers.collectLatest { renderFollowers(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.following.collectLatest { renderFollowing(it) }
            }
        }
    }

    private fun renderProfile(state: ProfileUiState) {
        binding.profileProgress.isVisible = state.isLoading || state.isSaving
        binding.buttonSaveProfile.isEnabled = !state.isLoading && !state.isSaving
        binding.buttonChangeAvatar.isEnabled = binding.buttonSaveProfile.isEnabled

        binding.profileMessage.isVisible = state.message != null
        binding.profileMessage.text = state.message ?: ""
        binding.profileError.isVisible = state.error != null
        binding.profileError.text = state.error ?: ""

        val user = state.user
        if (user != null) {
            followersCount = user.followersCount
            followingCount = user.followingCount
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.username
                ?: getString(R.string.profile_user_stub)
            binding.profileName.text = displayName
            val emailValue = profileViewModel.email.value.ifBlank { user.email.orEmpty() }
            binding.profileEmail.text = emailValue.ifBlank { getString(R.string.profile_email_stub) }
            binding.editDisplayName.updateTextIfDifferent(profileViewModel.displayName.value)
            binding.editUsername.updateTextIfDifferent(profileViewModel.username.value)
            binding.editEmail.updateTextIfDifferent(profileViewModel.email.value)
            val avatar = profileViewModel.avatarUrl.value ?: user.avatarUrl
            if (!avatar.isNullOrBlank()) {
                pendingAvatarBitmap = null
            }
            renderAvatar(avatar, displayName)
            user.id?.let { profileViewModel.loadRelations(it) }
            renderFollowers(profileViewModel.followers.value)
            renderFollowing(profileViewModel.following.value)
        } else {
            renderUserStub()
            renderAvatar(null, binding.profileName.text?.toString())
            followersCount = 0
            followingCount = 0
            renderFollowers(emptyList())
            renderFollowing(emptyList())
        }
    }

    private fun renderAvatar(avatarUrl: String?, title: String?) {
        val initial = title?.firstOrNull()?.uppercase() ?: "U"
        binding.profileAvatar.text = initial
        pendingAvatarBitmap?.let { bitmap ->
            binding.profileAvatarImage.load(bitmap) {
                placeholder(R.drawable.bg_avatar_placeholder)
                error(R.drawable.bg_avatar_placeholder)
                crossfade(true)
            }
            binding.profileAvatarImage.isVisible = true
            binding.profileAvatar.isVisible = false
            return
        }
        if (avatarUrl.isNullOrBlank()) {
            binding.profileAvatarImage.setImageDrawable(null)
            binding.profileAvatarImage.isVisible = false
            binding.profileAvatar.isVisible = true
            return
        }
        binding.profileAvatar.isVisible = false
        binding.profileAvatarImage.isVisible = true
        binding.profileAvatarImage.load(avatarUrl) {
            placeholder(R.drawable.bg_avatar_placeholder)
            error(R.drawable.bg_avatar_placeholder)
            crossfade(true)
        }
    }

    private fun startAvatarCrop(uri: Uri) {
        val cropIntent = Intent(CROP_ACTION).apply {
            setDataAndType(uri, "image/*")
            putExtra("crop", "true")
            putExtra("aspectX", CROP_ASPECT)
            putExtra("aspectY", CROP_ASPECT)
            putExtra("outputX", CROP_OUTPUT)
            putExtra("outputY", CROP_OUTPUT)
            putExtra("scale", true)
            putExtra("return-data", true)
            putExtra("circleCrop", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val handler = cropIntent.resolveActivity(requireContext().packageManager)
        if (handler == null) {
            uploadAvatar(uri)
            return
        }
        try {
            cropAvatarLauncher.launch(cropIntent)
        } catch (exception: ActivityNotFoundException) {
            Log.w(TAG, "Crop action not available, falling back to direct upload", exception)
            uploadAvatar(uri)
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val resolver = requireContext().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rawSample = max(bounds.outWidth / AVATAR_MAX_SIZE, bounds.outHeight / AVATAR_MAX_SIZE)
            .coerceAtLeast(1)
        val sampleSize = Integer.highestOneBit(rawSample).let { if (it < rawSample) it * 2 else it }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun handleCroppedAvatar(bitmap: Bitmap) {
        pendingAvatarBitmap = bitmap
        renderAvatar(profileViewModel.avatarUrl.value, binding.profileName.text?.toString())
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        uploadAvatarBytes(output.toByteArray(), "image/jpeg")
    }

    private fun generateAvatarFileName(): String = "avatar_${System.currentTimeMillis()}.jpg"

    private fun uploadAvatar(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null || bytes.isEmpty()) {
                binding.profileError.isVisible = true
                binding.profileError.text = getString(R.string.profile_avatar_read_error)
                return@launch
            }
            pendingAvatarBitmap = null
            binding.profileAvatarImage.load(uri) {
                placeholder(R.drawable.bg_avatar_placeholder)
                error(R.drawable.bg_avatar_placeholder)
                crossfade(true)
            }
            binding.profileAvatarImage.isVisible = true
            binding.profileAvatar.isVisible = false
            uploadAvatarBytes(bytes, mimeType)
        }
    }

    private fun uploadAvatarBytes(bytes: ByteArray, mimeType: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            profileViewModel.uploadAvatar(
                generateAvatarFileName(),
                bytes,
                mimeType
            )
        }
    }

    private fun TextView.updateTextIfDifferent(newValue: String) {
        if (text?.toString() != newValue) {
            setText(newValue)
            if (this is com.google.android.material.textfield.TextInputEditText) {
                setSelection(newValue.length)
            }
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
        val currentUserId = profileViewModel.uiState.value.user?.id
        val posts = state.posts.filter { post ->
            currentUserId?.let { post.authorId == it } ?: true
        }
        val liked = state.posts.filter { state.likedIds.contains(it.id) }
        renderPostList(binding.postsList, posts, ::openPost)
        renderPostList(binding.likedList, liked, ::openPost)
        renderDraftList(binding.draftsList, state.drafts)
        val showingPostsTab = binding.profileTabs.selectedTabPosition == 2
        binding.profileEmpty.isVisible = showingPostsTab && when (postsSubTab) {
            PostsSubTab.PUBLISHED -> posts.isEmpty()
            PostsSubTab.DRAFTS -> state.drafts.isEmpty()
        }
    }

    private fun renderPostList(container: LinearLayout, posts: List<PostCard>, onClick: (PostCard) -> Unit) {
        container.removeAllViews()
        if (posts.isEmpty()) {
            val stub = TextView(requireContext())
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        posts.forEach { post ->
            addMiniPostView(container, post) { onClick(post) }
        }
    }

    private fun renderDraftList(container: LinearLayout, drafts: List<PostDraft>) {
        container.removeAllViews()
        if (drafts.isEmpty()) {
            val stub = TextView(requireContext())
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        drafts.forEach { draft ->
            addMiniPostView(container, draft.toCard()) { openDraft(draft) }
        }
    }

    private fun addMiniPostView(container: LinearLayout, post: PostCard, onClick: () -> Unit) {
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
        view.setOnClickListener { onClick() }
        container.addView(view)
    }

    private fun renderFollowers(users: List<UserProfile>) {
        renderUserList(binding.followersList, users, followersCount, getString(R.string.profile_followers))
    }

    private fun renderFollowing(users: List<UserProfile>) {
        renderUserList(binding.followingList, users, followingCount, getString(R.string.profile_following))
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

    private fun showSection(position: Int) {
        binding.sectionFollowers.isVisible = position == 0
        binding.sectionFollowing.isVisible = position == 1
        val showingPostsTab = position == 2
        binding.sectionPosts.isVisible = showingPostsTab && postsSubTab == PostsSubTab.PUBLISHED
        binding.sectionDrafts.isVisible = showingPostsTab && postsSubTab == PostsSubTab.DRAFTS
        binding.sectionLiked.isVisible = position == 3
        if (showingPostsTab) {
            renderPosts(postViewModel.uiState.value)
        } else {
            binding.profileEmpty.isVisible = false
        }
    }

    private fun renderUserStub() {
        binding.profileName.text = getString(R.string.profile_user_stub)
        binding.profileEmail.text = getString(R.string.profile_email_stub)
        binding.profileAvatar.text = binding.profileName.text.firstOrNull()?.uppercase() ?: "U"
        binding.profileAvatarImage.setImageDrawable(null)
        binding.profileAvatarImage.isVisible = false
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(requireContext(), PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
    }

    private fun openDraft(draft: PostDraft) {
        val intent = Intent(requireContext(), CreatePostActivity::class.java)
            .putExtra(CreatePostActivity.EXTRA_DRAFT_ID, draft.id)
            .putExtra(CreatePostActivity.EXTRA_AUTHOR_ID, draft.request.authorId)
        startActivity(intent)
    }

    private fun setupBottomNavigation() {
        binding.profileBottomNavigation.selectedItemId = R.id.menu_profile
        binding.profileBottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_recipes -> {
                    startActivity(
                        Intent(requireContext(), MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_RECIPES)
                    )
                    activity?.finish()
                    true
                }

                R.id.menu_articles -> {
                    startActivity(
                        Intent(requireContext(), MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_ARTICLES)
                    )
                    activity?.finish()
                    true
                }

                R.id.menu_create -> {
                    startActivity(Intent(requireContext(), CreatePostActivity::class.java))
                    activity?.finish()
                    true
                }

                R.id.menu_profile -> true
                else -> false
            }
        }
    }

    companion object {
        private const val TAG = "ProfileFragment"
        private const val CROP_ACTION = "com.android.camera.action.CROP"
        private const val AVATAR_MAX_SIZE = 1024
        private const val CROP_ASPECT = 1
        private const val CROP_OUTPUT = 512
        private const val JPEG_QUALITY = 90
    }

    interface Host {
        fun onProfileRequiresAuth()
        fun onProfileLogout()
        fun onOpenUserProfile(userId: Long, displayName: String?, subscribed: Boolean?)
    }

    private fun openUser(user: UserProfile) {
        val id = user.id ?: return
        (activity as? Host)?.onOpenUserProfile(id, user.displayName ?: user.username, null)
    }
}
