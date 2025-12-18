package ru.zagrebin.culinaryblog.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.databinding.FragmentPublicProfileBinding

class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!

    private var userId: Long? = null
    private var displayName: String? = null
    private var subscribed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            userId = bundle.getLong(ARG_USER_ID).takeIf { it > 0 }
            displayName = bundle.getString(ARG_DISPLAY_NAME)
            subscribed = bundle.getBoolean(ARG_SUBSCRIBED, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublicProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_user_stub)
        binding.publicName.text = name
        binding.publicAvatar.text = name.firstOrNull()?.uppercase() ?: "U"
        binding.publicMeta.text = getString(R.string.nav_profile)
        renderSubscription()

        binding.buttonSubscribe.setOnClickListener {
            subscribed = !subscribed
            renderSubscription()
            Toast.makeText(requireContext(), R.string.profile_subscription_updated, Toast.LENGTH_SHORT).show()
        }
        binding.buttonClose.setOnClickListener {
            (activity as? Host)?.onPublicProfileClose() ?: activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderSubscription() {
        binding.buttonSubscribe.text =
            if (subscribed) getString(R.string.profile_unsubscribe) else getString(R.string.profile_subscribe)
    }

    interface Host {
        fun onPublicProfileClose()
    }

    companion object {
        private const val ARG_USER_ID = "arg_user_id"
        private const val ARG_DISPLAY_NAME = "arg_display_name"
        private const val ARG_SUBSCRIBED = "arg_subscribed"

        fun newInstance(
            userId: Long?,
            displayName: String?,
            subscribed: Boolean?
        ): PublicProfileFragment {
            val fragment = PublicProfileFragment()
            fragment.arguments = Bundle().apply {
                userId?.let { putLong(ARG_USER_ID, it) }
                putString(ARG_DISPLAY_NAME, displayName)
                subscribed?.let { putBoolean(ARG_SUBSCRIBED, it) }
            }
            return fragment
        }
    }
}
