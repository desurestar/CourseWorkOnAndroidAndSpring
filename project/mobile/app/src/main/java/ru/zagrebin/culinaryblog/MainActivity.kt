package ru.zagrebin.culinaryblog

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.EnumMap
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.databinding.ActivityMainBinding
import ru.zagrebin.culinaryblog.databinding.ItemPostCardBinding
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.ui.CreatePostFragment
import ru.zagrebin.culinaryblog.ui.PostDetailActivity
import ru.zagrebin.culinaryblog.ui.ProfileFragment
import ru.zagrebin.culinaryblog.ui.PublicProfileFragment
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import ru.zagrebin.culinaryblog.data.repository.OFFLINE_LIKE_CACHED
import ru.zagrebin.culinaryblog.data.repository.OFFLINE_UNLIKE_CACHED
import ru.zagrebin.culinaryblog.util.renderAvatar
import ru.zagrebin.culinaryblog.util.applyInfoStyle
import javax.inject.Inject
import kotlin.jvm.Volatile

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), CreatePostFragment.Host, ProfileFragment.Host, PublicProfileFragment.Host {

    private lateinit var binding: ActivityMainBinding
    private val postViewModel: PostViewModel by viewModels()
    private var latestState: PostsUiState = PostsUiState(isLoading = true)
    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var postRepository: PostRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Volatile private var currentUserId: Long? = null
    private var loadUserIdJob: Job? = null
    private val pendingUserIdCallbacks = mutableListOf<(Long?) -> Unit>()
    private var backPressedCallback: OnBackPressedCallback? = null
    private val postDetailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val postId = data.getLongExtra(PostDetailActivity.EXTRA_RESULT_POST_ID, -1L)
        val deleted = data.getBooleanExtra(PostDetailActivity.EXTRA_RESULT_DELETED, false)
        if (deleted && postId > 0 && currentTab.isFeed()) {
            latestState = latestState.copy(
                posts = latestState.posts.filterNot { it.id == postId },
                likedIds = latestState.likedIds.toMutableSet().apply { remove(postId) }
            )
            renderState(latestState)
            return@registerForActivityResult
        }
        if (
            postId <= 0 ||
            !currentTab.isFeed() ||
            !data.hasExtra(PostDetailActivity.EXTRA_RESULT_LIKED) ||
            !data.hasExtra(PostDetailActivity.EXTRA_RESULT_LIKES_COUNT)
        ) return@registerForActivityResult

        val liked = data.getBooleanExtra(PostDetailActivity.EXTRA_RESULT_LIKED, false)
        val likesCount = data.getIntExtra(PostDetailActivity.EXTRA_RESULT_LIKES_COUNT, -1)
        if (likesCount < 0) return@registerForActivityResult
        val updatedPosts = latestState.posts.map { post ->
            if (post.id == postId) post.copy(likesCount = likesCount) else post
        }
        val updatedLikedIds = latestState.likedIds.toMutableSet().apply {
            if (liked) add(postId) else remove(postId)
        }
        latestState = latestState.copy(posts = updatedPosts, likedIds = updatedLikedIds)
        renderState(latestState)
    }

    private var currentTab: ContentTab = ContentTab.RECIPES
    private val feedScrollPositions = EnumMap<ContentTab, Int>(ContentTab::class.java)
    private var restoreFeedScroll = false
    private var scrollRestoreScheduled = false
    private var lastFeedTabId: Int = DEFAULT_TAB_ID
    private val likedPostIds = mutableSetOf<Long>()
    private val scrollTopThresholdPx by lazy { (resources.displayMetrics.density * 200).toInt() }
    private var scrollTopBaseBottomMargin: Int = 0
    private val scrollTopRaisedOffset by lazy { resources.getDimensionPixelSize(R.dimen.scroll_top_button_raise) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        scrollTopBaseBottomMargin =
            (binding.buttonScrollTop.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        savedInstanceState?.getLongArray(STATE_LIKED_POSTS)?.let { saved ->
            likedPostIds.clear()
            likedPostIds.addAll(saved.toList())
        }

        binding.swipeRefresh.setOnRefreshListener { postViewModel.loadPosts() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            !(currentTab.isFeed() && !binding.postsScroll.canScrollVertically(-1))
        }
        binding.buttonRetry.setOnClickListener { postViewModel.loadPosts() }

        binding.postsScroll.setOnScrollChangeListener { v, _, scrollY, _, _ ->
            val isFeedTab = currentTab.isFeed()
            val atBottom = isFeedTab && scrollY > 0 && !v.canScrollVertically(1)
            binding.buttonScrollTop.isVisible = isFeedTab && scrollY > scrollTopThresholdPx
            updateScrollTopButtonMargin(atBottom)
            if (
                isFeedTab &&
                !latestState.isLoading &&
                !latestState.isAppending &&
                latestState.nextPage != null &&
                !v.canScrollVertically(1)
            ) {
                postViewModel.loadNextPage()
            }
        }
        binding.buttonScrollTop.setOnClickListener {
            binding.postsScroll.smoothScrollTo(0, 0)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if ((item.itemId == R.id.menu_create || item.itemId == R.id.menu_profile) &&
                tokenStorage.getToken().isNullOrBlank()
            ) {
                startActivity(Intent(this, AuthActivity::class.java))
                return@setOnItemSelectedListener false
            }
            applySelection(item.itemId)
            true
        }
        val initialTab = intent.getStringExtra(EXTRA_TARGET_TAB)
        binding.bottomNavigation.selectedItemId = when (initialTab) {
            EXTRA_TAB_ARTICLES -> R.id.menu_articles
            else -> DEFAULT_TAB_ID
        }
        applySelection(binding.bottomNavigation.selectedItemId)
        val targetUserId = intent.getLongExtra(EXTRA_TARGET_USER_ID, -1L)
        if (targetUserId > 0) {
            openPublicProfile(
                targetUserId,
                intent.getStringExtra(EXTRA_TARGET_USER_NAME),
                intent.getBooleanExtra(EXTRA_TARGET_USER_SUBSCRIBED, false)
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collect { state ->
                    latestState = state
                    renderState(state)
                }
            }
        }

        backPressedCallback = onBackPressedDispatcher.addCallback(this, false) {
            if (isPublicProfileVisible()) {
                closePublicProfile()
                return@addCallback
            }
            restoreFeedTab()
        }
        updateBackPressedHandling()
    }

    private fun applySelection(itemId: Int) {
        val previousTab = currentTab
        val nextTab = when (itemId) {
            R.id.menu_recipes -> ContentTab.RECIPES
            R.id.menu_articles -> ContentTab.ARTICLES
            R.id.menu_create -> ContentTab.CREATE
            R.id.menu_profile -> ContentTab.PROFILE
            else -> ContentTab.OTHER
        }

        if (previousTab.isFeed()) {
            feedScrollPositions[previousTab] = binding.postsScroll.scrollY
        }

        currentTab = nextTab
        if (currentTab.isFeed()) {
            lastFeedTabId = itemId
            restoreFeedScroll = true
        }

        if (currentTab.isFeed()) {
            updateFeedTitle()
        } else {
            binding.feedTitle.isVisible = false
        }

        when (currentTab) {
            ContentTab.RECIPES, ContentTab.ARTICLES -> showFeed()
            ContentTab.CREATE -> {
                showFragment(CREATE_TAG) { CreatePostFragment.newInstance() }
            }

            ContentTab.PROFILE -> {
                showFragment(PROFILE_TAG) { ProfileFragment() }
            }

            ContentTab.OTHER -> {
                hideFragments()
                binding.postsContent.isVisible = false
                binding.stubText.isVisible = true
                binding.fragmentContainer.isVisible = false
                binding.swipeRefresh.isEnabled = false
                binding.swipeRefresh.isRefreshing = false
                binding.buttonScrollTop.isVisible = false
                binding.feedTitle.isVisible = false
                binding.stubText.text = when (itemId) {
                    R.id.menu_messenger -> getString(R.string.messenger_stub_message)
                    else -> getString(R.string.view_stub_message)
                }
            }
        }
        updateBackPressedHandling()
    }

    private fun updateBackPressedHandling() {
        backPressedCallback?.isEnabled = isPublicProfileVisible() || !currentTab.isFeed()
    }

    private fun isPublicProfileVisible(): Boolean =
        supportFragmentManager.findFragmentByTag(PUBLIC_PROFILE_TAG)?.isVisible == true

    private fun renderState(state: PostsUiState) {
        if (!currentTab.isFeed()) return

        likedPostIds.clear()
        likedPostIds.addAll(state.likedIds)

        binding.progressBar.isVisible = state.isLoading
        val errorText = when {
            state.offline -> getString(R.string.offline_feed_message)
            else -> state.error
        }
        binding.errorText.isVisible = errorText != null
        binding.errorText.text = errorText ?: ""
        binding.buttonRetry.isVisible = errorText != null
        binding.swipeRefresh.isRefreshing = state.isLoading && currentTab.isFeed()

        val filteredPosts = filterPosts(state.posts)
        binding.emptyText.isVisible =
            !state.isLoading && !state.isAppending && state.error == null && filteredPosts.isEmpty()

        renderPosts(filteredPosts)
        if (restoreFeedScroll && !scrollRestoreScheduled) {
            scrollRestoreScheduled = true
            val targetScrollY = feedScrollPositions[currentTab] ?: 0
            binding.postsScroll.post {
                if (!isDestroyed && !isFinishing) {
                    try {
                        binding.postsScroll.scrollTo(0, targetScrollY)
                    } catch (e: IllegalStateException) {
                        Log.d(TAG, "Scroll restore skipped: ${e.message}")
                    }
                }
                restoreFeedScroll = false
                scrollRestoreScheduled = false
            }
        }
    }

    private fun filterPosts(posts: List<PostCard>): List<PostCard> = when (currentTab) {
        ContentTab.RECIPES -> posts.filter { normalizePostType(it.postType) == DEFAULT_POST_TYPE }
        ContentTab.ARTICLES -> posts.filter { normalizePostType(it.postType) == ARTICLE_POST_TYPE }
        else -> posts
    }

    private fun renderPosts(posts: List<PostCard>) {
        binding.postsContainer.removeAllViews()
        val iconPadding = resources.getDimensionPixelSize(R.dimen.create_horizontal_space)
        val primaryIconColor = ContextCompat.getColor(this, R.color.recipe_primary)
        fun applyMetaIcon(view: TextView, icon: Int) {
            view.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
            view.compoundDrawablePadding = iconPadding
            TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(primaryIconColor))
        }
        fun updateLikesView(view: TextView, liked: Boolean, count: Int, animate: Boolean = false) {
            view.text = getString(R.string.likes_format, count)
            val icon = if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            val tint = ContextCompat.getColor(this, if (liked) R.color.text_error else R.color.recipe_primary)
            view.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
            view.compoundDrawablePadding = iconPadding
            TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(tint))
            view.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (liked) R.color.text_error else R.color.text_muted
                )
            )
            if (animate) animateLike(view)
        }
        posts.forEach { post ->
            val cardBinding = ItemPostCardBinding.inflate(
                layoutInflater,
                binding.postsContainer,
                false
            )
            cardBinding.postType.text = formatType(post.postType)
            cardBinding.authorName.text =
                post.authorName?.ifBlank { getString(R.string.author_unknown) }
                    ?: getString(R.string.author_unknown)
            val openAuthor = View.OnClickListener { openAuthorProfile(post) }
            cardBinding.authorName.setOnClickListener(openAuthor)
            val avatarUrl = post.authorAvatarUrl?.takeIf { it.isNotBlank() }
            renderAvatar(cardBinding.avatarImage, cardBinding.avatarInitial, avatarUrl, post.authorName)
            if (avatarUrl != null) {
                cardBinding.avatarImage.setOnClickListener(openAuthor)
                cardBinding.avatarInitial.setOnClickListener(null)
            } else {
                cardBinding.avatarImage.setOnClickListener(null)
                cardBinding.avatarInitial.setOnClickListener(openAuthor)
            }
            cardBinding.publishedAt.text =
                formatDisplayDate(post.publishedAt) ?: getString(R.string.published_unknown)
            cardBinding.postTitle.text = post.title.ifBlank { getString(R.string.card_title_placeholder) }
            cardBinding.postExcerpt.text = post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
            bindTags(cardBinding.tagsGroup, post.tags)

            val isRecipe = normalizePostType(post.postType) == DEFAULT_POST_TYPE
            val hasRecipeMeta = isRecipe && (post.cookingTimeMinutes != null || post.calories != null)
            cardBinding.recipeMeta.isVisible = hasRecipeMeta
            cardBinding.cookingTime.isVisible = hasRecipeMeta && post.cookingTimeMinutes != null
            cardBinding.calories.isVisible = hasRecipeMeta && post.calories != null
            post.cookingTimeMinutes?.let {
                cardBinding.cookingTime.text = getString(R.string.cooking_time_format, it)
            }
            post.calories?.let {
                cardBinding.calories.text = getString(R.string.calories_format, it)
            }
            applyMetaIcon(cardBinding.cookingTime, R.drawable.ic_time_outline)
            applyMetaIcon(cardBinding.calories, R.drawable.ic_fire)
            cardBinding.viewsText.text =
                getString(R.string.views_format, post.viewsCount ?: 0L)
            applyMetaIcon(cardBinding.viewsText, R.drawable.ic_visibility)
            val likedPreviously = likedPostIds.contains(post.id)
            var currentLikes = post.likesCount
            var hasLiked = likedPreviously
            updateLikesView(cardBinding.likesText, hasLiked, currentLikes)

            val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
            cardBinding.postCover.isVisible = coverUrl != null
            if (coverUrl != null) {
                cardBinding.postCover.load(coverUrl) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                    crossfade(true)
                }
            }

            cardBinding.likesText.apply {
                isEnabled = true
                setOnClickListener {
                    if (tokenStorage.getToken().isNullOrBlank()) {
                        startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                        return@setOnClickListener
                    }
                    isEnabled = false
                    lifecycleScope.launch {
                        val result = if (hasLiked) postRepository.unlike(post.id) else postRepository.like(post.id)
                        val offlineHandled = result.exceptionOrNull()?.message in OFFLINE_CACHE_MESSAGES
                        if (result.isSuccess || offlineHandled) {
                            hasLiked = !hasLiked
                            currentLikes = (currentLikes + if (hasLiked) 1 else -1).coerceAtLeast(0)
                            if (hasLiked) likedPostIds.add(post.id) else likedPostIds.remove(post.id)
                            updateLikesView(cardBinding.likesText, hasLiked, currentLikes, true)
                            if (offlineHandled) {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.offline_feed_message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            hasLiked = false
                            updateLikesView(cardBinding.likesText, hasLiked, currentLikes)
                            Toast.makeText(
                                this@MainActivity,
                                R.string.error_like_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        isEnabled = true
                    }
                }
            }

            cardBinding.root.setOnClickListener { openPost(post) }

            binding.postsContainer.addView(cardBinding.root)
        }
    }

    private fun showFeed() {
        hideFragments()
        binding.fragmentContainer.isVisible = false
        binding.postsContent.isVisible = true
        binding.stubText.isVisible = false
        binding.swipeRefresh.isEnabled = true
        binding.swipeRefresh.isRefreshing = latestState.isLoading
        binding.buttonScrollTop.isVisible = binding.postsScroll.scrollY > scrollTopThresholdPx
        updateScrollTopButtonMargin(false)

        updateFeedTitle()


        renderState(latestState)
    }

    private fun updateFeedTitle() {
        binding.feedTitle.text = getString(
            if (currentTab == ContentTab.ARTICLES) R.string.nav_articles else R.string.nav_recipes
        )
        binding.feedTitle.isVisible = currentTab.isFeed()
    }

    private fun showFragment(tag: String, provider: () -> Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments
            .filter { it.tag == CREATE_TAG || it.tag == PROFILE_TAG || it.tag == PUBLIC_PROFILE_TAG }
            .forEach { transaction.hide(it) }
        val fragment = supportFragmentManager.findFragmentByTag(tag) ?: provider()
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragmentContainer, fragment, tag)
        }
        transaction.commit()

        binding.postsContent.isVisible = false
        binding.stubText.isVisible = false
        binding.fragmentContainer.isVisible = true
        binding.swipeRefresh.isEnabled = false
        binding.swipeRefresh.isRefreshing = false
        binding.buttonScrollTop.isVisible = false
    }

    private fun hideFragments() {
        val transaction = supportFragmentManager.beginTransaction()
        val targets = supportFragmentManager.fragments.filter { it.tag == CREATE_TAG || it.tag == PROFILE_TAG || it.tag == PUBLIC_PROFILE_TAG }
        targets.forEach { transaction.hide(it) }
        if (targets.isNotEmpty()) transaction.commit()
    }

    private fun openPublicProfile(userId: Long, displayName: String?, subscribed: Boolean?) {
        val existing = supportFragmentManager.findFragmentByTag(PUBLIC_PROFILE_TAG) as? PublicProfileFragment
        existing?.updateUser(userId, displayName, subscribed)
        showFragment(PUBLIC_PROFILE_TAG) { PublicProfileFragment.newInstance(userId, displayName, subscribed) }
        updateBackPressedHandling()
    }

    override fun onPublicProfileClose() {
        closePublicProfile()
    }

    private fun closePublicProfile() {
        supportFragmentManager.findFragmentByTag(PUBLIC_PROFILE_TAG)?.let {
            supportFragmentManager.commit { remove(it) }
        }
        showFeed()
        updateBackPressedHandling()
    }

    private fun formatType(postType: String?): String =
        if (normalizePostType(postType) == ARTICLE_POST_TYPE) {
            getString(R.string.post_type_article)
        } else {
            getString(R.string.post_type_recipe)
        }

    private fun animateLike(target: TextView) {
        target.animate().cancel()
        target.scaleX = 1f
        target.scaleY = 1f
        target.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(120)
            .withEndAction {
                target.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()
    }

    private fun bindTags(group: ChipGroup, tags: Set<String>?) {
        group.removeAllViews()
        if (tags.isNullOrEmpty()) {
            group.isVisible = false
            return
        }

        group.isVisible = true
        tags.forEach { tag ->
            val chip = Chip(this)
            chip.text = tag
            chip.applyInfoStyle()
            group.addView(chip)
        }
    }

    private fun updateScrollTopButtonMargin(raise: Boolean) {
        val params = binding.buttonScrollTop.layoutParams as ViewGroup.MarginLayoutParams
        val targetMargin = scrollTopBaseBottomMargin + if (raise) scrollTopRaisedOffset else 0
        if (params.bottomMargin != targetMargin) {
            params.bottomMargin = targetMargin
            binding.buttonScrollTop.layoutParams = params
        }
    }

    private enum class ContentTab {
        RECIPES,
        ARTICLES,
        CREATE,
        PROFILE,
        OTHER
    }

    private fun ensureCurrentUserIdLoaded(onLoaded: (Long?) -> Unit = {}) {
        val existing = currentUserId
        if (existing != null) {
            onLoaded(existing)
            return
        }
        if (tokenStorage.getToken().isNullOrBlank()) {
            onLoaded(null)
            return
        }
        synchronized(pendingUserIdCallbacks) {
            if (loadUserIdJob != null) {
                pendingUserIdCallbacks.add(onLoaded)
                return
            }
            pendingUserIdCallbacks.add(onLoaded)
            loadUserIdJob = lifecycleScope.launch {
                try {
                    profileRepository.getProfile()
                        .onSuccess { profile ->
                            currentUserId = profile.id
                        }
                        .onFailure {
                            Log.w(TAG, "Failed to fetch current user id: ${it.message}")
                        }
                } finally {
                    val callbacks = synchronized(pendingUserIdCallbacks) {
                        val copy = pendingUserIdCallbacks.toList()
                        pendingUserIdCallbacks.clear()
                        loadUserIdJob = null
                        copy
                    }
                    callbacks.forEach { it(currentUserId) }
                }
            }
        }
    }

    private fun openAuthorProfile(post: PostCard) {
        val authorId = post.authorId ?: return
        ensureCurrentUserIdLoaded { userId ->
            if (userId == authorId) {
                binding.bottomNavigation.selectedItemId = R.id.menu_profile
            } else {
                openPublicProfile(authorId, post.authorName, null)
            }
        }
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        postDetailLauncher.launch(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (likedPostIds.isNotEmpty()) {
            outState.putLongArray(STATE_LIKED_POSTS, likedPostIds.toLongArray())
        }
    }

    private fun ContentTab.isFeed(): Boolean = this == ContentTab.RECIPES || this == ContentTab.ARTICLES

    override fun onPostCreated(post: PostCard) {
        val targetTabId = if (normalizePostType(post.postType) == ARTICLE_POST_TYPE) {
            R.id.menu_articles
        } else {
            R.id.menu_recipes
        }
        binding.bottomNavigation.selectedItemId = targetTabId
        postViewModel.loadPosts()
        openPost(post)
    }

    override fun onPostUpdated(postId: Long) {
        postViewModel.loadPosts()
    }

    override fun onCreateRequiresAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        restoreFeedTab()
    }

    override fun onProfileRequiresAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        restoreFeedTab()
    }

    override fun onProfileLogout() {
        tokenStorage.clearToken()
        currentUserId = null
        restoreFeedTab()
    }

    override fun onOpenUserProfile(userId: Long, displayName: String?, subscribed: Boolean?) {
        openPublicProfile(userId, displayName, subscribed)
    }

    private fun restoreFeedTab() {
        binding.bottomNavigation.selectedItemId = lastFeedTabId
    }

    companion object {
        private const val DEFAULT_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
        private const val CREATE_TAG = "create_tab_fragment"
        private const val PROFILE_TAG = "profile_tab_fragment"
        private const val PUBLIC_PROFILE_TAG = "public_profile_fragment"
        private val DEFAULT_TAB_ID = R.id.menu_recipes
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_TAB_RECIPES = "tab_recipes"
        const val EXTRA_TAB_ARTICLES = "tab_articles"
        const val EXTRA_TARGET_USER_ID = "extra_target_user_id"
        const val EXTRA_TARGET_USER_NAME = "extra_target_user_name"
        const val EXTRA_TARGET_USER_SUBSCRIBED = "extra_target_user_subscribed"
        private const val STATE_LIKED_POSTS = "state_liked_posts"
        private const val TAG = "MainActivity"
        private val OFFLINE_CACHE_MESSAGES = setOf(OFFLINE_LIKE_CACHED, OFFLINE_UNLIKE_CACHED)
    }

    private fun normalizePostType(postType: String?): String =
        postType?.lowercase()?.takeIf { it.isNotBlank() } ?: DEFAULT_POST_TYPE
}
