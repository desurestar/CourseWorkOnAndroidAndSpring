package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityPostDetailBinding
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
    @Inject lateinit var tokenStorage: TokenStorage

    private var currentPostId: Long = -1
    private var isLiked: Boolean = false
    private var likesCount: Int = 0
    private var replyTo: Comment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.buttonLike.setOnClickListener { toggleLike() }
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
        binding.publishedAt.text = post.publishedAt ?: getString(R.string.published_unknown)

        binding.postTitle.text = post.title.ifBlank { getString(R.string.card_title_placeholder) }
        binding.postDescription.text =
            post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
        binding.postContent.text = getString(R.string.content_stub)

        val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
        binding.postCover.load(coverUrl) {
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.bg_image_placeholder)
            crossfade(true)
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
        binding.publishedAt.text = post.createdAt ?: getString(R.string.published_unknown)

        binding.postTitle.text = post.title?.ifBlank { getString(R.string.card_title_placeholder) }
            ?: getString(R.string.card_title_placeholder)
        binding.postDescription.text =
            post.excerpt?.ifBlank { getString(R.string.card_excerpt_placeholder) }
                ?: getString(R.string.card_excerpt_placeholder)
        binding.postContent.text =
            post.content?.ifBlank { getString(R.string.content_stub) }
                ?: getString(R.string.content_stub)

        val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
        binding.postCover.load(coverUrl) {
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.bg_image_placeholder)
            crossfade(true)
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
            val view = TextView(this)
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            view.text = "${index + 1}. ${step.description}"
            view.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            view.setTextColor(ContextCompat.getColor(this, R.color.recipe_primary))
            view.textSize = 16f
            binding.stepsContainer.addView(view)
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
            if (result.isSuccess) {
                isLiked = !isLiked
                likesCount = (likesCount + if (isLiked) 1 else -1).coerceAtLeast(0)
                updateLikeUi()
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
        binding.buttonLike.text = if (isLiked) getString(R.string.action_unlike) else getString(R.string.action_like)
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
                val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, binding.commentsList, false)
                val title = view.findViewById<TextView>(android.R.id.text1)
                val body = view.findViewById<TextView>(android.R.id.text2)
                val date = comment.createdAt.substringBefore("T")
                title.text = "${comment.authorName} • $date"
                body.text = comment.message
                val paddingStart = (depth * 28) + view.paddingStart
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

        val author = "Вы"
        commentRepository.addComment(currentPostId, author, text, replyTo?.id)
        binding.inputComment.text?.clear()
        clearReplyTarget()
    }

    private fun openAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
    }

    companion object {
        const val EXTRA_POST = "extra_post"
        private const val RECIPE_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
    }
}
