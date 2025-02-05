package com.sosiso4kawo.betaapp.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    @SuppressLint("ResourceAsColor")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Set up the header with color but no title
        binding.header.apply {
            setBackgroundColor(R.color.header_home)
            binding.header.findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
            binding.header.findViewById<ImageButton>(R.id.notification_button).visibility = View.VISIBLE
            setOnNotificationClickListener {
                // Handle notification click
            }
        }
        
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        // Initialize streak circle with a default value
        binding.streakCircle.setStreak(0)
        
        // Observe streak updates from view model
        homeViewModel.text.observe(viewLifecycleOwner) {
            // Update streak circle when data changes
            binding.streakCircle.setStreak(it.toString().toIntOrNull() ?: 0)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}