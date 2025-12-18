package ru.zagrebin.culinaryblog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import java.util.EnumMap
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.databinding.ActivityMainBinding
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.ui.CreatePostFragment
import ru.zagrebin.culinaryblog.ui.PostDetailActivity
import ru.zagrebin.culinaryblog.ui.ProfileFragment
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), CreatePostFragment.Host, ProfileFragment.Host {

    private lateinit var binding: ActivityMainBinding
    private val postViewModel: PostViewModel by viewModels()
    private var latestState: PostsUiState = PostsUiState(isLoading = true)
    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var postRepository: PostRepository

    private var currentTab: ContentTab = ContentTab.RECIPES
    private val feedScrollPositions = EnumMap<ContentTab, Int>(ContentTab::class.java)
    private var restoreFeedScroll = false
    private var scrollRestoreScheduled = false
    private var lastFeedTabId: Int = DEFAULT_TAB_ID
    private val likedPostIds = mutableSetOf<Long>()
    private val scrollTopThresholdPx by lazy { (resources.displayMetrics.density * 200).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            binding.buttonScrollTop.isVisible = currentTab.isFeed() && scrollY > scrollTopThresholdPx
            if (
                currentTab.isFeed() &&
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collect { state ->
                    latestState = state
                    renderState(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentTab.isFeed()) {
            postViewModel.loadPosts()
        }
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
    }

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
        posts.forEach { post ->
            val cardBinding = ru.zagrebin.culinaryblog.databinding.ItemPostCardBinding.inflate(
                layoutInflater,
                binding.postsContainer,
                false
            )
            cardBinding.postType.text = formatType(post.postType)
            cardBinding.authorName.text =
                post.authorName?.ifBlank { getString(R.string.author_unknown) }
                    ?: getString(R.string.author_unknown)
            cardBinding.avatarInitial.text = post.authorName?.firstOrNull()?.uppercase() ?: "?"
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
            cardBinding.viewsText.text =
                getString(R.string.views_format, post.viewsCount ?: 0L)
            val likedPreviously = likedPostIds.contains(post.id)
            var currentLikes = post.likesCount
            var hasLiked = likedPreviously
            cardBinding.likesText.text = getString(R.string.likes_format, currentLikes)
            cardBinding.likesText.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (hasLiked) R.color.recipe_primary else R.color.text_muted
                )
            )

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
                    if (hasLiked) return@setOnClickListener
                    if (tokenStorage.getToken().isNullOrBlank()) {
                        startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                        return@setOnClickListener
                    }
                    isEnabled = false
                    lifecycleScope.launch {
                        val result = postRepository.like(post.id)
                        val offlineLike = result.exceptionOrNull()?.message == OFFLINE_LIKE_CACHED
                        if (result.isSuccess || offlineLike) {
                            currentLikes += 1
                            hasLiked = true
                            likedPostIds.add(post.id)
                            cardBinding.likesText.text = getString(R.string.likes_format, currentLikes)
                            cardBinding.likesText.setTextColor(
                                ContextCompat.getColor(this@MainActivity, R.color.recipe_primary)
                            )
                            if (offlineLike) {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.offline_feed_message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            hasLiked = false
                            cardBinding.likesText.text = getString(R.string.likes_format, currentLikes)
                            Toast.makeText(
                                this@MainActivity,
                                R.string.error_like_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            isEnabled = true
                        }
                        isEnabled = !hasLiked
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
            .filter { it.tag == CREATE_TAG || it.tag == PROFILE_TAG }
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
        val targets = supportFragmentManager.fragments.filter { it.tag == CREATE_TAG || it.tag == PROFILE_TAG }
        targets.forEach { transaction.hide(it) }
        if (targets.isNotEmpty()) transaction.commit()
    }

    private fun formatType(postType: String?): String =
        if (normalizePostType(postType) == ARTICLE_POST_TYPE) {
            getString(R.string.post_type_article)
        } else {
            getString(R.string.post_type_recipe)
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
            chip.isCheckable = false
            chip.isClickable = false
            chip.chipBackgroundColor =
                ContextCompat.getColorStateList(this, R.color.recipe_primary_light)
            chip.setTextColor(ContextCompat.getColor(this, R.color.recipe_primary))
            group.addView(chip)
        }
    }

    private enum class ContentTab {
        RECIPES,
        ARTICLES,
        CREATE,
        PROFILE,
        OTHER
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
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
        restoreFeedTab()
    }

    override fun onProfileOpenDrafts(): Boolean {
        binding.bottomNavigation.selectedItemId = R.id.menu_create
        return true
    }

    private fun restoreFeedTab() {
        binding.bottomNavigation.selectedItemId = lastFeedTabId
    }

    companion object {
        private const val DEFAULT_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
        private const val CREATE_TAG = "create_tab_fragment"
        private const val PROFILE_TAG = "profile_tab_fragment"
        private val DEFAULT_TAB_ID = R.id.menu_recipes
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_TAB_RECIPES = "tab_recipes"
        const val EXTRA_TAB_ARTICLES = "tab_articles"
        private const val STATE_LIKED_POSTS = "state_liked_posts"
        private const val TAG = "MainActivity"
        private const val OFFLINE_LIKE_CACHED = "OFFLINE_LIKE_CACHED"
        private const val OFFLINE_UNLIKE_CACHED = "OFFLINE_UNLIKE_CACHED"
    }

    private fun normalizePostType(postType: String?): String =
        postType?.lowercase()?.takeIf { it.isNotBlank() } ?: DEFAULT_POST_TYPE
}
