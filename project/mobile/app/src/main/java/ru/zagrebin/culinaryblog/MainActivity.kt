package ru.zagrebin.culinaryblog

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.data.repository.OFFLINE_LIKE_CACHED
import ru.zagrebin.culinaryblog.data.repository.OFFLINE_UNLIKE_CACHED
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityMainBinding
import ru.zagrebin.culinaryblog.databinding.DialogFiltersBinding
import ru.zagrebin.culinaryblog.databinding.ItemPostCardBinding
import ru.zagrebin.culinaryblog.data.local.LocalCacheManager
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostFilters
import ru.zagrebin.culinaryblog.model.TagItem
import ru.zagrebin.culinaryblog.ui.CreatePostFragment
import ru.zagrebin.culinaryblog.ui.DraftsFragment
import ru.zagrebin.culinaryblog.ui.PostDetailActivity
import ru.zagrebin.culinaryblog.ui.ProfileFragment
import ru.zagrebin.culinaryblog.ui.PublicProfileFragment
import ru.zagrebin.culinaryblog.ui.RefreshableTab
import ru.zagrebin.culinaryblog.util.applyTagStyle
import ru.zagrebin.culinaryblog.util.renderAvatar
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import javax.inject.Inject
import kotlin.jvm.Volatile
import java.util.EnumMap

@AndroidEntryPoint
class MainActivity :
    AppCompatActivity(),
    CreatePostFragment.Host,
    ProfileFragment.Host,
    PublicProfileFragment.Host {

    private lateinit var binding: ActivityMainBinding
    private val postViewModel: PostViewModel by viewModels()
    private var latestState: PostsUiState = PostsUiState(isLoading = true)

    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var postRepository: PostRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var localCacheManager: LocalCacheManager

    @Volatile private var currentUserId: Long? = null
    private var loadUserIdJob: Job? = null
    private val pendingUserIdCallbacks = mutableListOf<(Long?) -> Unit>()
    private var backPressedCallback: OnBackPressedCallback? = null
    private val loadedFeedTabs = mutableSetOf<ContentTab>()

    private val postDetailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
    private var lastDraftsStateHash: Int = 0
    private val feedScrollPositions = EnumMap<ContentTab, Int>(ContentTab::class.java)
    private var restoreFeedScroll = false
    private var scrollRestoreScheduled = false
    private var lastFeedTabId: Int = DEFAULT_TAB_ID
    private val likedPostIds = mutableSetOf<Long>()
    private val filtersByTab = EnumMap<ContentTab, PostFilters?>(ContentTab::class.java)
    private var availableTags: List<TagItem> = emptyList()
    private var tagsLoadingJob: Job? = null
    private val scrollTopThresholdPx by lazy { (resources.displayMetrics.density * 200).toInt() }
    private var scrollTopBaseBottomMargin: Int = 0
    private val scrollTopRaisedOffset by lazy { resources.getDimensionPixelSize(R.dimen.scroll_top_button_raise) }
    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scrollTopBaseBottomMargin =
            (binding.buttonScrollTop.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        binding.buttonFilters.setOnClickListener { showFiltersDialog() }
        updateFiltersButtonState()

        savedInstanceState?.getLongArray(STATE_LIKED_POSTS)?.let { saved ->
            likedPostIds.clear()
            likedPostIds.addAll(saved.toList())
        }

        binding.swipeRefresh.setOnRefreshListener {
            when (currentTab) {
                ContentTab.RECIPES, ContentTab.ARTICLES ->
                    postViewModel.loadPosts(filtersForCurrentTab(), currentPostType())
                ContentTab.DRAFTS, ContentTab.PROFILE -> {
                    binding.swipeRefresh.isRefreshing = true
                    val tag = if (currentTab == ContentTab.DRAFTS) DRAFTS_TAG else PROFILE_TAG
                    val fragment = supportFragmentManager.findFragmentByTag(tag)
                    if (fragment != null) {
                        refreshFragment(fragment)
                    } else {
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
                else -> binding.swipeRefresh.isRefreshing = false
            }
        }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            !(currentTab.isFeed() && !binding.postsScroll.canScrollVertically(-1))
        }
        binding.buttonRetry.setOnClickListener {
            postViewModel.loadPosts(filtersForCurrentTab(), currentPostType())
        }

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
            if ((item.itemId == R.id.menu_create || item.itemId == R.id.menu_profile || item.itemId == R.id.menu_drafts) &&
                tokenStorage.getToken().isNullOrBlank()
            ) {
                startActivity(Intent(this, AuthActivity::class.java))
                return@setOnItemSelectedListener false
            }
            applySelection(item.itemId)
            true
        }

        // Use white background for swipe refresh progress circle and hide the small header progress
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(android.R.color.white)
        binding.progressBar.isVisible = false

        val initialTab = intent.getStringExtra(EXTRA_TARGET_TAB)
        binding.bottomNavigation.selectedItemId = when (initialTab) {
            EXTRA_TAB_ARTICLES -> R.id.menu_articles
            EXTRA_TAB_DRAFTS -> R.id.menu_drafts
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

                    // If swipe-refresh is active for drafts/profile, stop it when drafts/serverDrafts change
                    if (binding.swipeRefresh.isRefreshing && (currentTab == ContentTab.DRAFTS || currentTab == ContentTab.PROFILE)) {
                        val currentHash = (state.drafts.hashCode() xor state.serverDrafts.hashCode())
                        if (currentHash != lastDraftsStateHash) {
                            binding.swipeRefresh.isRefreshing = false
                        }
                        lastDraftsStateHash = currentHash
                    }
                }
            }
        }

        ensureTagsLoaded()
        
        // Monitor network connectivity and trigger draft sync
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ru.zagrebin.culinaryblog.util.NetworkMonitor.observeConnectivity(this@MainActivity).collect { isConnected ->
                    if (isConnected) {
                        Log.d(TAG, "Network connected, triggering draft sync")
                        ru.zagrebin.culinaryblog.worker.DraftSyncScheduler.triggerImmediateSync(this@MainActivity)
                    } else {
                        Log.d(TAG, "Network disconnected")
                    }
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

    override fun onResume() {
        super.onResume()
        ensureCurrentUserIdLoaded { id ->
            postViewModel.setCurrentUser(id)
        }
        if (hasResumedOnce) {
            refreshCurrentTabIfNeeded()
        }
        hasResumedOnce = true
    }

    private fun applySelection(itemId: Int) {
        val previousTab = currentTab
        val nextTab = when (itemId) {
            R.id.menu_recipes -> ContentTab.RECIPES
            R.id.menu_articles -> ContentTab.ARTICLES
            R.id.menu_create -> ContentTab.CREATE
            R.id.menu_profile -> ContentTab.PROFILE
            R.id.menu_drafts -> ContentTab.DRAFTS
            else -> ContentTab.OTHER
        }

        if (previousTab.isFeed()) {
            feedScrollPositions[previousTab] = binding.postsScroll.scrollY
        }

        currentTab = nextTab
        if (currentTab.isFeed()) {
            lastFeedTabId = itemId
            restoreFeedScroll = true
            if (!loadedFeedTabs.contains(currentTab)) {
                loadedFeedTabs.add(currentTab)
                postViewModel.loadPosts(filtersForCurrentTab(), currentPostType())
            }
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

            ContentTab.DRAFTS -> {
                showFragment(DRAFTS_TAG) { DraftsFragment() }
                // ВАЖНО: НЕ вызывать refreshFragment() здесь — он выполнится через runOnCommit в showFragment()
            }

            ContentTab.PROFILE -> {
                showFragment(PROFILE_TAG) { ProfileFragment() }
                // ВАЖНО: НЕ вызывать refreshFragment() здесь — он выполнится через runOnCommit в showFragment()
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
                binding.stubText.text = getString(R.string.view_stub_message)
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

        updateFiltersButtonState()
        likedPostIds.clear()
        likedPostIds.addAll(state.likedIds)

        // Use SwipeRefreshLayout's spinner as single loading indicator
        // keep `progressBar` hidden to avoid duplicate spinners
        binding.progressBar.isVisible = false
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

    private fun filterPosts(posts: List<PostCard>): List<PostCard> {
        return if (currentTab.isFeed()) {
            PostFilters.filter(posts, filtersByTab[currentTab], currentPostType())
        } else {
            posts
        }
    }

    private fun renderPosts(posts: List<PostCard>) {
        binding.postsContainer.removeAllViews()
        val iconPadding = resources.getDimensionPixelSize(R.dimen.create_horizontal_space)

        fun updateLikesView(view: TextView, liked: Boolean, count: Int, animate: Boolean = false) {
            view.text = getString(R.string.likes_format, count)
            val icon = if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            val tint = ContextCompat.getColor(
                this,
                if (liked) R.color.text_error else R.color.recipe_primary
            )
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

            cardBinding.postTitle.text =
                post.title.ifBlank { getString(R.string.card_title_placeholder) }
            cardBinding.postExcerpt.text =
                post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }

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

            cardBinding.viewsText.text = getString(R.string.views_format, post.viewsCount ?: 0L)

            val likedPreviously = likedPostIds.contains(post.id) || post.liked
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
                        val result =
                            if (hasLiked) postRepository.unlike(post.id) else postRepository.like(post.id)

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
        val isFeed = currentTab.isFeed()
        binding.feedHeader.isVisible = isFeed
        binding.buttonFilters.isVisible = isFeed
        binding.feedTitle.isVisible = isFeed
        if (isFeed) updateFiltersButtonState()
    }

    private fun showFragment(tag: String, provider: () -> Fragment): Fragment {
        val transaction = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments
            .filter { it.tag == CREATE_TAG || it.tag == PROFILE_TAG || it.tag == PUBLIC_PROFILE_TAG || it.tag == DRAFTS_TAG }
            .forEach { transaction.hide(it) }

        val fragment = supportFragmentManager.findFragmentByTag(tag) ?: provider()
        val isNew = !fragment.isAdded
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragmentContainer, fragment, tag)
        }

        // refresh only AFTER commit (when fragment is attached)
        transaction.runOnCommit {
            if (isNew) refreshFragment(fragment)
        }

        transaction.commit()

        binding.postsContent.isVisible = false
        binding.stubText.isVisible = false
        binding.fragmentContainer.isVisible = true
        // Enable swipe-refresh for fragments that implement RefreshableTab
        binding.swipeRefresh.isEnabled = fragment is RefreshableTab
        binding.swipeRefresh.isRefreshing = false
        binding.buttonScrollTop.isVisible = false
        return fragment
    }

    private fun refreshFragment(fragment: Fragment) {
        (fragment as? RefreshableTab)?.refreshContent()
    }

    private fun refreshCurrentTabIfNeeded() {
        when (currentTab) {
            ContentTab.DRAFTS -> {
                supportFragmentManager.findFragmentByTag(DRAFTS_TAG)?.let { refreshFragment(it) }
            }
            ContentTab.PROFILE -> {
                supportFragmentManager.findFragmentByTag(PROFILE_TAG)?.let { refreshFragment(it) }
            }
            else -> Unit
        }
    }

    private fun hideFragments() {
        val transaction = supportFragmentManager.beginTransaction()
        val targets = supportFragmentManager.fragments.filter {
            it.tag == CREATE_TAG || it.tag == PROFILE_TAG || it.tag == PUBLIC_PROFILE_TAG || it.tag == DRAFTS_TAG
        }
        targets.forEach { transaction.hide(it) }
        if (targets.isNotEmpty()) transaction.commit()
    }

    private fun openPublicProfile(userId: Long, displayName: String?, subscribed: Boolean?) {
        val existing =
            supportFragmentManager.findFragmentByTag(PUBLIC_PROFILE_TAG) as? PublicProfileFragment
        existing?.updateUser(userId, displayName, subscribed)
        showFragment(PUBLIC_PROFILE_TAG) {
            PublicProfileFragment.newInstance(userId, displayName, subscribed)
        }
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
            chip.applyTagStyle(availableTags.firstOrNull { it.name == tag }?.color)
            chip.isCheckable = false
            chip.isClickable = false
            group.addView(chip)
        }
    }

    private fun filtersForCurrentTab(): PostFilters {
        val type = currentPostType()
        val stored = filtersByTab[currentTab]
        val normalized = (stored ?: PostFilters(postType = type)).normalizedForType(type)
        filtersByTab[currentTab] = normalized
        return normalized
    }

    private fun updateFiltersButtonState() {
        val count = filtersForCurrentTab().appliedCount(currentPostType())
        val base = getString(R.string.filters_action)
        binding.buttonFilters.text = if (count > 0) "$base ($count)" else base
    }

    private fun currentPostType(): String =
        if (currentTab == ContentTab.ARTICLES) ARTICLE_POST_TYPE else DEFAULT_POST_TYPE

    private fun showFiltersDialog() {
        if (!currentTab.isFeed()) return
        val dialogBinding = DialogFiltersBinding.inflate(layoutInflater)
        val filters = filtersForCurrentTab()
        val isRecipeTab = currentPostType() == DEFAULT_POST_TYPE

        dialogBinding.recipeFiltersGroup.isVisible = isRecipeTab
        if (isRecipeTab) {
            dialogBinding.inputCookingTimeMin.setText(filters.cookingTimeMin?.toString().orEmpty())
            dialogBinding.inputCookingTimeMax.setText(filters.cookingTimeMax?.toString().orEmpty())
            dialogBinding.inputCaloriesMin.setText(filters.caloriesMin?.toString().orEmpty())
            dialogBinding.inputCaloriesMax.setText(filters.caloriesMax?.toString().orEmpty())
        } else {
            dialogBinding.inputCookingTimeMin.setText("")
            dialogBinding.inputCookingTimeMax.setText("")
            dialogBinding.inputCaloriesMin.setText("")
            dialogBinding.inputCaloriesMax.setText("")
        }

        val selectedTags = filters.tags.toMutableSet()
        renderTagChips(dialogBinding, selectedTags)
        ensureTagsLoaded { renderTagChips(dialogBinding, selectedTags) }

        dialogBinding.buttonResetFilters.isVisible = filters.appliedCount(currentPostType()) > 0

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonCancelFilters.setOnClickListener { dialog.dismiss() }
        dialogBinding.buttonApplyFilters.setOnClickListener {
            val updated = PostFilters(
                postType = currentPostType(),
                cookingTimeMin = dialogBinding.inputCookingTimeMin.text?.toString()?.toIntOrNull(),
                cookingTimeMax = dialogBinding.inputCookingTimeMax.text?.toString()?.toIntOrNull(),
                caloriesMin = dialogBinding.inputCaloriesMin.text?.toString()?.toIntOrNull(),
                caloriesMax = dialogBinding.inputCaloriesMax.text?.toString()?.toIntOrNull(),
                tags = selectedTags.map { it.trim() }.filter { it.isNotBlank() }.toSet()
            ).normalizedForType(currentPostType())

            filtersByTab[currentTab] = updated
            updateFiltersButtonState()
            postViewModel.loadPosts(updated, currentPostType())
            dialog.dismiss()
        }

        dialogBinding.buttonResetFilters.setOnClickListener {
            val cleared = PostFilters(postType = currentPostType()).normalizedForType(currentPostType())
            filtersByTab[currentTab] = cleared
            updateFiltersButtonState()
            postViewModel.loadPosts(cleared, currentPostType())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun renderTagChips(binding: DialogFiltersBinding, selected: MutableSet<String>) {
        binding.tagsGroup.removeAllViews()
        val tags = availableTags
        val loading = tagsLoadingJob != null && tags.isEmpty()
        binding.tagsProgress.isVisible = loading
        binding.tagsEmpty.isVisible = tags.isEmpty() && !loading
        binding.tagsGroup.isVisible = tags.isNotEmpty()

        tags.forEach { tag ->
            val chip = Chip(this)
            chip.text = tag.name
            chip.isCheckable = true
            chip.isChecked = selected.contains(tag.name)
            chip.applyTagStyle(tag.color)
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(tag.name) else selected.remove(tag.name)
            }
            binding.tagsGroup.addView(chip)
        }
    }

    private fun ensureTagsLoaded(onComplete: () -> Unit = {}) {
        if (availableTags.isNotEmpty()) {
            onComplete()
            return
        }
        tagsLoadingJob?.let { job ->
            lifecycleScope.launch {
                job.join()
                onComplete()
            }
            return
        }
        tagsLoadingJob = lifecycleScope.launch {
            val result = postRepository.getTags()
            if (result.isSuccess) {
                val loaded = result.getOrDefault(emptyList())
                val wasEmpty = availableTags.isEmpty()
                val isFeedTab = currentTab.isFeed()
                availableTags = loaded
                if (wasEmpty && loaded.isNotEmpty() && isFeedTab) {
                    val state = latestState
                    runOnUiThread { renderState(state) }
                }
            }
            tagsLoadingJob = null
            onComplete()
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
        DRAFTS,
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
        tokenStorage.getUserId()?.let { cachedId ->
            currentUserId = cachedId
            onLoaded(cachedId)
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
                            val id = profile.id
                            currentUserId = id
                            if (id != null) {
                                tokenStorage.saveUserId(id)
                            }
                        }
                        .onFailure {
                            Log.w(TAG, "Failed to fetch current user id: ${it.message}")
                            // Attempt to infer current user id from any local draft author (offline fallback)
                            try {
                                val inferred = postRepository.getAnyDraftAuthorId()
                                if (inferred != null) {
                                    currentUserId = inferred
                                    tokenStorage.saveUserId(inferred)
                                    Log.d(TAG, "Inferred current user id from local drafts: $inferred")
                                }
                            } catch (t: Exception) {
                                Log.w(TAG, "Failed to infer user id from local drafts: ${t.message}")
                            }
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

    private fun ContentTab.isFeed(): Boolean =
        this == ContentTab.RECIPES || this == ContentTab.ARTICLES

    override fun onPostCreated(post: PostCard) {
        val targetTabId = if (normalizePostType(post.postType) == ARTICLE_POST_TYPE) {
            R.id.menu_articles
        } else {
            R.id.menu_recipes
        }
        binding.bottomNavigation.selectedItemId = targetTabId
        postViewModel.loadPosts(filtersForCurrentTab(), currentPostType())
        openPost(post)
    }

    override fun onPostUpdated(postId: Long) {
        postViewModel.loadPosts(filtersForCurrentTab(), currentPostType())
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
        lifecycleScope.launch {
            localCacheManager.clearAll()
            postViewModel.setCurrentUser(null)
        }
        restoreFeedTab()
    }

    override fun onOpenUserProfile(userId: Long, displayName: String?, subscribed: Boolean?) {
        openPublicProfile(userId, displayName, subscribed)
    }

    private fun restoreFeedTab() {
        binding.bottomNavigation.selectedItemId = lastFeedTabId
    }

    companion object {
        private const val DEFAULT_POST_TYPE = PostFilters.RECIPE_POST_TYPE
        private const val ARTICLE_POST_TYPE = PostFilters.ARTICLE_POST_TYPE
        private const val CREATE_TAG = "create_tab_fragment"
        private const val PROFILE_TAG = "profile_tab_fragment"
        private const val PUBLIC_PROFILE_TAG = "public_profile_fragment"
        private const val DRAFTS_TAG = "drafts_tab_fragment"
        private val DEFAULT_TAB_ID = R.id.menu_recipes
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_TAB_RECIPES = "tab_recipes"
        const val EXTRA_TAB_ARTICLES = "tab_articles"
        const val EXTRA_TAB_DRAFTS = "tab_drafts"
        const val EXTRA_TARGET_USER_ID = "extra_target_user_id"
        const val EXTRA_TARGET_USER_NAME = "extra_target_user_name"
        const val EXTRA_TARGET_USER_SUBSCRIBED = "extra_target_user_subscribed"
        private const val STATE_LIKED_POSTS = "state_liked_posts"
        private const val TAG = "MainActivity"
        private val OFFLINE_CACHE_MESSAGES = setOf(OFFLINE_LIKE_CACHED, OFFLINE_UNLIKE_CACHED)
    }

    private fun normalizePostType(postType: String?): String =
        postType?.lowercase()?.takeIf { it.isNotBlank() } ?: DEFAULT_POST_TYPE

    fun isSwipeRefreshing(): Boolean = binding.swipeRefresh.isRefreshing

    fun stopSwipeRefresh() {
        if (binding.swipeRefresh.isRefreshing) binding.swipeRefresh.isRefreshing = false
    }
}
