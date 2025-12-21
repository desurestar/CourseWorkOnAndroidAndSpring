package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.model.PostCard

@AndroidEntryPoint
class CreatePostActivity : AppCompatActivity(), CreatePostFragment.Host {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        if (savedInstanceState == null) {
            val authorId = intent.getLongExtra(EXTRA_AUTHOR_ID, -1L).takeIf { it >= 0 }
            val draftId = intent.getLongExtra(EXTRA_DRAFT_ID, -1L).takeIf { it >= 0 }
            val editPostId = intent.getLongExtra(EXTRA_EDIT_POST_ID, -1L).takeIf { it >= 0 }
            supportFragmentManager.commit {
                replace(
                    R.id.fragmentContainer,
                    CreatePostFragment.newInstance(authorId, draftId, editPostId),
                    FRAGMENT_TAG
                )
            }
        }
    }

    override fun onPostCreated(post: PostCard) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CREATED_POST, post))
        startActivity(
            Intent(this, PostDetailActivity::class.java).putExtra(
                PostDetailActivity.EXTRA_POST,
                post
            )
        )
        finish()
    }

    override fun onPostUpdated(postId: Long) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_UPDATED_POST_ID, postId))
        finish()
    }

    override fun onCreateRequiresAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_AUTHOR_ID = "extra_author_id"
        const val EXTRA_CREATED_POST = "extra_created_post"
        const val EXTRA_DRAFT_ID = "extra_draft_id"
        const val EXTRA_EDIT_POST_ID = "extra_edit_post_id"
        const val EXTRA_RESULT_UPDATED_POST_ID = "extra_result_updated_post_id"
        private const val FRAGMENT_TAG = "create_post_fragment"
    }
}
