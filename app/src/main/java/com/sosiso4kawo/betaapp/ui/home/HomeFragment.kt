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
import android.util.Log

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var homeViewModel: HomeViewModel
    private var levelLockedDialog: AlertDialog? = null

    @SuppressLint("ResourceAsColor")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return _binding?.root ?: run {
            _binding = FragmentHomeBinding.inflate(inflater, container, false)
            setupInitialViews()
            binding.root
        }
    }

    private fun setupInitialViews() {
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.notification_button).visibility = View.VISIBLE
            setOnNotificationClickListener {}
        }
        
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding.streakCircle.setStreak(0)
        setupLearningButton()
        setupLevels()
        initializeLevelLockedDialog()
        
        homeViewModel.text.observe(viewLifecycleOwner) {
            binding.streakCircle.setStreak(it.toString().toIntOrNull() ?: 0)
        }
    }

    private fun setupLearningButton() {
        val hasStartedLearning = false
        binding.startLearningButton.apply {
            text = getString(
                if (hasStartedLearning) R.string.continue_learning
                else R.string.start_learning
            )
            setOnClickListener {
                Log.d("ButtonClick", "Learning button clicked. Status: ${if (hasStartedLearning) "Continue" else "Start"}")
            }
        }
    }

    private fun setupLevels() {
        val totalLevels = 15
        val completedLevels = 1
        val recycledButton = LevelButtonView(requireContext())

        binding.levelsGrid.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            removeAllViews()
        }

        for (i in 1..totalLevels) {
            val levelButton = if (i == 1) recycledButton else LevelButtonView(requireContext()).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            levelButton.apply {
                val status = when {
                    i <= completedLevels -> LevelButtonView.LevelStatus.COMPLETED
                    i == completedLevels + 1 -> LevelButtonView.LevelStatus.AVAILABLE
                    else -> LevelButtonView.LevelStatus.LOCKED
                }
                setLevel(i, status)
                setOnClickListener { view ->
                    view.isEnabled = false
                    handleLevelClick(i, status)
                    view.postDelayed({ view.isEnabled = true }, 300)
                }
            }
            binding.levelsGrid.addView(levelButton)
        }
    }

    private fun initializeLevelLockedDialog() {
        levelLockedDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.level_locked_title)
            .setMessage(R.string.level_locked_message)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create()
    }

    private fun handleLevelClick(levelNumber: Int, status: LevelButtonView.LevelStatus) {
        when (status) {
            LevelButtonView.LevelStatus.LOCKED -> {
                levelLockedDialog?.show()
            }
            LevelButtonView.LevelStatus.AVAILABLE,
            LevelButtonView.LevelStatus.COMPLETED -> {
                // TODO: Navigate to level
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        levelLockedDialog?.dismiss()
        levelLockedDialog = null
        _binding = null
    }
}