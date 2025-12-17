package ru.zagrebin.culinaryblog

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.databinding.ActivityMainBinding
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.ui.CreatePostActivity
import ru.zagrebin.culinaryblog.ui.ProfileActivity
import ru.zagrebin.culinaryblog.ui.PostDetailActivity
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val postViewModel: PostViewModel by viewModels()
    private var latestState: PostsUiState = PostsUiState(isLoading = true)
    @Inject lateinit var tokenStorage: TokenStorage

    private val createPostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val created = result.data?.let { data ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    data.getParcelableExtra(CreatePostActivity.EXTRA_CREATED_POST, PostCard::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data.getParcelableExtra(CreatePostActivity.EXTRA_CREATED_POST)
                }
            }
            created?.let { post ->
                val targetTabId = if (normalizePostType(post.postType) == ARTICLE_POST_TYPE) {
                    R.id.menu_articles
                } else {
                    R.id.menu_recipes
                }
                binding.bottomNavigation.selectedItemId = targetTabId
                postViewModel.loadPosts()
                openPost(post)
            } ?: postViewModel.loadPosts()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private var currentTab: ContentTab = ContentTab.RECIPES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.swipeRefresh.setOnRefreshListener { postViewModel.loadPosts() }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.menu_create) {
                if (tokenStorage.getToken().isNullOrBlank()) {
                    startActivity(Intent(this, AuthActivity::class.java))
                } else {
                    createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
                }
                return@setOnItemSelectedListener false
            }
            if (item.itemId == R.id.menu_profile) {
                if (tokenStorage.getToken().isNullOrBlank()) {
                    startActivity(Intent(this, AuthActivity::class.java))
                } else {
                    startActivity(Intent(this, ProfileActivity::class.java))
                }
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collect { state ->
                    latestState = state
                    renderState(state)
                }
            }
        }

        applySelection(binding.bottomNavigation.selectedItemId)
    }

    private fun applySelection(itemId: Int) {
        currentTab = when (itemId) {
            R.id.menu_recipes -> ContentTab.RECIPES
            R.id.menu_articles -> ContentTab.ARTICLES
            else -> ContentTab.OTHER
        }

        if (currentTab == ContentTab.OTHER) {
            binding.titleText.text = getString(R.string.view_stub_title)
            binding.subtitleText.text = getString(R.string.view_stub_message)
            binding.postsContent.isVisible = false
            binding.stubText.isVisible = true
            binding.swipeRefresh.isEnabled = false
            binding.swipeRefresh.isRefreshing = false
            binding.stubText.text = when (itemId) {
                R.id.menu_create -> getString(R.string.create_stub_message)
                R.id.menu_messenger -> getString(R.string.messenger_stub_message)
                R.id.menu_profile -> getString(R.string.profile_stub_message)
                else -> getString(R.string.view_stub_message)
            }
            return
        }

        binding.postsContent.isVisible = true
        binding.stubText.isVisible = false
        binding.swipeRefresh.isEnabled = true

        if (currentTab == ContentTab.RECIPES) {
            binding.titleText.text = getString(R.string.nav_recipes)
            binding.subtitleText.text = getString(R.string.recipes_subtitle)
        } else {
            binding.titleText.text = getString(R.string.nav_articles)
            binding.subtitleText.text = getString(R.string.articles_subtitle)
        }

        renderState(latestState)
    }

    private fun renderState(state: PostsUiState) {
        if (currentTab == ContentTab.OTHER) return

        binding.progressBar.isVisible = state.isLoading
        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error ?: ""
        binding.swipeRefresh.isRefreshing = state.isLoading && currentTab != ContentTab.OTHER

        val filteredPosts = filterPosts(state.posts)
        binding.emptyText.isVisible = !state.isLoading && state.error == null && filteredPosts.isEmpty()

        renderPosts(filteredPosts)
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
                post.publishedAt ?: getString(R.string.published_unknown)
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
            cardBinding.likesText.text = getString(R.string.likes_format, post.likesCount)

            val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
            cardBinding.postCover.load(coverUrl) {
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
                crossfade(true)
            }

            cardBinding.root.setOnClickListener { openPost(post) }

            binding.postsContainer.addView(cardBinding.root)
        }
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
        OTHER
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
    }

    companion object {
        private const val DEFAULT_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
        private  val DEFAULT_TAB_ID = R.id.menu_recipes
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_TAB_RECIPES = "tab_recipes"
        const val EXTRA_TAB_ARTICLES = "tab_articles"
    }

    private fun normalizePostType(postType: String?): String =
        postType?.lowercase()?.takeIf { it.isNotBlank() } ?: DEFAULT_POST_TYPE
}
