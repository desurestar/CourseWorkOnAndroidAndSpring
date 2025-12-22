package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.databinding.FragmentAdminUsersBinding
import ru.zagrebin.culinaryblog.databinding.ItemAdminUserBinding
import ru.zagrebin.culinaryblog.viewmodel.AdminUsersViewModel

@AndroidEntryPoint
class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminUsersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSearchUsers.setOnClickListener {
            viewModel.load(binding.adminUserSearch.text?.toString()?.trim())
        }
        binding.buttonLoadMoreUsers.setOnClickListener { viewModel.loadMore() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }

        if (savedInstanceState == null) {
            viewModel.load()
        }
    }

    private fun render(state: ru.zagrebin.culinaryblog.viewmodel.AdminUsersState) {
        binding.adminUsersProgress.isVisible = state.isLoading || state.isAppending
        binding.adminUsersError.isVisible = state.error != null
        binding.adminUsersError.text = state.error ?: ""
        binding.buttonLoadMoreUsers.isVisible = state.nextPage != null

        val container = binding.adminUsersContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        state.items.forEach { user ->
            val itemBinding = ItemAdminUserBinding.inflate(inflater, container, false)
            itemBinding.adminUserName.text = user.displayName.ifBlank { user.username }
            itemBinding.adminUserEmail.text = user.email
            itemBinding.adminUserIsAdmin.setOnCheckedChangeListener(null)
            itemBinding.adminUserIsAdmin.isChecked = user.isAdmin
            itemBinding.adminUserIsAdmin.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                viewModel.toggleAdmin(user.id, checked)
            }
            itemBinding.buttonDeleteUser.setOnClickListener {
                viewModel.delete(user.id)
            }
            container.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
