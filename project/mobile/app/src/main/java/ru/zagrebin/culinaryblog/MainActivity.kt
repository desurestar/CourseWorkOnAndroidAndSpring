package ru.zagrebin.culinaryblog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import ru.zagrebin.culinaryblog.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            applySelection(item.itemId)
            true
        }
        binding.bottomNavigation.selectedItemId = R.id.menu_recipes
        applySelection(R.id.menu_recipes)
    }

    private fun applySelection(itemId: Int) {
        val (title, message) = when (itemId) {
            R.id.menu_recipes -> getString(R.string.nav_recipes) to getString(R.string.recipes_stub_message)
            R.id.menu_articles -> getString(R.string.nav_articles) to getString(R.string.articles_stub_message)
            R.id.menu_create -> getString(R.string.nav_create) to getString(R.string.create_stub_message)
            R.id.menu_messenger -> getString(R.string.nav_messenger) to getString(R.string.messenger_stub_message)
            R.id.menu_profile -> getString(R.string.nav_profile) to getString(R.string.profile_stub_message)
            else -> getString(R.string.view_stub_title) to getString(R.string.view_stub_message)
        }
        binding.titleText.text = title
        binding.contentText.text = message
    }
}