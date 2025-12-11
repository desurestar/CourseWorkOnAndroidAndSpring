package ru.zagrebin.culinaryblog.ui

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var selectedPostType: String = POST_TYPE_RECIPE
    private var pendingImageTarget: ImageTarget? = null

    private val statusValues = listOf("draft", "published")
    private val statusLabels by lazy {
        listOf(getString(R.string.status_draft), getString(R.string.status_published))
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleImageUri(it) } ?: run { pendingImageTarget = null }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val target = pendingImageTarget
        pendingImageTarget = null
        if (bitmap != null && target != null) {
            handleCameraBitmap(target, bitmap)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingImageTarget
        if (granted && target != null) {
            cameraLauncher.launch(null)
        } else {
            pendingImageTarget = null
            Toast.makeText(this, R.string.create_upload_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val passedAuthorId = intent.getLongExtra(EXTRA_AUTHOR_ID, viewModel.authorId)
        viewModel.setAuthorId(passedAuthorId)

        setupStatusSpinner()
        setupPostTypeSelector()
        setupClicks()
        observeState()

        viewModel.loadTags()
        viewModel.loadIngredients()
        updateRecipeVisibility()
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

    private fun setupPostTypeSelector() {
        binding.postTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPostType = if (checkedId == binding.radioArticle.id) {
                POST_TYPE_ARTICLE
            } else {
                POST_TYPE_RECIPE
            }
            updateRecipeVisibility()
        }
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
        binding.buttonPickCover.setOnClickListener { pickImage(ImageTarget.Cover) }
        binding.buttonCaptureCover.setOnClickListener { captureImage(ImageTarget.Cover) }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressTags.isVisible = state.loadingTags
                    binding.progressIngredients.isVisible = state.loadingIngredients
                    binding.progressSubmit.isVisible = state.submitting
                    binding.buttonSubmit.isEnabled = !state.submitting
                    val resolvedError = resolveErrorMessage(state.error)
                    binding.textError.isVisible = !resolvedError.isNullOrBlank()
                    binding.textError.text = resolvedError ?: ""

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
        rowBinding.buttonPickStepImage.setOnClickListener { pickImage(ImageTarget.Step(rowBinding)) }
        rowBinding.buttonCaptureStepImage.setOnClickListener { captureImage(ImageTarget.Step(rowBinding)) }
        stepRows.add(rowBinding)
        binding.stepsContainer.addView(rowBinding.root)
    }

    private fun updateRecipeVisibility() {
        val isRecipe = selectedPostType == POST_TYPE_RECIPE
        binding.recipeSection.isVisible = isRecipe
        if (isRecipe) {
            if (ingredientRows.isEmpty()) addIngredientRow()
            if (stepRows.isEmpty()) addStepRow()
        } else {
            ingredientRows.clear()
            stepRows.clear()
            binding.ingredientsContainer.removeAllViews()
            binding.stepsContainer.removeAllViews()
        }
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

        val isRecipe = selectedPostType == POST_TYPE_RECIPE

        val ingredientRequests = if (isRecipe) {
            val list = mutableListOf<PostIngredientRequest>()
            var invalidAmount = false
            ingredientRows.forEach { row ->
                val selectedId = row.spinnerIngredient.tag as? Long
                val amount = row.inputAmount.text.toString().trim().toDoubleOrNull()
                val unit = row.inputUnit.text.toString().trim().ifBlank { null }
                if (selectedId != null) {
                    if (amount == null || amount < MIN_POSITIVE_AMOUNT) {
                        invalidAmount = true
                    } else {
                        list.add(
                            PostIngredientRequest(
                                ingredientId = selectedId,
                                quantityValue = amount,
                                unit = unit
                            )
                        )
                    }
                }
            }

            if (invalidAmount || (ingredientRows.isNotEmpty() && list.isEmpty())) {
                binding.textError.isVisible = true
                binding.textError.text = getString(R.string.create_ingredient_error)
                return
            }
            list
        } else {
            emptyList()
        }

        val stepRequests = if (isRecipe) {
            val steps = stepRows.mapIndexed { index, row ->
                RecipeStepRequest(
                    order = index + 1,
                    description = row.inputStepDescription.text.toString().trim(),
                    imageUrl = row.inputStepImage.text.toString().trim().ifBlank { null }
                )
            }.filter { it.description.isNotBlank() }

            if (stepRows.isNotEmpty() && steps.isEmpty()) {
                binding.textError.isVisible = true
                binding.textError.text = getString(R.string.create_step_error)
                return
            }
            steps
        } else {
            emptyList()
        }

        val request = PostCreateRequest(
            postType = selectedPostType,
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

    private fun pickImage(target: ImageTarget) {
        pendingImageTarget = target
        galleryLauncher.launch("image/*")
    }

    private fun captureImage(target: ImageTarget) {
        pendingImageTarget = target
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun handleImageUri(uri: Uri) {
        val target = pendingImageTarget ?: return
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            pendingImageTarget = null
            if (bytes == null) {
                Toast.makeText(this@CreatePostActivity, R.string.create_upload_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val name = extractFileName(uri, mime)
            uploadImageBytes(target, bytes, mime, name)
        }
    }

    private fun handleCameraBitmap(target: ImageTarget, bitmap: Bitmap) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }
            uploadImageBytes(target, bytes, "image/jpeg", "camera_${System.currentTimeMillis()}.jpg")
        }
    }

    private fun uploadImageBytes(target: ImageTarget, bytes: ByteArray, mimeType: String, fileName: String) {
        binding.buttonSubmit.isEnabled = false
        Toast.makeText(this, R.string.create_uploading, Toast.LENGTH_SHORT).show()
        viewModel.uploadImage(
            type = when (target) {
                ImageTarget.Cover -> "cover"
                is ImageTarget.Step -> "step"
            },
            fileName = fileName,
            content = bytes,
            mimeType = mimeType
        ) { result ->
            binding.buttonSubmit.isEnabled = !viewModel.state.value.submitting
            result.onSuccess { url ->
                when (target) {
                    ImageTarget.Cover -> binding.inputCoverUrl.setText(url)
                    is ImageTarget.Step -> target.binding.inputStepImage.setText(url)
                }
                Toast.makeText(this, R.string.create_upload_success, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, R.string.create_upload_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractFileName(uri: Uri, mimeType: String): String {
        val nameFromCursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        if (!nameFromCursor.isNullOrBlank()) return nameFromCursor

        val segment = uri.lastPathSegment
        if (!segment.isNullOrBlank()) return segment

        val ext = mimeType.substringAfter('/', "bin")
        return "upload_${System.currentTimeMillis()}.$ext"
    }

    private sealed interface ImageTarget {
        data object Cover : ImageTarget
        data class Step(val binding: ItemStepRowBinding) : ImageTarget
    }

    companion object {
        const val EXTRA_AUTHOR_ID = "extra_author_id"
        private const val POST_TYPE_RECIPE = "recipe"
        private const val POST_TYPE_ARTICLE = "article"
        // Threshold to ensure ingredient amount is positive
        private const val MIN_POSITIVE_AMOUNT = 0.01
    }

    private fun resolveErrorMessage(error: String?): String? {
        return when (error) {
            null -> null
            CreatePostViewModel.GENERIC_ERROR_KEY -> getString(R.string.create_error_generic)
            else -> error
        }
    }
}
