package ru.zagrebin.culinaryblog.ui

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.MainActivity
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityCreatePostBinding
import ru.zagrebin.culinaryblog.databinding.ItemIngredientRowBinding
import ru.zagrebin.culinaryblog.databinding.ItemStepRowBinding
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostIngredientRequest
import ru.zagrebin.culinaryblog.model.PostUpdateRequest
import ru.zagrebin.culinaryblog.model.RecipeStepRequest
import ru.zagrebin.culinaryblog.model.STATUS_DRAFT
import ru.zagrebin.culinaryblog.model.TagItem
import ru.zagrebin.culinaryblog.util.resolveUrl
import ru.zagrebin.culinaryblog.util.applyTagStyle
import ru.zagrebin.culinaryblog.viewmodel.CreatePostViewModel
import java.text.DecimalFormat
import javax.inject.Inject
import com.google.android.material.chip.Chip

@AndroidEntryPoint
class CreatePostFragment : Fragment() {

    private var _binding: ActivityCreatePostBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreatePostViewModel by viewModels()
    @Inject lateinit var tokenStorage: TokenStorage

    private val ingredientRows = mutableListOf<ItemIngredientRowBinding>()
    private val ingredientSelection = mutableMapOf<ItemIngredientRowBinding, Long?>()
    private val stepRows = mutableListOf<ItemStepRowBinding>()
    private val selectedTags = mutableSetOf<Long>()
    private var lastShownDraftKey: Pair<Long, Long>? = null
    private var restoredDraftId: Long? = null
    private var editPostId: Long? = null
    private var draftId: Long? = null
    private var draftIdPendingRemoval: Long? = null

    private var tags: List<TagItem> = emptyList()
    private var ingredients: List<IngredientItem> = emptyList()
    private var selectedPostType: String = POST_TYPE_RECIPE
    private var pendingImageTarget: ImageTarget? = null
    private val amountFormatter = DecimalFormat("#.##")

    private val statusValues = listOf(STATUS_DRAFT, "published")
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
            Toast.makeText(requireContext(), R.string.create_upload_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (tokenStorage.getToken().isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.create_login_required), Toast.LENGTH_LONG).show()
            val handled = (activity as? Host)?.let { it.onCreateRequiresAuth(); true } ?: false
            if (!handled) {
                startActivity(Intent(requireContext(), AuthActivity::class.java))
                if (activity !is MainActivity) activity?.finish()
            } else if (activity !is MainActivity) {
                activity?.finish()
            }
            return
        }

        val passedAuthorId = arguments?.getLong(ARG_AUTHOR_ID) ?: viewModel.authorId
        viewModel.setAuthorId(passedAuthorId)
        draftId = arguments?.getLong(ARG_DRAFT_ID, INVALID_DRAFT_ID)?.takeIf { it != INVALID_DRAFT_ID }
        editPostId = arguments?.getLong(ARG_EDIT_POST_ID, INVALID_EDIT_ID)?.takeIf { it != INVALID_EDIT_ID }

        if (activity is MainActivity) {
            binding.createBottomNavigation.isVisible = false
        } else {
            setupBottomNavigation()
        }
        setupStatusSpinner()
        binding.statusSpinner.setSelection(statusValues.indexOf(STATUS_DRAFT), false)
        setupPostTypeSelector()
        setupClicks()
        updateDraftActionsVisibility()
        observeState()

        viewModel.loadTags()
        viewModel.loadIngredients()
        updateRecipeVisibility()
        restoreDraftIfNeeded()
        maybeLoadExistingPost()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        ingredientRows.clear()
        ingredientSelection.clear()
        stepRows.clear()
        selectedTags.clear()
        lastShownDraftKey = null
        restoredDraftId = null
        draftId = null
    }

    private fun setupStatusSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
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

    private fun updateDraftActionsVisibility() {
        binding.draftActionsRow.isVisible = draftId != null && editPostId == null
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
        binding.buttonDeleteDraft.setOnClickListener { deleteDraft() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

                    state.created?.let { created ->
                        draftIdPendingRemoval?.let { draftToRemove ->
                            draftIdPendingRemoval = null
                            if (draftId == draftToRemove) {
                                draftId = null
                                updateDraftActionsVisibility()
                            }
                            viewLifecycleOwner.lifecycleScope.launch {
                                viewModel.deleteDraft(draftToRemove)
                            }
                        }
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.create_success),
                            Toast.LENGTH_LONG
                        ).show()
                        val handled = (activity as? Host)?.let { host ->
                            host.onPostCreated(created)
                            true
                        } ?: false
                        if (!handled) {
                            startActivity(
                                Intent(requireContext(), PostDetailActivity::class.java).putExtra(
                                    PostDetailActivity.EXTRA_POST,
                                    created
                                )
                            )
                            if (activity !is MainActivity) {
                                activity?.finish()
                            }
                        }
                        viewModel.clearCreated()
                    }
                    state.updatedPostId?.let { updatedId ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.create_success),
                            Toast.LENGTH_LONG
                        ).show()
                        val handled = (activity as? Host)?.let { host ->
                            host.onPostUpdated(updatedId)
                            true
                        } ?: false
                        viewModel.clearUpdated()
                        if (!handled && activity !is MainActivity) {
                            activity?.finish()
                        }
                    }
                    state.draftSaved?.let { draft ->
                        val draftKey = draft.id to draft.updatedAt
                        if (lastShownDraftKey != draftKey) {
                            lastShownDraftKey = draftKey
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.create_draft_saved_offline),
                                Toast.LENGTH_LONG
                            ).show()
                        }
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
            val chip = Chip(requireContext())
            chip.text = tag.name
            chip.isCheckable = true
            chip.isChecked = selectedTags.contains(tag.id)
            chip.applyTagStyle(tag.color)
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedTags.add(tag.id) else selectedTags.remove(tag.id)
            }
            binding.tagsContainer.addView(chip)
        }
    }

    private fun renderIngredientAdapters() {
        val labels = listOf(getString(R.string.create_choose_ingredient)) + ingredients.map { it.name }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        ingredientRows.forEach { row ->
            row.spinnerIngredient.adapter = adapter
            val currentId = ingredientSelection[row]
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
                    ingredientSelection[row] = selected
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
    }

    private fun formatAmount(value: Double?): String = value?.let { amountFormatter.format(it) } ?: ""

    private fun addIngredientRow(prefill: PostIngredientRequest? = null, renderAdapters: Boolean = true) {
        val rowBinding = ItemIngredientRowBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
        rowBinding.buttonRemoveIngredient.setOnClickListener {
            binding.ingredientsContainer.removeView(rowBinding.root)
            ingredientRows.remove(rowBinding)
            ingredientSelection.remove(rowBinding)
        }
        rowBinding.inputAmount.setText(formatAmount(prefill?.quantityValue))
        rowBinding.inputUnit.setText(prefill?.unit ?: "")
        ingredientRows.add(rowBinding)
        binding.ingredientsContainer.addView(rowBinding.root)
        ingredientSelection[rowBinding] = prefill?.ingredientId
        if (renderAdapters) renderIngredientAdapters()
    }

    private fun addStepRow(prefill: RecipeStepRequest? = null) {
        val rowBinding = ItemStepRowBinding.inflate(layoutInflater, binding.stepsContainer, false)
        rowBinding.buttonRemoveStep.setOnClickListener {
            binding.stepsContainer.removeView(rowBinding.root)
            stepRows.remove(rowBinding)
        }
        rowBinding.buttonPickStepImage.setOnClickListener { pickImage(ImageTarget.Step(rowBinding)) }
        rowBinding.buttonCaptureStepImage.setOnClickListener { captureImage(ImageTarget.Step(rowBinding)) }
        rowBinding.inputStepDescription.setText(prefill?.description ?: "")
        rowBinding.inputStepImage.setText(prefill?.imageUrl ?: "")
        showStepPreview(rowBinding, prefill?.imageUrl)
        stepRows.add(rowBinding)
        binding.stepsContainer.addView(rowBinding.root)
    }

    private fun showCoverPreview(url: String?) {
        val safeUrl = resolveUrl(url?.takeIf { it.isNotBlank() })
        if (safeUrl == null) {
            binding.coverPreview.isVisible = false
            return
        }
        binding.coverPreview.isVisible = true
        binding.coverPreview.load(safeUrl) {
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.bg_image_placeholder)
            crossfade(true)
        }
    }

    private fun showStepPreview(rowBinding: ItemStepRowBinding, url: String?) {
        val safeUrl = resolveUrl(url?.takeIf { it.isNotBlank() })
        if (safeUrl == null) {
            rowBinding.stepImagePreview.isVisible = false
            return
        }
        rowBinding.stepImagePreview.isVisible = true
        rowBinding.stepImagePreview.load(safeUrl) {
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.bg_image_placeholder)
            crossfade(true)
        }
    }

    private fun updateRecipeVisibility() {
        val isRecipe = selectedPostType == POST_TYPE_RECIPE
        binding.recipeSection.isVisible = isRecipe
        if (isRecipe) {
            if (ingredientRows.isEmpty()) addIngredientRow()
            if (stepRows.isEmpty()) addStepRow()
        } else {
            ingredientRows.clear()
            ingredientSelection.clear()
            stepRows.clear()
            binding.ingredientsContainer.removeAllViews()
            binding.stepsContainer.removeAllViews()
        }
    }

    private fun restoreDraftIfNeeded() {
        if (editPostId != null) return
        val targetDraftId = draftId ?: return
        if (restoredDraftId == targetDraftId) return
        viewLifecycleOwner.lifecycleScope.launch {
            val draft = viewModel.getDraft(targetDraftId).getOrNull()
            if (draft != null) {
                restoredDraftId = targetDraftId
                applyDraft(draft)
            } else {
                Toast.makeText(requireContext(), R.string.create_draft_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFormData(
        authorId: Long,
        postType: String,
        status: String?,
        title: String?,
        excerpt: String?,
        content: String?,
        coverUrl: String?,
        cookingTimeMinutes: Int?,
        calories: Int?,
        tagIds: List<Long>,
        ingredients: List<PostIngredientRequest>,
        steps: List<RecipeStepRequest>
    ) {
        viewModel.setAuthorId(authorId)
        selectedPostType = postType
        binding.postTypeGroup.check(
            if (postType == POST_TYPE_ARTICLE) binding.radioArticle.id else binding.radioRecipe.id
        )
        updateRecipeVisibility()

        binding.inputTitle.setText(title ?: "")
        binding.inputExcerpt.setText(excerpt ?: "")
        binding.inputContent.setText(content ?: "")
        binding.inputCoverUrl.setText(coverUrl.orEmpty())
        showCoverPreview(coverUrl)
        binding.inputCookingTime.setText(cookingTimeMinutes?.toString().orEmpty())
        binding.inputCalories.setText(calories?.toString().orEmpty())

        val statusIndex = statusValues.indexOf(status ?: STATUS_DRAFT).takeIf { it >= 0 } ?: 0
        binding.statusSpinner.setSelection(statusIndex, false)

        selectedTags.clear()
        selectedTags.addAll(tagIds)
        renderTags()

        ingredientRows.clear()
        ingredientSelection.clear()
        binding.ingredientsContainer.removeAllViews()
        ingredients.forEach { addIngredientRow(it, renderAdapters = false) }
        renderIngredientAdapters()

        stepRows.clear()
        binding.stepsContainer.removeAllViews()
        steps.forEach { addStepRow(it) }
        updateRecipeVisibility()
    }

    private fun applyRequest(request: PostCreateRequest) {
        applyFormData(
            authorId = request.authorId,
            postType = request.postType,
            status = request.status,
            title = request.title,
            excerpt = request.excerpt,
            content = request.content,
            coverUrl = request.coverUrl,
            cookingTimeMinutes = request.cookingTimeMinutes,
            calories = request.calories,
            tagIds = request.tagIds,
            ingredients = request.ingredients,
            steps = request.steps
        )
    }

    private fun applyDraft(draft: PostDraft) {
        applyRequest(draft.request)
    }

    private fun applyPost(post: PostFull) {
        val ingredientRequests = post.ingredients.map {
            PostIngredientRequest(
                ingredientId = it.ingredientId,
                quantityValue = it.quantityValue,
                unit = it.unit
            )
        }
        val stepRequests = post.steps.sortedBy { it.order }.map {
            RecipeStepRequest(
                order = it.order,
                description = it.description,
                imageUrl = it.imageUrl
            )
        }
        applyFormData(
            authorId = post.author?.id ?: viewModel.authorId,
            postType = post.postType ?: POST_TYPE_RECIPE,
            status = post.status ?: STATUS_DRAFT,
            title = post.title,
            excerpt = post.excerpt,
            content = post.content,
            coverUrl = post.coverUrl,
            cookingTimeMinutes = post.cookingTimeMinutes,
            calories = post.calories,
            tagIds = post.tags.map { it.id },
            ingredients = ingredientRequests,
            steps = stepRequests
        )
    }

    private fun maybeLoadExistingPost() {
        val postId = editPostId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressSubmit.isVisible = true
            val result = viewModel.getPost(postId)
            binding.progressSubmit.isVisible = false
            if (result.isSuccess) {
                result.getOrNull()?.let { applyPost(it) }
            } else {
                Toast.makeText(requireContext(), R.string.post_edit_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submit() {
        val isDraftMode = draftId != null && editPostId == null
        val selectedStatus = statusValues.getOrNull(binding.statusSpinner.selectedItemPosition) ?: STATUS_DRAFT
        if (isDraftMode && selectedStatus == STATUS_DRAFT) {
            saveDraftChanges()
            return
        }

        val createRequest = buildCreateRequest() ?: return
        val updateRequest = createRequest.toUpdateRequest()

        binding.textError.isVisible = false
        val targetPostId = editPostId
        if (targetPostId != null) {
            viewModel.updatePost(targetPostId, updateRequest)
        } else {
            draftIdPendingRemoval = if (isDraftMode) draftId else null
            viewModel.createPost(createRequest)
        }
    }

    private fun saveDraftChanges() {
        val request = buildCreateRequest(forceDraftStatus = true) ?: run {
            val message = binding.textError.text?.takeIf { binding.textError.isVisible && it.isNotBlank() }
                ?: getString(R.string.draft_save_error)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            return
        }
        val targetDraftId = draftId ?: run {
            Toast.makeText(requireContext(), R.string.draft_save_error, Toast.LENGTH_SHORT).show()
            return
        }
        draftIdPendingRemoval = null
        binding.progressSubmit.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.saveDraft(request, targetDraftId)
            binding.progressSubmit.isVisible = false
            if (result.isSuccess) {
                Toast.makeText(requireContext(), R.string.create_draft_saved_offline, Toast.LENGTH_LONG).show()
                activity?.finish()
            } else {
                binding.textError.isVisible = true
                binding.textError.text = result.exceptionOrNull()?.message ?: getString(R.string.create_error_generic)
            }
        }
    }

    private fun deleteDraft() {
        val targetDraftId = draftId ?: run {
            Toast.makeText(requireContext(), R.string.draft_delete_error, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressSubmit.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.deleteDraft(targetDraftId)
            binding.progressSubmit.isVisible = false
            if (result.isSuccess) {
                Toast.makeText(requireContext(), R.string.draft_delete_success, Toast.LENGTH_SHORT).show()
                activity?.finish()
            } else {
                binding.textError.isVisible = true
                binding.textError.text = result.exceptionOrNull()?.message ?: getString(R.string.draft_delete_error)
            }
        }
    }

    private fun buildCreateRequest(forceDraftStatus: Boolean = false): PostCreateRequest? {
        val title = binding.inputTitle.text.toString().trim()
        val excerpt = binding.inputExcerpt.text.toString().trim()
        val content = binding.inputContent.text.toString().trim()
        val status = if (forceDraftStatus) STATUS_DRAFT else statusValues.getOrNull(binding.statusSpinner.selectedItemPosition) ?: STATUS_DRAFT
        val isRecipe = selectedPostType == POST_TYPE_RECIPE

        val ingredientRequests = if (isRecipe) {
            val list = mutableListOf<PostIngredientRequest>()
            var invalidAmount = false
            ingredientRows.forEach { row ->
                val selectedId = ingredientSelection[row]
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

            if (status != STATUS_DRAFT && (invalidAmount || (ingredientRows.isNotEmpty() && list.isEmpty()))) {
                binding.textError.isVisible = true
                binding.textError.text = getString(R.string.create_ingredient_error)
                return null
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

            if (status != STATUS_DRAFT && stepRows.isNotEmpty() && steps.isEmpty()) {
                binding.textError.isVisible = true
                binding.textError.text = getString(R.string.create_step_error)
                return null
            }
            steps
        } else {
            emptyList()
        }

        val createRequest = PostCreateRequest(
            postType = selectedPostType,
            status = status,
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
        return createRequest
    }

    private fun PostCreateRequest.toUpdateRequest(): PostUpdateRequest = PostUpdateRequest(
        postType = postType,
        status = status,
        title = title,
        excerpt = excerpt,
        content = content,
        coverUrl = coverUrl,
        cookingTimeMinutes = cookingTimeMinutes,
        calories = calories,
        tagIds = tagIds,
        ingredients = ingredients,
        steps = steps
    )

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
        viewLifecycleOwner.lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            val mime = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
            pendingImageTarget = null
            if (bytes == null) {
                Toast.makeText(requireContext(), R.string.create_upload_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val name = extractFileName(uri, mime)
            uploadImageBytes(target, bytes, mime, name)
        }
    }

    private fun handleCameraBitmap(target: ImageTarget, bitmap: Bitmap) {
        viewLifecycleOwner.lifecycleScope.launch {
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
        Toast.makeText(requireContext(), R.string.create_uploading, Toast.LENGTH_SHORT).show()
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
                    ImageTarget.Cover -> {
                        binding.inputCoverUrl.setText(url)
                        showCoverPreview(url)
                    }
                    is ImageTarget.Step -> {
                        target.binding.inputStepImage.setText(url)
                        showStepPreview(target.binding, url)
                    }
                }
                Toast.makeText(requireContext(), R.string.create_upload_success, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(requireContext(), R.string.create_upload_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractFileName(uri: Uri, mimeType: String): String {
        val nameFromCursor = requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
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

    private fun resolveErrorMessage(error: String?): String? {
        return when (error) {
            null -> null
            CreatePostViewModel.GENERIC_ERROR_KEY -> getString(R.string.create_error_generic)
            else -> error
        }
    }

    private fun setupBottomNavigation() {
        binding.createBottomNavigation.selectedItemId = R.id.menu_create
        binding.createBottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_recipes -> {
                    startActivity(
                        Intent(requireContext(), MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_RECIPES)
                    )
                    activity?.finish()
                    true
                }

                R.id.menu_articles -> {
                    startActivity(
                        Intent(requireContext(), MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_ARTICLES)
                    )
                    activity?.finish()
                    true
                }

                R.id.menu_profile -> {
                    startActivity(Intent(requireContext(), ProfileActivity::class.java))
                    activity?.finish()
                    true
                }

                R.id.menu_create -> true
                else -> false
            }
        }
    }

    interface Host {
        fun onPostCreated(post: PostCard)
        fun onPostUpdated(postId: Long)
        fun onCreateRequiresAuth()
    }

    companion object {
        private const val POST_TYPE_RECIPE = "recipe"
        private const val POST_TYPE_ARTICLE = "article"
        private const val MIN_POSITIVE_AMOUNT = 0.01
        private const val INVALID_DRAFT_ID = -1L
        private const val INVALID_EDIT_ID = -1L
        private const val ARG_AUTHOR_ID = "arg_author_id"
        private const val ARG_DRAFT_ID = "arg_draft_id"
        private const val ARG_EDIT_POST_ID = "arg_edit_post_id"

        fun newInstance(authorId: Long? = null, draftId: Long? = null, editPostId: Long? = null): CreatePostFragment {
            return CreatePostFragment().apply {
                if (authorId != null || draftId != null || editPostId != null) {
                    arguments = Bundle().apply {
                        authorId?.let { putLong(ARG_AUTHOR_ID, it) }
                        draftId?.let { putLong(ARG_DRAFT_ID, it) }
                        editPostId?.let { putLong(ARG_EDIT_POST_ID, it) }
                    }
                }
            }
        }
    }
}
