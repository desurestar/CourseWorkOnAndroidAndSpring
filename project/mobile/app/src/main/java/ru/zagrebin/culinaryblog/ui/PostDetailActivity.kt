package ru.zagrebin.culinaryblog.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.Log
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.ui.CreatePostActivity
import ru.zagrebin.culinaryblog.MainActivity
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
import ru.zagrebin.culinaryblog.model.PostAuthor
import ru.zagrebin.culinaryblog.model.PostStep
import ru.zagrebin.culinaryblog.model.Comment
import ru.zagrebin.culinaryblog.util.renderAvatar
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
    private var author: PostAuthor? = null
    private var cachedCommentAuthor: CommentAuthor? = null
    private var cachedAuthorToken: String? = null
    private var currentUserId: Long? = null
    private val editPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val updatedId = result.data?.getLongExtra(CreatePostActivity.EXTRA_RESULT_UPDATED_POST_ID, -1L) ?: -1L
        if (updatedId > 0) {
            loadFull(updatedId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.postActionsRow.isVisible = false
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
        prefetchAuthorInfo()
        collectComments(post.id)
        loadFull(post.id)
    }

    private fun setupInteractions() {
        binding.authorRow.setOnClickListener { openAuthorProfile() }
        binding.likesText.setOnClickListener { toggleLike() }
        binding.buttonSendComment.setOnClickListener { sendComment() }
        binding.buttonCancelReply.setOnClickListener { clearReplyTarget() }
        binding.buttonEditPost.setOnClickListener { editPost() }
        binding.buttonDeletePost.setOnClickListener { confirmDeletePost() }
        updateLikeUi()
    }

    private fun renderPreview(post: PostCard) {
        val isRecipe = normalizePostType(post.postType) == RECIPE_POST_TYPE
        isLiked = false

        binding.authorName.text =
            post.authorName?.ifBlank { getString(R.string.author_unknown) }
                ?: getString(R.string.author_unknown)
        renderAvatar(binding.avatarImage, binding.avatarInitial, post.authorAvatarUrl, post.authorName)
        author = PostAuthor(post.authorId, post.authorName, post.authorAvatarUrl, null)
        updatePostActionsVisibility()
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
        renderAvatar(binding.avatarImage, binding.avatarInitial, post.author?.avatarUrl, post.author?.displayName)
        author = post.author
        updatePostActionsVisibility()
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
            styleInfoChip(chip)
            binding.ingredientsGroup.addView(chip)
        }
    }

    private fun styleInfoChip(chip: Chip) {
        chip.isClickable = false
        chip.isCheckable = false
        chip.chipBackgroundColor =
            ContextCompat.getColorStateList(this, R.color.recipe_primary_light)
        chip.setTextColor(ContextCompat.getColor(this, R.color.recipe_primary))
        chip.shapeAppearanceModel = chip.shapeAppearanceModel
            .toBuilder()
            .setAllCornerSizes(resources.getDimension(R.dimen.chip_corner_radius))
            .build()
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
                    justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
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
                updateLikeUi(true)
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

    private fun updateLikeUi(animated: Boolean = false) {
        binding.likesText.text = getString(R.string.likes_format, likesCount)
        val textColor = if (isLiked) R.color.text_error else R.color.text_muted
        val iconColor = if (isLiked) R.color.text_error else R.color.recipe_primary
        val icon = if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        binding.likesText.setTextColor(ContextCompat.getColor(this, textColor))
        binding.likesText.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        binding.likesText.compoundDrawablePadding =
            resources.getDimensionPixelSize(R.dimen.create_horizontal_space)
        TextViewCompat.setCompoundDrawableTintList(
            binding.likesText,
            ColorStateList.valueOf(ContextCompat.getColor(this, iconColor))
        )
        if (animated) animateLike(binding.likesText)
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
                val avatarImage = view.findViewById<ImageView>(R.id.commentAvatarImage)
                val avatarInitial = view.findViewById<TextView>(R.id.commentAvatarInitial)
                val author = view.findViewById<TextView>(R.id.commentAuthor)
                val date = view.findViewById<TextView>(R.id.commentDate)
                val message = view.findViewById<TextView>(R.id.commentMessage)
                val editButton = view.findViewById<android.widget.TextView>(R.id.actionEdit)
                val deleteButton = view.findViewById<android.widget.TextView>(R.id.actionDelete)

                val avatarUrl = comment.avatarUrl?.takeIf { it.isNotBlank() }
                if (avatarUrl != null) {
                    avatarInitial.isVisible = false
                    avatarImage.load(avatarUrl) {
                        placeholder(R.drawable.bg_avatar_placeholder)
                        error(R.drawable.bg_avatar_placeholder)
                        transformations(CircleCropTransformation())
                        crossfade(true)
                    }
                } else {
                    avatarInitial.isVisible = true
                    avatarImage.setImageResource(R.drawable.bg_avatar_placeholder)
                    avatarInitial.text = comment.authorName.firstOrNull()?.uppercase() ?: "?"
                }
                author.text = comment.authorName
                date.text = formatDisplayDate(comment.createdAt) ?: getString(R.string.published_unknown)
                message.text = comment.message
                val canModify = canModifyComment(comment)
                editButton.isVisible = canModify
                deleteButton.isVisible = canModify

                val paddingStart = (depth * resources.getDimensionPixelSize(R.dimen.comment_indent)) + view.paddingStart
                view.setPaddingRelative(paddingStart, view.paddingTop, view.paddingEnd, view.paddingBottom)
                view.setOnClickListener { setReplyTarget(comment) }
                editButton.setOnClickListener { showEditCommentDialog(comment) }
                deleteButton.setOnClickListener { deleteComment(comment) }
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

    private fun prefetchAuthorInfo() {
        lifecycleScope.launch {
            runCatching { resolveAuthor() }
            if (currentPostId > 0) {
                renderComments(commentRepository.getComments(currentPostId).value)
            }
        }
    }

    private fun updatePostActionsVisibility() {
        val canModifyPost = currentUserId != null && author?.id != null && currentUserId == author?.id
        binding.postActionsRow.isVisible = canModifyPost
    }

    private fun editPost() {
        if (currentPostId <= 0) return
        if (tokenStorage.getToken().isNullOrBlank()) {
            openAuth()
            return
        }
        val intent = Intent(this, CreatePostActivity::class.java)
            .putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, currentPostId)
        author?.id?.let { intent.putExtra(CreatePostActivity.EXTRA_AUTHOR_ID, it) }
        editPostLauncher.launch(intent)
    }

    private fun confirmDeletePost() {
        if (currentPostId <= 0) return
        if (tokenStorage.getToken().isNullOrBlank()) {
            openAuth()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.post_delete_confirm_title)
            .setMessage(R.string.post_delete_confirm_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> deletePost() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deletePost() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { postRepository.deletePost(currentPostId) }
            if (result.isSuccess) {
                Toast.makeText(this@PostDetailActivity, R.string.post_delete_success, Toast.LENGTH_SHORT).show()
                setResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        putExtra(EXTRA_RESULT_POST_ID, currentPostId)
                        putExtra(EXTRA_RESULT_DELETED, true)
                    }
                )
                finish()
            } else {
                Toast.makeText(this@PostDetailActivity, R.string.post_delete_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun canModifyComment(comment: Comment, authorInfo: CommentAuthor? = cachedCommentAuthor): Boolean {
        val currentId = authorInfo?.id ?: currentUserId
        if (currentId != null && comment.authorId != null) {
            return currentId == comment.authorId
        }
        val currentName = authorInfo?.name ?: return false
        return currentName == comment.authorName
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
                val author = resolveAuthor()
                commentRepository.addComment(
                    currentPostId,
                    author.name,
                    text,
                    replyTo?.id,
                    author.avatarUrl,
                    author.id
                )
                binding.inputComment.text?.clear()
                clearReplyTarget()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add comment", e)
                Toast.makeText(this@PostDetailActivity, R.string.error_posting_comment, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditCommentDialog(comment: Comment) {
        lifecycleScope.launch {
            val authorInfo = resolveAuthor()
            if (!canModifyComment(comment, authorInfo)) {
                Toast.makeText(this@PostDetailActivity, R.string.comment_modify_forbidden, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val input = EditText(this@PostDetailActivity).apply {
                setText(comment.message)
                setSelection(comment.message.length)
            }
            AlertDialog.Builder(this@PostDetailActivity)
                .setTitle(R.string.comment_edit_title)
                .setView(input)
                .setPositiveButton(R.string.action_save) { _, _ ->
                    val newText = input.text?.toString()?.trim().orEmpty()
                    if (newText.isBlank()) {
                        Toast.makeText(this@PostDetailActivity, R.string.comment_edit_empty_error, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        val updated = withContext(Dispatchers.IO) {
                            commentRepository.updateComment(comment.postId, comment.id, newText)
                        }
                        if (updated) {
                            Toast.makeText(this@PostDetailActivity, R.string.comment_edit_updated, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@PostDetailActivity, R.string.comment_edit_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun deleteComment(comment: Comment) {
        lifecycleScope.launch {
            val authorInfo = resolveAuthor()
            if (!canModifyComment(comment, authorInfo)) {
                Toast.makeText(this@PostDetailActivity, R.string.comment_modify_forbidden, Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@PostDetailActivity)
                .setTitle(R.string.comment_delete_confirm_title)
                .setMessage(R.string.comment_delete_confirm_message)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            commentRepository.deleteComment(comment.postId, comment.id)
                        }
                        if (result) {
                            Toast.makeText(this@PostDetailActivity, R.string.comment_deleted, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@PostDetailActivity, R.string.comment_delete_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private suspend fun resolveAuthor(): CommentAuthor {
        val currentToken = tokenStorage.getToken()
        if (cachedAuthorToken != null && cachedAuthorToken != currentToken) {
            cachedCommentAuthor = null
        }
        cachedCommentAuthor?.let { return it }
        val profileResult = profileRepository.getProfile()
        profileResult.exceptionOrNull()?.let {
            Log.w(TAG, "Failed to fetch profile for comment author", it)
        }
        val profile = profileResult.getOrNull()
        currentUserId = profile?.id
        updatePostActionsVisibility()
        val name = profile?.displayName?.takeIf { it.isNotBlank() }
            ?: profile?.username?.takeIf { it.isNotBlank() }
            ?: getString(R.string.comment_author_you)
        return CommentAuthor(
            name = name,
            avatarUrl = profile?.avatarUrl,
            id = profile?.id
        ).also {
            cachedCommentAuthor = it
            cachedAuthorToken = currentToken
        }
    }

    private fun openAuthorProfile() {
        val target = author ?: return
        val authorId = target.id ?: return
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TARGET_USER_ID, authorId)
                .putExtra(MainActivity.EXTRA_TARGET_USER_NAME, target.displayName)
                .putExtra(MainActivity.EXTRA_TARGET_USER_SUBSCRIBED, target.subscribed ?: false)
        )
        finish()
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

    private data class CommentAuthor(
        val name: String,
        val avatarUrl: String?,
        val id: Long?
    )

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

    companion object {
        const val EXTRA_POST = "extra_post"
        const val EXTRA_RESULT_POST_ID = "extra_result_post_id"
        const val EXTRA_RESULT_LIKED = "extra_result_liked"
        const val EXTRA_RESULT_LIKES_COUNT = "extra_result_likes_count"
        const val EXTRA_RESULT_DELETED = "extra_result_deleted"
        private const val RECIPE_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
        private const val TAG = "PostDetailActivity"
    }
}
