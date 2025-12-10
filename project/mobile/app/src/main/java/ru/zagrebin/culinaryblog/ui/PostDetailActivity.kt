package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import coil.load
import com.google.android.material.chip.Chip
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.ActivityPostDetailBinding
import ru.zagrebin.culinaryblog.model.PostCard

class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val post = intent.getSerializableExtra(EXTRA_POST) as? PostCard
        if (post == null) {
            finish()
            return
        }
        render(post)
    }

    private fun render(post: PostCard) {
        val isRecipe = normalizePostType(post.postType) == DEFAULT_POST_TYPE

        binding.authorName.text =
            post.authorName?.ifBlank { getString(R.string.author_unknown) }
                ?: getString(R.string.author_unknown)
        binding.avatarInitial.text = post.authorName?.firstOrNull()?.uppercase() ?: "?"
        binding.postType.text = formatType(post.postType)
        binding.publishedAt.text = post.publishedAt ?: getString(R.string.published_unknown)

        binding.postTitle.text = post.title.ifBlank { getString(R.string.card_title_placeholder) }
        binding.postDescription.text =
            post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
        binding.postContent.text =
            post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }

        val coverUrl = post.coverUrl?.takeIf { it.isNotBlank() }
        binding.postCover.load(coverUrl) {
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.bg_image_placeholder)
            crossfade(true)
        }

        bindIngredients(isRecipe, post.tags)
        bindSteps(isRecipe, post.excerpt)
        bindMeta(isRecipe, post)
    }

    private fun bindIngredients(isRecipe: Boolean, tags: Set<String>?) {
        binding.ingredientsSection.isVisible = isRecipe
        if (!isRecipe) return

        binding.ingredientsGroup.removeAllViews()
        val ingredients = tags?.filter { it.isNotBlank() } ?: emptyList()
        binding.ingredientsGroup.isVisible = ingredients.isNotEmpty()
        binding.ingredientsStub.isVisible = ingredients.isEmpty()

        ingredients.forEach { ingredient ->
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

    private fun bindSteps(isRecipe: Boolean, excerpt: String) {
        binding.stepsSection.isVisible = isRecipe
        if (!isRecipe) return

        binding.stepsContainer.removeAllViews()
        val steps = excerpt.split(".", "!", "?")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        binding.stepsStub.isVisible = steps.isEmpty()
        steps.forEachIndexed { index, step ->
            val view = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                binding.stepsContainer,
                false
            ) as android.widget.TextView
            view.text = "${index + 1}. $step"
            view.setTextColor(ContextCompat.getColor(this, R.color.recipe_primary))
            binding.stepsContainer.addView(view)
        }
    }

    private fun bindMeta(isRecipe: Boolean, post: PostCard) {
        binding.cookingTime.isVisible = isRecipe && post.cookingTimeMinutes != null
        binding.calories.isVisible = isRecipe && post.calories != null

        post.cookingTimeMinutes?.let {
            binding.cookingTime.text = getString(R.string.cooking_time_format, it)
        }
        post.calories?.let {
            binding.calories.text = getString(R.string.calories_format, it)
        }

        binding.viewsText.text = getString(R.string.views_format, (post.viewsCount ?: 0).toInt())
        binding.likesText.text = getString(R.string.likes_format, post.likesCount)
    }

    private fun formatType(postType: String?): String =
        if (normalizePostType(postType) == ARTICLE_POST_TYPE) {
            getString(R.string.post_type_article)
        } else {
            getString(R.string.post_type_recipe)
        }

    private fun normalizePostType(postType: String?): String =
        postType?.lowercase()?.takeIf { it.isNotBlank() } ?: DEFAULT_POST_TYPE

    companion object {
        const val EXTRA_POST = "extra_post"
        private const val DEFAULT_POST_TYPE = "recipe"
        private const val ARTICLE_POST_TYPE = "article"
    }
}
