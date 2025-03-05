package com.sosiso4kawo.betaapp.ui.achievements

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.Gson
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.AchievementsService
import com.sosiso4kawo.betaapp.data.model.Achievement
import com.sosiso4kawo.betaapp.data.model.Condition
import com.sosiso4kawo.betaapp.databinding.FragmentAchievementsBinding
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import retrofit2.Retrofit

class AchievementsFragment : Fragment() {

    private var _binding: FragmentAchievementsBinding? = null
    private val binding get() = _binding!!

    // Получаем Retrofit и SessionManager через DI (Koin)
    private val retrofit: Retrofit by inject()
    private val sessionManager: SessionManager by inject()

    // Создаем сервис достижений через Retrofit
    private val achievementsService: AchievementsService by lazy {
        retrofit.create(AchievementsService::class.java)
    }
    // Токен для запросов
    private val token: String by lazy {
        "Bearer " + sessionManager.getAccessToken()
    }
    // Получаем идентификатор пользователя (должен быть сохранен в SessionManager)
    private val userUuid: String by lazy {
        sessionManager.getUserUuid() ?: ""
    }

    private lateinit var achievementsAdapter: AchievementsAdapter

    // Ссылки на вьюшки внутри блока прогресса
    private lateinit var tvProgressLabel: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAchievementsBinding.inflate(inflater, container, false)

        // Настройка header (CustomHeaderView)
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_achievements)
            setTitle("Достижения")
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем ссылки на дочерние элементы внутри progressSection
        tvProgressLabel = binding.progressSection.findViewById(R.id.tvProgressLabel)
        tvProgressPercent = binding.progressSection.findViewById(R.id.tvProgressPercent)
        progressBar = binding.progressSection.findViewById(R.id.progressBar)

        // Настройка блока прогресса
        tvProgressLabel.text = "Общий прогресс"
        tvProgressPercent.setTextColor(Color.parseColor("#F97316"))
        progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#F97316"))

        // Настройка RecyclerView: GridLayoutManager с 2 колонками
        achievementsAdapter = AchievementsAdapter()
        binding.recyclerViewAchievements.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerViewAchievements.adapter = achievementsAdapter

        loadAchievements()
    }

    private fun loadAchievements() {
        lifecycleScope.launch {
            try {
                // Выполняем оба запроса
                val allResponse = achievementsService.getAchievements(token)
                val userResponse = achievementsService.getUserAchievements(userUuid, token)

                if (allResponse.isSuccessful && userResponse.isSuccessful) {
                    val allAchievements = allResponse.body()?.achievements ?: emptyList()
                    val userAchievements = userResponse.body()?.achievements ?: emptyList()

                    // Объединяем списки: для каждого достижения из общего списка
                    // ищем соответствующее достижение в пользовательском списке по id.
                    val mergedAchievements = allAchievements.map { achievement ->
                        val userAch = userAchievements.find { it.id == achievement.id }
                        if (userAch != null) {
                            // Помечаем достижение как выполненное и обновляем поле condition (если необходимо)
                            achievement.copy(achieved = true, condition = userAch.condition)
                        } else {
                            achievement.copy(achieved = false)
                        }
                    }
                    achievementsAdapter.submitList(mergedAchievements)
                    updateProgress(mergedAchievements)
                } else {
                    Toast.makeText(context, "Ошибка загрузки достижений", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProgress(achievements: List<Achievement>) {
        val total = achievements.size
        // Вычисляем количество выполненных достижений
        val achievedCount = achievements.count { it.achieved == true }
        val percent = if (total > 0) (achievedCount * 100 / total) else 0
        progressBar.progress = percent
        tvProgressPercent.text = "$percent%"
    }

    private fun parseCondition(condition: String): Condition {
        return try {
            Gson().fromJson(condition, Condition::class.java)
        } catch (e: Exception) {
            Condition() // Значения по умолчанию при ошибке парсинга
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
