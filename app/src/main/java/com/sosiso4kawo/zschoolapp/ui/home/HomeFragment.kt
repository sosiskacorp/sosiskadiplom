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
        initializeLearningButton() // Changed from setupLearningButton
        setupCoursesSection()
    }

    // Renamed and modified to set initial disabled state
    private fun initializeLearningButton() {
        binding.startLearningButton.apply {
            text = getString(R.string.start_learning) // Default text
            isEnabled = false // Disabled until courses load
            setOnClickListener {
                Log.d("HomeFragment", "Кнопка обучения нажата (курсы еще не загружены или действие не определено).")
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
                val coursesResponse = coursesService.getCourses()
                val courses = coursesResponse.body() ?: emptyList()

                val progressResponse = userService.getProgress("Bearer $token")
                val progressData = progressResponse.body()
                    ?: ProgressResponse(emptyList(), emptyList(), emptyList())

                progressMap = courses.associate { course ->
                    val courseLessonsResponse = coursesService.getCourseContent(course.uuid)
                    val lessons = courseLessonsResponse.body() ?: emptyList()
                    val completed = lessons.count { lesson ->
                        progressData.lessons.any { it.lesson_uuid == lesson.uuid }
                    }
                    val progressValue = if (lessons.isNotEmpty()) (completed * 100) / lessons.size else 0
                    course.uuid to progressValue
                }

                withContext(Dispatchers.Main) {
                    coursesList.clear()
                    coursesList.addAll(courses) // Assuming courses are in desired order
                    coursesAdapter.progressMap = progressMap
                    coursesAdapter.notifyDataSetChanged()
                    updateLearningButtonState() // Call to update button state
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Ошибка загрузки данных: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.startLearningButton.text = getString(R.string.start_learning)
                    binding.startLearningButton.isEnabled = false
                    Log.e("HomeFragment", "Ошибка загрузки, кнопка обучения отключена.")
                }
            }
        }
    }

    // New method to update the learning button based on loaded data
    private fun updateLearningButtonState() {
        if (coursesList.isEmpty()) {
            binding.startLearningButton.text = getString(R.string.start_learning)
            binding.startLearningButton.isEnabled = false
            binding.startLearningButton.setOnClickListener {
                Log.d("HomeFragment", "Нет доступных курсов для начала.")
            }
            return
        }

        val targetCourse: Course?
        var buttonTextRes = R.string.start_learning // Default to start

        // 1. Try to find a course to continue (0 < progress < 100)
        val courseToContinue = coursesList.firstOrNull { course ->
            val progress = progressMap[course.uuid] ?: 0
            progress > 0 && progress < 100
        }

        if (courseToContinue != null) {
            targetCourse = courseToContinue
            buttonTextRes = R.string.continue_learning
        } else {
            // 2. If no course to continue, find the first course to start (progress == 0)
            val courseToStart = coursesList.firstOrNull { course ->
                (progressMap[course.uuid] ?: 0) == 0
            }
            if (courseToStart != null) {
                targetCourse = courseToStart
                buttonTextRes = R.string.start_learning
            } else {
                // 3. If all courses are 100% completed, or list was not empty but no course matched above,
                // default to the first course in the list (e.g., for review).
                // The button text will remain R.string.start_learning (or could be changed to "Повторить")
                targetCourse = coursesList.firstOrNull()
                // buttonTextRes is already R.string.start_learning
            }
        }

        if (targetCourse != null) {
            binding.startLearningButton.text = getString(buttonTextRes)
            binding.startLearningButton.isEnabled = true
            val finalTargetCourse = targetCourse // Capture for lambda
            binding.startLearningButton.setOnClickListener {
                Log.d("HomeFragment", "Кнопка 'Начать/Продолжить обучение' нажата для курса: ${finalTargetCourse.title}")
                findNavController().navigate(
                    R.id.action_navigation_home_to_courseDetailFragment,
                    bundleOf(
                        "courseUuid" to finalTargetCourse.uuid, // Corrected: use finalTargetCourse
                        "courseTitle" to finalTargetCourse.title // Corrected: use finalTargetCourse
                    )
                )
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
