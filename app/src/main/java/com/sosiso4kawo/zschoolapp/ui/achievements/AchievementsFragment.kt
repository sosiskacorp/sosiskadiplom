package com.sosiso4kawo.zschoolapp.ui.achievements

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.api.AchievementsService
import com.sosiso4kawo.zschoolapp.data.model.Achievement
import com.sosiso4kawo.zschoolapp.databinding.FragmentAchievementsBinding
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import retrofit2.Retrofit

class AchievementsFragment : Fragment() {

    companion object {
        private const val TAG = "AchievementsFragment"
    }

    private var _binding: FragmentAchievementsBinding? = null
    private val binding get() = _binding!!

    private val retrofit: Retrofit by inject()
    private val sessionManager: SessionManager by inject()

    private val achievementsService: AchievementsService by lazy {
        retrofit.create(AchievementsService::class.java)
    }

    private val token: String by lazy {
        "Bearer " + sessionManager.getAccessToken()
    }

    private val userUuid: String by lazy {
        sessionManager.getUserUuid() ?: ""
    }

    private lateinit var achievementsAdapter: AchievementsAdapter

    private lateinit var tvProgressLabel: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating fragment view")
        _binding = FragmentAchievementsBinding.inflate(inflater, container, false)

        binding.header.apply {
            Log.d(TAG, "Configuring header component")
            setHeaderBackgroundColor(R.color.header_achievements)
            setTitle("Достижения")
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Initializing views and components")

        tvProgressLabel = binding.progressSection.findViewById(R.id.tvProgressLabel)
        tvProgressPercent = binding.progressSection.findViewById(R.id.tvProgressPercent)
        progressBar = binding.progressSection.findViewById(R.id.progressBar)

        Log.d(TAG, "Setting up progress section UI components")
        tvProgressLabel.text = "Общий прогресс"
        tvProgressPercent.setTextColor(Color.parseColor("#F97316"))
        progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#F97316"))

        Log.d(TAG, "Configuring RecyclerView with GridLayoutManager")
        achievementsAdapter = AchievementsAdapter()
        binding.recyclerViewAchievements.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerViewAchievements.adapter = achievementsAdapter

        Log.d(TAG, "Initiating achievements data loading")
        loadAchievements()
    }

    private fun loadAchievements() {
        lifecycleScope.launch {
            Log.d(TAG, "loadAchievements: Starting coroutine for data loading")
            try {
                Log.d(TAG, "Making API call to get all achievements")
                val allResponse = achievementsService.getAchievements(token)
                Log.d(TAG, "All achievements API response code: ${allResponse.code()}")
                Log.d(TAG, "All achievements API response headers: ${allResponse.headers()}")
                Log.d(TAG, "All achievements API raw response: ${allResponse.body()}")
                
                Log.d(TAG, "Making API call to get user achievements for UUID: $userUuid")
                val userResponse = achievementsService.getUserAchievements(userUuid, token)
                Log.d(TAG, "User achievements API response code: ${userResponse.code()}")
                Log.d(TAG, "User achievements API response headers: ${userResponse.headers()}")
                Log.d(TAG, "User achievements API raw response: ${userResponse.body()}")

                if (allResponse.isSuccessful && userResponse.isSuccessful) {
                    val allAchievements = allResponse.body()?.achievements ?: emptyList()
                    val userAchievements = userResponse.body()?.achievements ?: emptyList()

                    Log.d(TAG, "Received ${allAchievements.size} all achievements from API")
                    Log.d(TAG, "Received ${userAchievements.size} user achievements from API")

                    Log.d(TAG, "========== STARTING ACHIEVEMENT MERGING PROCESS ==========")
                    Log.d(TAG, "Merging ${allAchievements.size} total achievements with ${userAchievements.size} user achievements")
                    
                    val mergedAchievements = allAchievements.map { achievement ->
                        val userAch = userAchievements.find { it.id == achievement.id }
                        Log.d(TAG, "\n----- Processing Achievement ID: ${achievement.id} -----")
                        Log.d(TAG, "Title: ${achievement.title}")
                        Log.d(TAG, "Description: ${achievement.description}")
                        Log.d(TAG, "Secret: ${achievement.secret}")
                        Log.d(TAG, "Created at: ${achievement.created_at}")

                        if (userAch != null) {
                            val condition = achievement.condition
                            val currentCount = userAch.current_count ?: 0
                            val requiredCount = condition?.count ?: 0
                            val isCompleted = currentCount >= requiredCount
                            val actionType = condition?.action ?: "unknown"

                            Log.d(TAG, "User data found: YES")
                            Log.d(TAG, "Action type: $actionType")
                            Log.d(TAG, "Current progress: $currentCount/$requiredCount (${(currentCount * 100 / requiredCount.coerceAtLeast(1))}%)")
                            Log.d(TAG, "Completion status: ${if (isCompleted) "COMPLETED" else "IN PROGRESS"}")
                            Log.d(TAG, "Achieved at: ${achievement.achieved_at ?: "Not yet achieved"}")

                            achievement.copy(achieved = isCompleted, condition = condition)
                        } else {
                            Log.d(TAG, "User data found: NO")
                            Log.d(TAG, "Status: NOT STARTED (no user progress data)")
                            Log.d(TAG, "Default values will be used")
                            
                            achievement.copy(achieved = false)
                        }
                    }

                    Log.d(TAG, "Merged achievements count: ${mergedAchievements.size}")
                    achievementsAdapter.submitList(mergedAchievements)
                    updateProgress(mergedAchievements)
                } else {
                    Log.e(TAG, "API request failed - All response code: ${allResponse.code()}, User response code: ${userResponse.code()}")
                    Toast.makeText(context, "Ошибка загрузки достижений", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception occurred during achievements loading", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgress(achievements: List<Achievement>) {
        val total = achievements.size
        val achievedCount = achievements.count { it.achieved == true }
        val percent = if (total > 0) (achievedCount * 100 / total) else 0

        Log.d(TAG, "Updating progress UI - Achieved: $achievedCount/$total ($percent%)")
        progressBar.progress = percent
        tvProgressPercent.text = "$percent%"
    }



    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Clearing binding reference")
        _binding = null
    }
}