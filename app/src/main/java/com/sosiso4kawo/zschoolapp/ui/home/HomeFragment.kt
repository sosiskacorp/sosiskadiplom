package com.sosiso4kawo.zschoolapp.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.bundle.bundleOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.api.CoursesService
import com.sosiso4kawo.zschoolapp.data.api.UserService
import com.sosiso4kawo.zschoolapp.data.model.Course
import com.sosiso4kawo.zschoolapp.data.model.ProgressResponse
import com.sosiso4kawo.zschoolapp.databinding.FragmentHomeBinding
import com.sosiso4kawo.zschoolapp.ui.courses.CoursesAdapter
import com.sosiso4kawo.zschoolapp.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var coursesAdapter: CoursesAdapter
    private val coursesList = mutableListOf<Course>()

    // Сервисы
    private val coursesService: CoursesService by inject()
    private val userService: UserService by inject()
    private val sessionManager: SessionManager by inject()
    private var progressMap: Map<String, Int> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupInitialViews()
        loadStreak()
        loadCoursesWithProgress()
        return binding.root
    }

    private fun setupInitialViews() {
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
        }
        // Изначально выставляем стрик равным 0 до загрузки данных
        binding.streakCircle.setStreak(0)
        setupLearningButton()
        setupCoursesSection()
    }

    private fun setupLearningButton() {
        val hasStartedLearning = false
        binding.startLearningButton.apply {
            text = getString(
                if (hasStartedLearning) R.string.continue_learning
                else R.string.start_learning
            )
            setOnClickListener {
                Log.d("HomeFragment", "Learning button clicked. Status: ${if (hasStartedLearning) "Continue" else "Start"}")
            }
        }
    }

    private fun setupCoursesSection() {
        binding.rvCourses.apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    LinearLayoutManager.VERTICAL
                ).apply {
                    val divider = ContextCompat.getDrawable(requireContext(), R.drawable.divider)!!
                    setDrawable(divider)
                }
            )
        }

        coursesAdapter = CoursesAdapter(coursesList, emptyMap()) { course ->
            showCourseDialog(course)
        }
        binding.rvCourses.adapter = coursesAdapter
    }

    private fun showCourseDialog(course: Course) {
        AlertDialog.Builder(requireContext())
            .setTitle(course.title)
            .setMessage("Описание: ${course.description}\nСложность: ${course.difficulty.title}")
            .setPositiveButton("Перейти к курсу") { _, _ ->
                findNavController().navigate(
                    R.id.action_navigation_home_to_courseDetailFragment,
                    bundleOf(
                        "courseUuid" to course.uuid,
                        "courseTitle" to course.title
                    )
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadCoursesWithProgress() {
        lifecycleScope.launch {
            try {
                val token = sessionManager.getAccessToken() ?: return@launch
                val courses = coursesService.getCourses().body() ?: emptyList()
                val progress = userService.getProgress("Bearer $token").body()
                    ?: ProgressResponse(emptyList(), emptyList(), emptyList())

                progressMap = courses.associate { course ->
                    val lessons = coursesService.getCourseContent(course.uuid).body() ?: emptyList()
                    val completed = lessons.count { lesson ->
                        progress.lessons.any { it.lesson_uuid == lesson.uuid }
                    }
                    val progressValue = if (lessons.isNotEmpty()) (completed * 100) / lessons.size else 0
                    course.uuid to progressValue
                }

                withContext(Dispatchers.Main) {
                    coursesList.clear()
                    coursesList.addAll(courses)
                    coursesAdapter.progressMap = progressMap
                    coursesAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Ошибка загрузки данных: ${e.message}")
            }
        }
    }

    // Метод загрузки стрика пользователя
    private fun loadStreak() {
        lifecycleScope.launch {
            try {
                val token = sessionManager.getAccessToken() ?: return@launch
                val response = userService.getStreak("Bearer $token")
                if (response.isSuccessful) {
                    val streakResponse = response.body()
                    val streak = streakResponse?.days ?: 0
                    withContext(Dispatchers.Main) {
                        binding.streakCircle.setStreak(streak)
                    }
                } else {
                    Log.e("HomeFragment", "Ошибка загрузки стрика: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Исключение при загрузке стрика: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
