package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.databinding.ActivityAdminBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminIngredientBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminPostBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminTagBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminUserBinding
import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.AdminUser
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.TagItem
import ru.zagrebin.culinaryblog.data.repository.AdminRepository
import ru.zagrebin.culinaryblog.R

@AndroidEntryPoint
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    @Inject lateinit var adminRepository: AdminRepository

    private val postStatuses = listOf("draft", "published", "archived")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        setupActions()

        loadPosts()
        loadIngredients()
        loadTags()
        loadUsers()
    }

    private fun setupToolbar() {
        binding.buttonAdminBack.setOnClickListener { finish() }
        binding.adminToolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.adminTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val index = tab?.position ?: 0
                binding.adminContent.displayedChild = index
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupActions() {
        binding.buttonRefreshPosts.setOnClickListener { loadPosts() }
        binding.buttonAddIngredient.setOnClickListener { addIngredient() }
        binding.buttonAddTag.setOnClickListener { addTag() }
        binding.buttonRefreshUsers.setOnClickListener { loadUsers() }
    }

    private fun addIngredient() {
        val name = binding.inputIngredientName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) return
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.createIngredient(name)
            showLoading(false)
            if (result.isSuccess) {
                binding.inputIngredientName.text?.clear()
                loadIngredients()
            } else {
                showError(result.exceptionOrNull()?.message)
            }
        }
    }

    private fun addTag() {
        val name = binding.inputTagName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) return
        val color = binding.inputTagColor.text?.toString()?.trim().orEmpty().ifBlank { null }
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.createTag(name, color)
            showLoading(false)
            if (result.isSuccess) {
                binding.inputTagName.text?.clear()
                loadTags()
            } else {
                showError(result.exceptionOrNull()?.message)
            }
        }
    }

    private fun loadPosts() {
        val search = binding.inputPostSearch.text?.toString()?.trim().orEmpty().ifBlank { null }
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.getPosts(search)
            showLoading(false)
            result.onSuccess { renderPosts(it) }.onFailure { showError(it.message) }
        }
    }

    private fun loadIngredients() {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.getIngredients()
            showLoading(false)
            result.onSuccess { renderIngredients(it) }.onFailure { showError(it.message) }
        }
    }

    private fun loadTags() {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.getTags()
            showLoading(false)
            result.onSuccess { renderTags(it) }.onFailure { showError(it.message) }
        }
    }

    private fun loadUsers() {
        val search = binding.inputUserSearch.text?.toString()?.trim().orEmpty().ifBlank { null }
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.getUsers(search)
            showLoading(false)
            result.onSuccess { renderUsers(it) }.onFailure { showError(it.message) }
        }
    }

    private fun renderPosts(items: List<AdminPost>) {
        showError(null)
        binding.postsList.removeAllViews()
        binding.postsEmpty.isVisible = items.isEmpty()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, postStatuses).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        items.forEach { post ->
            val itemBinding = ItemAdminPostBinding.inflate(layoutInflater, binding.postsList, false)
            itemBinding.adminPostTitle.text = post.title?.ifBlank { getString(R.string.card_title_placeholder) }
            val meta = getString(
                R.string.admin_post_meta,
                post.id,
                post.postType ?: "-",
                post.authorUsername ?: getString(R.string.author_unknown)
            )
            itemBinding.adminPostMeta.text = meta
            itemBinding.adminPostStatus.adapter = adapter
            val statusIndex = postStatuses.indexOfFirst { it.equals(post.status, ignoreCase = true) }
            if (statusIndex >= 0) {
                itemBinding.adminPostStatus.setSelection(statusIndex)
            }
            itemBinding.adminPostStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                private var initial = true
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (initial) {
                        initial = false
                        return
                    }
                    val newStatus = postStatuses[position]
                    updatePostStatus(post, newStatus)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            itemBinding.buttonDeletePost.setOnClickListener { deletePost(post.id) }
            itemBinding.buttonOpenPost.setOnClickListener { openPost(post) }
            binding.postsList.addView(itemBinding.root)
        }
    }

    private fun renderIngredients(items: List<IngredientItem>) {
        showError(null)
        binding.ingredientsList.removeAllViews()
        binding.ingredientsEmpty.isVisible = items.isEmpty()
        items.forEach { ingredient ->
            val itemBinding = ItemAdminIngredientBinding.inflate(layoutInflater, binding.ingredientsList, false)
            itemBinding.adminIngredientName.text = ingredient.name
            itemBinding.buttonDeleteIngredient.setOnClickListener { deleteIngredient(ingredient.id) }
            binding.ingredientsList.addView(itemBinding.root)
        }
    }

    private fun renderTags(items: List<TagItem>) {
        showError(null)
        binding.tagsList.removeAllViews()
        binding.tagsEmpty.isVisible = items.isEmpty()
        items.forEach { tag ->
            val itemBinding = ItemAdminTagBinding.inflate(layoutInflater, binding.tagsList, false)
            itemBinding.adminTagName.text = tag.name
            val colorValue = tag.color?.takeIf { it.isNotBlank() } ?: "#DDDDDD"
            try {
                itemBinding.adminTagColor.setBackgroundColor(android.graphics.Color.parseColor(colorValue))
            } catch (_: IllegalArgumentException) {
                itemBinding.adminTagColor.setBackgroundColor(android.graphics.Color.LTGRAY)
            }
            itemBinding.buttonDeleteTag.setOnClickListener { deleteTag(tag.id) }
            binding.tagsList.addView(itemBinding.root)
        }
    }

    private fun renderUsers(items: List<AdminUser>) {
        showError(null)
        binding.usersList.removeAllViews()
        binding.usersEmpty.isVisible = items.isEmpty()
        items.forEach { user ->
            val itemBinding = ItemAdminUserBinding.inflate(layoutInflater, binding.usersList, false)
            val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.username ?: getString(R.string.profile_user_stub)
            itemBinding.adminUserName.text = "$name (#${user.id})"
            itemBinding.adminUserEmail.text = user.email ?: ""
            itemBinding.adminUserIsAdmin.setOnCheckedChangeListener(null)
            itemBinding.adminUserIsAdmin.isChecked = user.isAdmin
            itemBinding.adminUserIsAdmin.setOnCheckedChangeListener { _, isChecked ->
                updateUserRole(user, if (isChecked) "admin" else "user")
            }
            itemBinding.buttonDeleteUser.setOnClickListener { deleteUser(user.id) }
            binding.usersList.addView(itemBinding.root)
        }
    }

    private fun updatePostStatus(post: AdminPost, status: String) {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.updatePostStatus(post.id, status)
            showLoading(false)
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message)
                loadPosts()
            }
        }
    }

    private fun deletePost(id: Long) {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.deletePost(id)
            showLoading(false)
            if (result.isSuccess) {
                loadPosts()
            } else {
                showError(result.exceptionOrNull()?.message)
            }
        }
    }

    private fun deleteIngredient(id: Long) {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.deleteIngredient(id)
            showLoading(false)
            if (result.isSuccess) loadIngredients() else showError(result.exceptionOrNull()?.message)
        }
    }

    private fun deleteTag(id: Long) {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.deleteTag(id)
            showLoading(false)
            if (result.isSuccess) loadTags() else showError(result.exceptionOrNull()?.message)
        }
    }

    private fun updateUserRole(user: AdminUser, role: String) {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.updateUserRole(user.id, role)
            showLoading(false)
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message)
                loadUsers()
            }
        }
    }

    private fun deleteUser(id: Long) {
        showLoading(true)
        lifecycleScope.launch {
            val result = adminRepository.deleteUser(id)
            showLoading(false)
            if (result.isSuccess) {
                loadUsers()
            } else {
                showError(result.exceptionOrNull()?.message)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.adminProgress.isVisible = show
    }

    private fun showError(message: String?) {
        if (message.isNullOrBlank()) {
            binding.adminError.isVisible = false
            return
        }
        binding.adminError.isVisible = true
        binding.adminError.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openPost(post: AdminPost) {
        if (post.id <= 0) {
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_SHORT).show()
            return
        }
        val card = PostCard(
            id = post.id,
            title = post.title ?: "",
            excerpt = "",
            coverUrl = null,
            authorId = null,
            postType = post.postType,
            likesCount = 0,
            cookingTimeMinutes = null,
            calories = null,
            authorName = post.authorUsername,
            publishedAt = post.createdAt,
            tags = emptySet(),
            viewsCount = 0L
        )
        val intent = Intent(this, PostDetailActivity::class.java).putExtra(PostDetailActivity.EXTRA_POST, card)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (binding.adminContent.displayedChild == 0) {
            loadPosts()
        }
    }
}
