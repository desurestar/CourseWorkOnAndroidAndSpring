package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import ru.zagrebin.culinaryblog.databinding.ActivityProfileBinding
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.ui.CreatePostActivity
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import ru.zagrebin.culinaryblog.viewmodel.PostsUiState
import javax.inject.Inject

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val postViewModel: PostViewModel by viewModels()
    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (tokenStorage.getToken().isNullOrBlank()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupActions()
        renderUserStub()
        observePosts()
    }

    private fun setupTabs() {
        val tabs = binding.profileTabs
        tabs.addTab(tabs.newTab().setText(R.string.profile_followers))
        tabs.addTab(tabs.newTab().setText(R.string.profile_following))
        tabs.addTab(tabs.newTab().setText(R.string.profile_posts))
        tabs.addTab(tabs.newTab().setText(R.string.profile_liked))
        tabs.addTab(tabs.newTab().setText(R.string.profile_drafts))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showSection(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        tabs.getTabAt(0)?.select()
    }

    private fun setupActions() {
        binding.buttonLogout.setOnClickListener {
            tokenStorage.clearToken()
            finish()
        }
        binding.buttonOpenDrafts.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
        renderFollowers(listOf("Алексей", "Мария", "Владимир"))
        renderFollowing(listOf("Иван", "Дарья"))
    }

    private fun observePosts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uiState.collect { renderPosts(it) }
            }
        }
    }

    private fun renderPosts(state: PostsUiState) {
        val posts = state.posts
        val liked = posts.filter { it.likesCount > 0 }
        val drafts = posts.take(1)
        renderPostList(binding.postsList, posts)
        renderPostList(binding.likedList, liked)
        renderPostList(binding.draftsList, drafts)
        binding.profileEmpty.isVisible = posts.isEmpty()
    }

    private fun renderPostList(container: LinearLayout, posts: List<PostCard>) {
        container.removeAllViews()
        if (posts.isEmpty()) {
            val stub = TextView(this)
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        posts.forEach { post ->
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false)
            view.findViewById<TextView>(android.R.id.text1).text = post.title.ifBlank { getString(R.string.card_title_placeholder) }
            view.findViewById<TextView>(android.R.id.text2).text = post.excerpt.ifBlank { getString(R.string.card_excerpt_placeholder) }
            view.setOnClickListener { openPost(post) }
            container.addView(view)
        }
    }

    private fun renderFollowers(items: List<String>) {
        renderSimpleList(binding.followersList, items)
    }

    private fun renderFollowing(items: List<String>) {
        renderSimpleList(binding.followingList, items)
    }

    private fun renderSimpleList(container: LinearLayout, items: List<String>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val stub = TextView(this)
            stub.text = getString(R.string.profile_empty)
            container.addView(stub)
            return
        }
        items.forEach { name ->
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, container, false)
            view.findViewById<TextView>(android.R.id.text1).text = name
            container.addView(view)
        }
    }

    private fun showSection(position: Int) {
        binding.sectionFollowers.isVisible = position == 0
        binding.sectionFollowing.isVisible = position == 1
        binding.sectionPosts.isVisible = position == 2
        binding.sectionLiked.isVisible = position == 3
        binding.sectionDrafts.isVisible = position == 4
    }

    private fun renderUserStub() {
        binding.profileName.text = getString(R.string.profile_user_stub)
        binding.profileEmail.text = getString(R.string.profile_email_stub)
        binding.profileAvatar.text = binding.profileName.text.firstOrNull()?.uppercase() ?: "U"
    }

    private fun openPost(post: PostCard) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra(PostDetailActivity.EXTRA_POST, post)
        startActivity(intent)
    }
}
