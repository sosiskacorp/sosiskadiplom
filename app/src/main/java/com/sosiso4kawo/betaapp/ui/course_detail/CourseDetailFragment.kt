package com.sosiso4kawo.betaapp.ui.course_detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.CoursesService
import com.sosiso4kawo.betaapp.data.api.UserService
import com.sosiso4kawo.betaapp.data.model.Lesson
import com.sosiso4kawo.betaapp.data.model.ProgressResponse
import com.sosiso4kawo.betaapp.databinding.FragmentCourseDetailBinding
import com.sosiso4kawo.betaapp.ui.lessons.LessonsAdapter
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject


class CourseDetailFragment : Fragment() {

    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!

    private val coursesService: CoursesService by inject()
    private val userService: UserService by inject()

    private var lessonsAdapter: LessonsAdapter? = null
    private val lessonsList = mutableListOf<Lesson>()

    // Набор uuid пройденных уроков
    private val completedLessons = mutableSetOf<String>()

    private var courseUuid: String? = null
    private var courseTitle: String? = null

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            courseUuid = it.getString("courseUuid")
            courseTitle = it.getString("courseTitle")
        }
        sessionManager = SessionManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInitialViews()
        loadLessons()
        loadProgress()
    }

    private fun setupInitialViews() {
        // Настройка хедера
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
            findViewById<android.widget.ProgressBar>(R.id.progress_bar)?.visibility = View.VISIBLE
            setOnNotificationClickListener { }
            showBackButton(true)
            setOnBackClickListener { findNavController().navigateUp() }
        }

        // Настройка круга стрика
        binding.streakCircle.setStreak(0)

        // Настройка кнопки обучения
        binding.startLearningButton.apply {
            text = getString(R.string.start_learning)
            setOnClickListener {
                Log.d("CourseDetailFragment", "Learning button clicked")
            }
        }

        // Установка заголовка курса
        binding.tvLessonsHeader.text = courseTitle

        // Настройка списка уроков
        binding.rvLessons.layoutManager = GridLayoutManager(
            requireContext(),
            3,
            GridLayoutManager.VERTICAL,
            false
        )
        // Изначально адаптер создаётся с пустым набором пройденных уроков
        lessonsAdapter = LessonsAdapter(lessonsList, completedLessons)
        binding.rvLessons.adapter = lessonsAdapter
    }

    /**
     * Загрузка уроков.
     * Если список уроков получается отдельным запросом, реализуйте здесь загрузку.
     * В данном примере предполагается, что lessonsList заполняется из ответа getCourseContent.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadLessons() {
        if (courseUuid == null) {
            Log.e("CourseDetailFragment", "Course UUID is null")
            return
        }
        lifecycleScope.launch {
            try {
                val response = coursesService.getCourseContent(courseUuid!!)
                if (response.isSuccessful && response.body() != null) {
                    lessonsList.clear()
                    lessonsList.addAll(response.body()!!)
                    lessonsAdapter?.notifyDataSetChanged()
                } else {
                    Log.e("CourseDetailFragment", "Error loading lessons: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CourseDetailFragment", "Exception while loading lessons", e)
            }
        }
    }

    /**
     * Загрузка прогресса пользователя
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadProgress() {
        lifecycleScope.launch {
            try {
                // Получаем токен из SessionManager
                val accessToken = sessionManager.getAccessToken()
                if (accessToken.isNullOrEmpty()) {
                    Log.e("CourseDetailFragment", "Access token is null or empty")
                    return@launch
                }
                val response = userService.getProgress("Bearer $accessToken")
                if (response.isSuccessful && response.body() != null) {
                    val progress: ProgressResponse = response.body()!!
                    completedLessons.clear()
                    // Заполняем набор пройденных уроков (используем поле lesson_uuid)
                    progress.lessons.forEach { completedLessons.add(it.lesson_uuid) }
                    // Обновляем адаптер, чтобы применить новые статусы
                    lessonsAdapter?.notifyDataSetChanged()
                } else {
                    Log.e("CourseDetailFragment", "Error loading progress: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CourseDetailFragment", "Exception while loading progress", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lessonsAdapter = null
    }
}
