package ru.zagrebin.culinaryblog.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.AuthActivity
import ru.zagrebin.culinaryblog.MainActivity
import ru.zagrebin.culinaryblog.R

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity(), ProfileFragment.Host {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, ProfileFragment(), FRAGMENT_TAG)
            }
        }
    }

    override fun onProfileRequiresAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    override fun onProfileLogout() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.EXTRA_TAB_RECIPES)
        )
        finish()
    }

    override fun onProfileOpenDrafts(): Boolean {
        startActivity(Intent(this, CreatePostActivity::class.java))
        return true
    }

    companion object {
        private const val FRAGMENT_TAG = "profile_fragment"
    }
}
