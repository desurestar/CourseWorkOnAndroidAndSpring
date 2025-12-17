package ru.zagrebin.culinaryblog

import android.os.Bundle
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
import ru.zagrebin.culinaryblog.databinding.ActivityAuthBinding
import ru.zagrebin.culinaryblog.viewmodel.AuthViewModel
import ru.zagrebin.culinaryblog.viewmodel.UiState

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupActions()
        observeState()
    }

    private fun setupTabs() {
        binding.authTabs.addTab(binding.authTabs.newTab().setText(R.string.auth_tab_login))
        binding.authTabs.addTab(binding.authTabs.newTab().setText(R.string.auth_tab_register))
        binding.authTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val login = tab.position == 0
                binding.loginForm.isVisible = login
                binding.registerForm.isVisible = !login
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.authTabs.getTabAt(0)?.select()
    }

    private fun setupActions() {
        binding.buttonLogin.setOnClickListener {
            viewModel.loginUsername.value = binding.inputLoginUsername.text.toString()
            viewModel.loginPassword.value = binding.inputLoginPassword.text.toString()
            viewModel.login()
        }
        binding.buttonRegister.setOnClickListener {
            viewModel.regUsername.value = binding.inputRegisterUsername.text.toString()
            viewModel.regEmail.value = binding.inputRegisterEmail.text.toString()
            viewModel.regPassword.value = binding.inputRegisterPassword.text.toString()
            viewModel.regConfirmPassword.value = binding.inputRegisterConfirm.text.toString()
            viewModel.register()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.loginState.collect { renderLogin(it) } }
                launch { viewModel.registerState.collect { renderRegister(it) } }
            }
        }
    }

    private fun renderLogin(state: UiState) {
        when (state) {
            is UiState.Idle -> {
                binding.progressLogin.isVisible = false
                binding.loginError.isVisible = false
            }
            is UiState.Loading -> {
                binding.progressLogin.isVisible = true
                binding.loginError.isVisible = false
            }
            is UiState.Success -> {
                binding.progressLogin.isVisible = false
                binding.loginError.isVisible = false
                binding.authReadyText.isVisible = true
                setResult(RESULT_OK)
                finish()
            }
            is UiState.Error -> {
                binding.progressLogin.isVisible = false
                binding.loginError.isVisible = true
                binding.loginError.text = state.message
            }
        }
    }

    private fun renderRegister(state: UiState) {
        when (state) {
            is UiState.Idle -> {
                binding.progressRegister.isVisible = false
                binding.registerError.isVisible = false
            }
            is UiState.Loading -> {
                binding.progressRegister.isVisible = true
                binding.registerError.isVisible = false
            }
            is UiState.Success -> {
                binding.progressRegister.isVisible = false
                binding.registerError.isVisible = false
                binding.authReadyText.isVisible = true
                setResult(RESULT_OK)
                finish()
            }
            is UiState.Error -> {
                binding.progressRegister.isVisible = false
                binding.registerError.isVisible = true
                binding.registerError.text = state.message
            }
        }
    }
}
