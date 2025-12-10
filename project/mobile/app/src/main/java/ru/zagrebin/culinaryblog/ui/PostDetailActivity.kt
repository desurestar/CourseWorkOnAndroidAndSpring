package ru.zagrebin.culinaryblog.ui

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
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.databinding.ActivityPostDetailBinding
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostStep
import javax.inject.Inject

@AndroidEntryPoint
class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    @Inject lateinit var postRepository: PostRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val post = readPostFromIntent()
        if (post == null) {
            finish()
            return
        }
        renderPreview(post)
        loadFull(post.id)
    }

    private fun renderPreview(post: PostCard) {
        val isRecipe = normalizePostType(post.postType) == RECIPE_POST_TYPE

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
    }

    private fun renderFull(post: PostFull) {
        val isRecipe = normalizePostType(post.postType) == RECIPE_POST_TYPE

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
            view.setTextAppearance(R.style.TextAppearance_MaterialComponents.Body2)
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
        binding.likesText.text = getString(R.string.likes_format, likes)
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

    companion object {
        const val EXTRA_POST = "extra_post"
        private const val RECIPE_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
    }
}
