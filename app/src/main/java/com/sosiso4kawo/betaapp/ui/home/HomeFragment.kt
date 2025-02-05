package com.sosiso4kawo.betaapp.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentHomeBinding
import com.sosiso4kawo.betaapp.ui.custom.LevelButtonView

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var homeViewModel: HomeViewModel

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
        
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // Initialize streak circle with a default value
        binding.streakCircle.setStreak(0)
        
        // Set up learning button and levels
        setupLearningButton()
        setupLevels()
        
        // Observe streak updates from view model
        homeViewModel.text.observe(viewLifecycleOwner) {
            binding.streakCircle.setStreak(it.toString().toIntOrNull() ?: 0)
        }
        
        return root
    }

    private fun setupLearningButton() {
        // TODO: Replace with actual progress check
        val hasStartedLearning = false
        binding.startLearningButton.text = getString(
            if (hasStartedLearning) R.string.continue_learning
            else R.string.start_learning
        )
    }

    private fun setupLevels() {
        // TODO: Replace with actual level data
        val totalLevels = 15
        val completedLevels = 0

        for (i in 1..totalLevels) {
            val levelButton = LevelButtonView(requireContext()).apply {
                val status = when {
                    i <= completedLevels -> LevelButtonView.LevelStatus.COMPLETED
                    i == completedLevels + 1 -> LevelButtonView.LevelStatus.AVAILABLE
                    else -> LevelButtonView.LevelStatus.LOCKED
                }
                setLevel(i, status)
                setOnClickListener { handleLevelClick(i, status) }
            }
            binding.levelsGrid.addView(levelButton)
        }
    }

    private fun handleLevelClick(levelNumber: Int, status: LevelButtonView.LevelStatus) {
        when (status) {
            LevelButtonView.LevelStatus.LOCKED -> showLevelLockedDialog()
            LevelButtonView.LevelStatus.AVAILABLE,
            LevelButtonView.LevelStatus.COMPLETED -> {
                // TODO: Navigate to level
            }
        }
    }

    private fun showLevelLockedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.level_locked_title)
            .setMessage(R.string.level_locked_message)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}