package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import ru.zagrebin.culinaryblog.MainActivity
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityProfileBinding
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.ui.CreatePostActivity
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import ru.zagrebin.culinaryblog.viewmodel.ProfileUiState
import ru.zagrebin.culinaryblog.viewmodel.ProfileViewModel
import javax.inject.Inject

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val postViewModel: PostViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadAvatar(uri)
        }
    }
    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (tokenStorage.getToken().isNullOrBlank()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupTabs()
        setupActions()
        observeProfile()
        renderUserStub()
        observePosts()
    }

    private fun setupTabs() {
        val tabs = binding.profileTabs
        tabs.addTab(tabs.newTab().setText(R.string.profile_followers))
        tabs.addTab(tabs.newTab().setText(R.string.profile_following))
        tabs.addTab(tabs.newTab().setText(R.string.profile_posts))
        tabs.addTab(tabs.newTab().setText(R.string.profile_liked))
        tabs.addTab(tabs.newTab().setText(R.string.profile_drafts))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showSection(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        tabs.getTabAt(0)?.select()
    }

    private fun setupActions() {
        binding.buttonLogout.setOnClickListener {
            tokenStorage.clearToken()
            finish()
        }
        binding.buttonOpenDrafts.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
        binding.buttonChangeAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.buttonSaveProfile.setOnClickListener {
            profileViewModel.saveProfile()
        }
        binding.buttonEditProfile.setOnClickListener {
            binding.editDisplayName.requestFocus()
            binding.profileScroll.smoothScrollTo(0, binding.editDisplayName.top)
        }
        setupInputs()
        renderFollowers(listOf("Алексей", "Мария", "Владимир"))
        renderFollowing(listOf("Иван", "Дарья"))
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.uiState.collectLatest { renderProfile(it) }
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
            renderAvatar(avatar, displayName)
        } else {
            renderUserStub()
            renderAvatar(null, binding.profileName.text?.toString())
        }
    }

    private fun renderAvatar(avatarUrl: String?, title: String?) {
        val initial = title?.firstOrNull()?.uppercase() ?: "U"
        binding.profileAvatar.text = initial
        if (avatarUrl.isNullOrBlank()) {
            binding.profileAvatarImage.setImageDrawable(null)
            binding.profileAvatarImage.isVisible = false
            return
        }
        binding.profileAvatarImage.isVisible = true
        binding.profileAvatarImage.load(avatarUrl) {
            placeholder(R.drawable.bg_avatar_placeholder)
            error(R.drawable.bg_avatar_placeholder)
            crossfade(true)
        }
    }

    private fun uploadAvatar(uri: Uri) {
        lifecycleScope.launch {
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes.isNullOrEmpty()) {
                binding.profileError.isVisible = true
                binding.profileError.text = getString(R.string.profile_avatar_read_error)
                return@launch
            }
            profileViewModel.uploadAvatar(
                "avatar_${System.currentTimeMillis()}.jpg",
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collectLatest { renderPosts(it) }
            }
        }
    }

    private fun renderPosts(state: PostsUiState) {
        val posts = state.posts
        val liked = posts.filter { it.likesCount > 0 }
        val drafts = posts.take(1)
        renderPostList(binding.postsList, posts)
        renderPostList(binding.likedList, liked)
        renderPostList(binding.draftsList, drafts)
        binding.profileEmpty.isVisible = posts.isEmpty()
    }

    private fun renderPostList(container: LinearLayout, posts: List<PostCard>) {
        container.removeAllViews()
        if (posts.isEmpty()) {
            val stub = TextView(this)
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
                post.publishedAt ?: getString(R.string.published_unknown)
            view.findViewById<TextView>(R.id.miniPostLikes).text =
                getString(R.string.likes_format, post.likesCount)
            val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
            view.findViewById<android.widget.ImageView>(R.id.miniPostCover).load(coverUrl) {
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
                crossfade(true)
            }
            view.setOnClickListener { openPost(post) }
            container.addView(view)
        }
    }

    private fun renderFollowers(items: List<String>) {
        renderSimpleList(binding.followersList, items, getString(R.string.profile_followers))
    }

    private fun renderFollowing(items: List<String>) {
        renderSimpleList(binding.followingList, items, getString(R.string.profile_following))
    }

    private fun renderSimpleList(container: LinearLayout, items: List<String>, meta: String) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val stub = TextView(this)
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        items.forEach { name ->
            val view = layoutInflater.inflate(R.layout.item_profile_mini, container, false)
            view.findViewById<TextView>(R.id.miniProfileName).text = name
            view.findViewById<TextView>(R.id.miniProfileAvatar).text = name.firstOrNull()?.uppercase() ?: "?"
            view.findViewById<TextView>(R.id.miniProfileMeta).text = meta
            container.addView(view)
        }
    }

    private fun showSection(position: Int) {
        binding.sectionFollowers.isVisible = position == 0
        binding.sectionFollowing.isVisible = position == 1
        binding.sectionPosts.isVisible = position == 2
        binding.sectionLiked.isVisible = position == 3
        binding.sectionDrafts.isVisible = position == 4
    }

    private fun renderUserStub() {
        binding.profileName.text = getString(R.string.profile_user_stub)
        binding.profileEmail.text = getString(R.string.profile_email_stub)
        binding.profileAvatar.text = binding.profileName.text.firstOrNull()?.uppercase() ?: "U"
        binding.profileAvatarImage.setImageDrawable(null)
        binding.profileAvatarImage.isVisible = false
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
    }

    private fun setupBottomNavigation() {
        binding.profileBottomNavigation.selectedItemId = R.id.menu_profile
        binding.profileBottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_recipes -> {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_RECIPES)
                    )
                    finish()
                    true
                }

                R.id.menu_articles -> {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_ARTICLES)
                    )
                    finish()
                    true
                }

                R.id.menu_create -> {
                    startActivity(Intent(this, CreatePostActivity::class.java))
                    finish()
                    true
                }

                R.id.menu_profile -> true
                else -> false
            }
        }
    }
}
