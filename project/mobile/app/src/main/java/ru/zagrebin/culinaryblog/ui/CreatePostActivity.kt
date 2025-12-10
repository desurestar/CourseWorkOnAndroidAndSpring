package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.ActivityCreatePostBinding
import ru.zagrebin.culinaryblog.databinding.ItemIngredientRowBinding
import ru.zagrebin.culinaryblog.databinding.ItemStepRowBinding
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostIngredientRequest
import ru.zagrebin.culinaryblog.model.RecipeStepRequest
import ru.zagrebin.culinaryblog.model.TagItem
import ru.zagrebin.culinaryblog.viewmodel.CreatePostViewModel

@AndroidEntryPoint
class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val viewModel: CreatePostViewModel by viewModels()

    private val ingredientRows = mutableListOf<ItemIngredientRowBinding>()
    private val stepRows = mutableListOf<ItemStepRowBinding>()
    private val selectedTags = mutableSetOf<Long>()

    private var tags: List<TagItem> = emptyList()
    private var ingredients: List<IngredientItem> = emptyList()

    private val statusValues = listOf("draft", "published")
    private val statusLabels by lazy {
        listOf(getString(R.string.status_draft), getString(R.string.status_published))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val passedAuthorId = intent.getLongExtra(EXTRA_AUTHOR_ID, viewModel.authorId)
        viewModel.setAuthorId(passedAuthorId)

        setupStatusSpinner()
        setupClicks()
        observeState()

        viewModel.loadTags()
        viewModel.loadIngredients()
        addIngredientRow()
        addStepRow()
    }

    private fun setupStatusSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statusLabels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.statusSpinner.adapter = adapter
    }

    private fun setupClicks() {
        binding.buttonSearchTags.setOnClickListener {
            viewModel.loadTags(binding.inputTagSearch.text.toString().trim().ifBlank { null })
        }
        binding.buttonSearchIngredients.setOnClickListener {
            viewModel.loadIngredients(binding.inputIngredientSearch.text.toString().trim().ifBlank { null })
        }
        binding.buttonAddIngredient.setOnClickListener { addIngredientRow() }
        binding.buttonAddStep.setOnClickListener { addStepRow() }
        binding.buttonSubmit.setOnClickListener { submit() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressTags.isVisible = state.loadingTags
                    binding.progressIngredients.isVisible = state.loadingIngredients
                    binding.progressSubmit.isVisible = state.submitting
                    binding.buttonSubmit.isEnabled = !state.submitting
                    binding.textError.isVisible = state.error != null
                    binding.textError.text = state.error ?: ""

                    val newTags = state.tags
                    if (newTags != tags) {
                        tags = newTags
                        renderTags()
                    }

                    val newIngredients = state.ingredients
                    if (newIngredients != ingredients) {
                        ingredients = newIngredients
                        renderIngredientAdapters()
                    }

                    state.created?.let {
                        Toast.makeText(
                            this@CreatePostActivity,
                            getString(R.string.create_success),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun renderTags() {
        binding.tagsContainer.removeAllViews()
        if (tags.isEmpty()) {
            val stub = layoutInflater.inflate(android.R.layout.simple_list_item_1, binding.tagsContainer, false)
            (stub.findViewById(android.R.id.text1) as? android.widget.TextView)?.text =
                getString(R.string.create_tags_empty)
            binding.tagsContainer.addView(stub)
            return
        }

        tags.forEach { tag ->
            val checkbox = CheckBox(this)
            checkbox.text = tag.name
            checkbox.isChecked = selectedTags.contains(tag.id)
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedTags.add(tag.id) else selectedTags.remove(tag.id)
            }
            binding.tagsContainer.addView(checkbox)
        }
    }

    private fun renderIngredientAdapters() {
        val labels = listOf(getString(R.string.create_choose_ingredient)) + ingredients.map { it.name }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        ingredientRows.forEach { row ->
            row.spinnerIngredient.adapter = adapter
            val currentId = row.spinnerIngredient.tag as? Long
            if (currentId != null) {
                val idx = ingredients.indexOfFirst { it.id == currentId }
                if (idx >= 0) {
                    row.spinnerIngredient.setSelection(idx + 1, false)
                }
            }
            row.spinnerIngredient.onItemSelectedListener = null
            row.spinnerIngredient.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val selected = if (position > 0 && position - 1 < ingredients.size) {
                        ingredients[position - 1].id
                    } else null
                    row.spinnerIngredient.tag = selected
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
    }

    private fun addIngredientRow() {
        val rowBinding = ItemIngredientRowBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
        rowBinding.buttonRemoveIngredient.setOnClickListener {
            binding.ingredientsContainer.removeView(rowBinding.root)
            ingredientRows.remove(rowBinding)
        }
        ingredientRows.add(rowBinding)
        binding.ingredientsContainer.addView(rowBinding.root)
        renderIngredientAdapters()
    }

    private fun addStepRow() {
        val rowBinding = ItemStepRowBinding.inflate(layoutInflater, binding.stepsContainer, false)
        rowBinding.buttonRemoveStep.setOnClickListener {
            binding.stepsContainer.removeView(rowBinding.root)
            stepRows.remove(rowBinding)
        }
        stepRows.add(rowBinding)
        binding.stepsContainer.addView(rowBinding.root)
    }

    private fun submit() {
        val title = binding.inputTitle.text.toString().trim()
        val excerpt = binding.inputExcerpt.text.toString().trim()
        val content = binding.inputContent.text.toString().trim()
        if (title.isBlank() || excerpt.isBlank() || content.isBlank()) {
            binding.textError.isVisible = true
            binding.textError.text = getString(R.string.create_fill_required)
            return
        }

        val ingredientRequests = mutableListOf<PostIngredientRequest>()
        var invalidAmount = false
        ingredientRows.forEach { row ->
            val selectedId = row.spinnerIngredient.tag as? Long
            val amount = row.inputAmount.text.toString().trim().toDoubleOrNull()
            val unit = row.inputUnit.text.toString().trim().ifBlank { null }
            if (selectedId != null) {
                if (amount == null || amount <= 0.0) {
                    invalidAmount = true
                } else {
                    ingredientRequests.add(
                        PostIngredientRequest(
                            ingredientId = selectedId,
                            quantityValue = amount,
                            unit = unit
                        )
                    )
                }
            }
        }

        if (invalidAmount) {
            binding.textError.isVisible = true
            binding.textError.text = getString(R.string.create_ingredient_error)
            return
        }

        if (ingredientRows.isNotEmpty() && ingredientRequests.isEmpty()) {
            binding.textError.isVisible = true
            binding.textError.text = getString(R.string.create_ingredient_error)
            return
        }

        val stepRequests = stepRows.mapIndexed { index, row ->
            RecipeStepRequest(
                order = index + 1,
                description = row.inputStepDescription.text.toString().trim(),
                imageUrl = row.inputStepImage.text.toString().trim().ifBlank { null }
            )
        }.filter { it.description.isNotBlank() }

        if (stepRows.isNotEmpty() && stepRequests.isEmpty()) {
            binding.textError.isVisible = true
            binding.textError.text = getString(R.string.create_step_error)
            return
        }

        val request = PostCreateRequest(
            postType = "recipe",
            status = statusValues.getOrNull(binding.statusSpinner.selectedItemPosition) ?: "draft",
            title = title,
            excerpt = excerpt,
            content = content,
            coverUrl = binding.inputCoverUrl.text.toString().trim().ifBlank { null },
            cookingTimeMinutes = binding.inputCookingTime.text.toString().trim().toIntOrNull(),
            calories = binding.inputCalories.text.toString().trim().toIntOrNull(),
            authorId = viewModel.authorId,
            tagIds = selectedTags.toList(),
            ingredients = ingredientRequests,
            steps = stepRequests
        )

        binding.textError.isVisible = false
        viewModel.createPost(request)
    }

    companion object {
        const val EXTRA_AUTHOR_ID = "extra_author_id"
    }
}
