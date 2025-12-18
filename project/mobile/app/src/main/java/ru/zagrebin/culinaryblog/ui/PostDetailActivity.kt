package ru.zagrebin.culinaryblog.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.data.repository.CommentRepository
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.data.repository.ProfileRepository
import ru.zagrebin.culinaryblog.data.repository.OFFLINE_LIKE_CACHED
import ru.zagrebin.culinaryblog.data.repository.OFFLINE_UNLIKE_CACHED
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityPostDetailBinding
import ru.zagrebin.culinaryblog.formatDisplayDate
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostStep
import ru.zagrebin.culinaryblog.model.Comment
import javax.inject.Inject

@AndroidEntryPoint
class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    @Inject lateinit var postRepository: PostRepository
    @Inject lateinit var commentRepository: CommentRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var tokenStorage: TokenStorage

    private var currentPostId: Long = -1
    private var isLiked: Boolean = false
    private var likesCount: Int = 0
    private var likeStateChanged: Boolean = false
    private var replyTo: Comment? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backPressedCallback = onBackPressedDispatcher.addCallback(this) { finishWithResult() }

        val post = readPostFromIntent()
        if (post == null) {
            finish()
            return
        }
        currentPostId = post.id
        likesCount = post.likesCount
        renderPreview(post)
        setupInteractions()
        collectComments(post.id)
        loadFull(post.id)
    }

    private fun setupInteractions() {
        binding.likesText.setOnClickListener { toggleLike() }
        binding.buttonSendComment.setOnClickListener { sendComment() }
        binding.buttonCancelReply.setOnClickListener { clearReplyTarget() }
        updateLikeUi()
    }

    private fun renderPreview(post: PostCard) {
        val isRecipe = normalizePostType(post.postType) == RECIPE_POST_TYPE
        isLiked = false

        binding.authorName.text =
            post.authorName?.ifBlank { getString(R.string.author_unknown) }
                ?: getString(R.string.author_unknown)
        binding.avatarInitial.text = post.authorName?.firstOrNull()?.uppercase() ?: "?"
        binding.postType.text = formatType(post.postType)
        binding.publishedAt.text = formatDisplayDate(post.publishedAt) ?: getString(R.string.published_unknown)

        binding.postTitle.text = post.title.ifBlank { getString(R.string.card_title_placeholder) }
        binding.postDescription.text =
            post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
        binding.postContent.text = getString(R.string.content_stub)

        val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
        binding.postCover.isVisible = coverUrl != null
        if (coverUrl != null) {
            binding.postCover.load(coverUrl) {
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
                crossfade(true)
            }
        } else {
            binding.postCover.setImageDrawable(null)
        }

        bindIngredients(isRecipe, post.tags?.toList())
        bindSteps(isRecipe, emptyList())
        bindMeta(isRecipe, post.cookingTimeMinutes, post.calories, post.viewsCount, post.likesCount)
        updateLikeUi()
    }

    private fun renderFull(post: PostFull) {
        val isRecipe = normalizePostType(post.postType) == RECIPE_POST_TYPE
        currentPostId = post.id
        likesCount = post.likesCount
        isLiked = post.liked

        binding.authorName.text =
            post.author?.displayName?.ifBlank { getString(R.string.author_unknown) }
                ?: getString(R.string.author_unknown)
        binding.avatarInitial.text = post.author?.displayName?.firstOrNull()?.uppercase() ?: "?"
        binding.postType.text = formatType(post.postType)
        binding.publishedAt.text = formatDisplayDate(post.createdAt) ?: getString(R.string.published_unknown)

        binding.postTitle.text = post.title?.ifBlank { getString(R.string.card_title_placeholder) }
            ?: getString(R.string.card_title_placeholder)
        binding.postDescription.text =
            post.excerpt?.ifBlank { getString(R.string.card_excerpt_placeholder) }
                ?: getString(R.string.card_excerpt_placeholder)
        binding.postContent.text =
            post.content?.ifBlank { getString(R.string.content_stub) }
                ?: getString(R.string.content_stub)

        val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
        binding.postCover.isVisible = coverUrl != null
        if (coverUrl != null) {
            binding.postCover.load(coverUrl) {
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
                crossfade(true)
            }
        } else {
            binding.postCover.setImageDrawable(null)
        }

        val ingredientLabels = post.ingredients.map { ingredient ->
            val quantity = formatQuantity(ingredient.quantityValue, ingredient.unit)
            if (quantity.isNotBlank()) "${ingredient.ingredientName} — $quantity" else ingredient.ingredientName
        }

        bindIngredients(isRecipe, ingredientLabels)
        bindSteps(isRecipe, post.steps)
        bindMeta(isRecipe, post.cookingTimeMinutes, post.calories, post.viewsCount, post.likesCount)
        updateLikeUi()
    }

    private fun bindIngredients(isRecipe: Boolean, ingredients: List<String>?) {
        binding.ingredientsSection.isVisible = isRecipe
        if (!isRecipe) return

        binding.ingredientsGroup.removeAllViews()
        val ingredientsSafe = ingredients?.filter { it.isNotBlank() } ?: emptyList()
        binding.ingredientsGroup.isVisible = ingredientsSafe.isNotEmpty()
        binding.ingredientsStub.isVisible = ingredientsSafe.isEmpty()

        ingredientsSafe.forEach { ingredient ->
            val chip = Chip(this)
            chip.text = ingredient
            chip.isClickable = false
            chip.isCheckable = false
            chip.chipBackgroundColor =
                ContextCompat.getColorStateList(this, R.color.recipe_primary_light)
            chip.setTextColor(ContextCompat.getColor(this, R.color.recipe_primary))
            binding.ingredientsGroup.addView(chip)
        }
    }

    private fun bindSteps(isRecipe: Boolean, steps: List<PostStep>) {
        binding.stepsSection.isVisible = isRecipe
        if (!isRecipe) return

        binding.stepsContainer.removeAllViews()
        val stepsSafe = steps.sortedBy { it.order }
        binding.stepsStub.isVisible = stepsSafe.isEmpty()
        stepsSafe.forEachIndexed { index, step ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.step_item_margin_bottom) }
            }

            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "${index + 1}. ${step.description}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
                setTextColor(ContextCompat.getColor(this@PostDetailActivity, R.color.recipe_primary))
                textSize = 16f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
                }
            }
            container.addView(textView)

            val imageUrl = step.imageUrl?.takeIf { it.isNotBlank() }
            if (imageUrl != null) {
                val imageView = android.widget.ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.step_item_image_height)
                    ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.step_item_image_margin_top) }
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_image_placeholder)
                }
                imageView.load(imageUrl) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                    crossfade(true)
                }
                container.addView(imageView)
            }

            binding.stepsContainer.addView(container)
        }
    }

    private fun bindMeta(isRecipe: Boolean, cookingTime: Int?, calories: Int?, views: Long?, likes: Int) {
        binding.cookingTime.isVisible = isRecipe && cookingTime != null
        binding.calories.isVisible = isRecipe && calories != null

        cookingTime?.let {
            binding.cookingTime.text = getString(R.string.cooking_time_format, it)
        }
        calories?.let {
            binding.calories.text = getString(R.string.calories_format, it)
        }

        binding.viewsText.text = getString(R.string.views_format, views ?: 0L)
        likesCount = likes
        binding.likesText.text = getString(R.string.likes_format, likesCount)
    }

    private fun loadFull(id: Long) {
        lifecycleScope.launch {
            val result = postRepository.getPost(id)
            if (result.isSuccess) {
                result.getOrNull()?.let { renderFull(it) }
            } else {
                Toast.makeText(
                    this@PostDetailActivity,
                    R.string.error_loading,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun formatQuantity(quantity: Double?, unit: String?): String {
        val value = quantity?.toString() ?: ""
        val unitSafe = unit?.takeIf { it.isNotBlank() } ?: ""
        return "$value $unitSafe".trim()
    }

    private fun formatType(postType: String?): String =
        if (normalizePostType(postType) == ARTICLE_POST_TYPE) {
            getString(R.string.post_type_article)
        } else {
            getString(R.string.post_type_recipe)
        }

    private fun normalizePostType(postType: String?): String =
        postType?.lowercase()?.takeIf { it.isNotBlank() } ?: RECIPE_POST_TYPE

    private fun readPostFromIntent(): PostCard? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_POST, PostCard::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_POST)
        }
    }

    private fun toggleLike() {
        if (currentPostId <= 0) return
        if (tokenStorage.getToken().isNullOrBlank()) {
            openAuth()
            return
        }

        lifecycleScope.launch {
            val result = if (isLiked) postRepository.unlike(currentPostId) else postRepository.like(currentPostId)
            val offlineHandled = result.exceptionOrNull()?.message in setOf(OFFLINE_LIKE_CACHED, OFFLINE_UNLIKE_CACHED)
            if (result.isSuccess || offlineHandled) {
                isLiked = !isLiked
                likesCount = (likesCount + if (isLiked) 1 else -1).coerceAtLeast(0)
                likeStateChanged = true
                updateLikeUi()
                if (offlineHandled) {
                    Toast.makeText(
                        this@PostDetailActivity,
                        R.string.offline_feed_message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@PostDetailActivity,
                    R.string.error_loading,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateLikeUi() {
        binding.likesText.text = getString(R.string.likes_format, likesCount)
        val color = if (isLiked) R.color.recipe_primary else R.color.text_muted
        binding.likesText.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun collectComments(postId: Long) {
        lifecycleScope.launch {
            commentRepository.getComments(postId).collectLatest { renderComments(it) }
        }
    }

    private fun renderComments(items: List<Comment>) {
        binding.commentsStub.isVisible = items.isEmpty()
        binding.commentsList.isVisible = items.isNotEmpty()
        binding.commentsList.removeAllViews()

        val byParent = items.groupBy { it.parentId }

        fun renderLevel(parentId: Long?, depth: Int) {
            val level = byParent[parentId] ?: return
            level.forEach { comment ->
                val view = layoutInflater.inflate(R.layout.item_comment, binding.commentsList, false)
                val avatar = view.findViewById<TextView>(R.id.commentAvatar)
                val author = view.findViewById<TextView>(R.id.commentAuthor)
                val date = view.findViewById<TextView>(R.id.commentDate)
                val message = view.findViewById<TextView>(R.id.commentMessage)

                avatar.text = comment.authorName.firstOrNull()?.uppercase() ?: "?"
                author.text = comment.authorName
                date.text = formatDisplayDate(comment.createdAt) ?: getString(R.string.published_unknown)
                message.text = comment.message

                val paddingStart = (depth * resources.getDimensionPixelSize(R.dimen.comment_indent)) + view.paddingStart
                view.setPaddingRelative(paddingStart, view.paddingTop, view.paddingEnd, view.paddingBottom)
                view.setOnClickListener { setReplyTarget(comment) }
                binding.commentsList.addView(view)
                renderLevel(comment.id, depth + 1)
            }
        }

        renderLevel(null, 0)
    }

    private fun setReplyTarget(comment: Comment) {
        replyTo = comment
        binding.replyRow.isVisible = true
        binding.replyLabel.text = getString(R.string.comments_replying) + " — " + comment.authorName
    }

    private fun clearReplyTarget() {
        replyTo = null
        binding.replyRow.isVisible = false
    }

    private fun sendComment() {
        if (currentPostId <= 0) return
        val text = binding.inputComment.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.comments_hint, Toast.LENGTH_SHORT).show()
            return
        }

        if (tokenStorage.getToken().isNullOrBlank()) {
            openAuth()
            return
        }

        lifecycleScope.launch {
            try {
                val author = resolveAuthorName()
                commentRepository.addComment(currentPostId, author, text, replyTo?.id)
                binding.inputComment.text?.clear()
                clearReplyTarget()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add comment", e)
                Toast.makeText(this@PostDetailActivity, R.string.error_posting_comment, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun resolveAuthorName(): String {
        val profileResult = profileRepository.getProfile()
        profileResult.exceptionOrNull()?.let {
            Log.w(TAG, "Failed to fetch profile for comment author", it)
        }
        val profile = profileResult.getOrNull() ?: return getString(R.string.comment_author_you)
        return profile.displayName?.takeIf { it.isNotBlank() }
            ?: profile.username?.takeIf { it.isNotBlank() }
            ?: getString(R.string.comment_author_you)
    }

    private fun openAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
    }

    private fun finishWithResult() {
        if (likeStateChanged && currentPostId != -1L) {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(EXTRA_RESULT_POST_ID, currentPostId)
                putExtra(EXTRA_RESULT_LIKED, isLiked)
                putExtra(EXTRA_RESULT_LIKES_COUNT, likesCount)
            })
        }
        super.finish()
    }

    override fun onDestroy() {
        backPressedCallback?.remove()
        backPressedCallback = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_POST = "extra_post"
        const val EXTRA_RESULT_POST_ID = "extra_result_post_id"
        const val EXTRA_RESULT_LIKED = "extra_result_liked"
        const val EXTRA_RESULT_LIKES_COUNT = "extra_result_likes_count"
        private const val RECIPE_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
        private const val TAG = "PostDetailActivity"
    }
}
