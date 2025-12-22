package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.ActivityAdminPanelBinding

@AndroidEntryPoint
class AdminPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPanelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        binding.buttonCloseAdmin.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        if (savedInstanceState == null) {
            binding.adminTabs.getTabAt(0)?.select()
            showFragment(AdminPostsFragment())
        }
    }

    private fun setupTabs() {
        val titles = listOf(
            getString(R.string.admin_tab_posts),
            getString(R.string.admin_tab_ingredients),
            getString(R.string.admin_tab_tags),
            getString(R.string.admin_tab_users)
        )
        titles.forEach { title ->
            binding.adminTabs.addTab(binding.adminTabs.newTab().setText(title))
        }
        binding.adminTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showFragment(AdminPostsFragment())
                    1 -> showFragment(AdminIngredientsFragment())
                    2 -> showFragment(AdminTagsFragment())
                    3 -> showFragment(AdminUsersFragment())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.adminContainer, fragment)
        }
    }
}
