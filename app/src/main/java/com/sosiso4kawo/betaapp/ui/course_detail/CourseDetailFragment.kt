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
import com.sosiso4kawo.betaapp.ui.lessons.LessonInfoDialogFragment
import com.sosiso4kawo.betaapp.ui.lessons.LessonsAdapter
import com.sosiso4kawo.betaapp.util.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CourseDetailFragment : Fragment() {

    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!

    private val coursesService: CoursesService by inject()
    private val userService: UserService by inject()

    // Список уроков
    private val lessonsList = mutableListOf<Lesson>()
    // Набор UUID завершённых уроков
    private val completedLessons = mutableSetOf<String>()

    private var courseUuid: String? = null
    private var courseTitle: String? = null

    private lateinit var sessionManager: SessionManager
    private var lessonsAdapter: LessonsAdapter? = null

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
        loadStreak() // загрузка стрика для отображения в круге
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
                Log.d("CourseDetailFragment", "Кнопка обучения нажата")
            }
        }

        // Установка заголовка курса
        binding.tvLessonsHeader.text = courseTitle

        // Настройка списка уроков – создание адаптера с callback для обработки нажатия
        binding.rvLessons.layoutManager = GridLayoutManager(
            requireContext(),
            3,
            GridLayoutManager.VERTICAL,
            false
        )
        lessonsAdapter = LessonsAdapter(lessonsList, completedLessons) { lesson ->
            onLessonClick(lesson)
        }
        binding.rvLessons.adapter = lessonsAdapter
    }

    /**
     * Обработка нажатия на урок.
     * Если урок завершён, переходим к LessonContentFragment,
     * иначе – к LessonInfoDialogFragment.
     */
    private fun onLessonClick(lesson: Lesson) {
        val bundle = Bundle().apply { putString("lessonUuid", lesson.uuid) }
        if (completedLessons.contains(lesson.uuid)) {
            findNavController().navigate(R.id.lessonContentFragment, bundle)
        } else {
            val dialog = LessonInfoDialogFragment().apply { arguments = bundle }
            dialog.show(requireActivity().supportFragmentManager, "LessonInfoDialog")
        }
    }

    /**
     * Загрузка уроков курса с последующей сортировкой по ордеру.
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
                    // Очищаем список и добавляем уроки, сортируя их по полю order
                    lessonsList.clear()
                    val sortedLessons = response.body()!!.sortedBy { it.order }
                    lessonsList.addAll(sortedLessons)
                    lessonsAdapter?.notifyDataSetChanged()
                } else {
                    Log.e("CourseDetailFragment", "Ошибка загрузки уроков: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CourseDetailFragment", "Исключение при загрузке уроков", e)
            }
        }
    }

    /**
     * Загрузка прогресса пользователя – формирование набора завершённых уроков.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadProgress() {
        lifecycleScope.launch {
            try {
                val accessToken = sessionManager.getAccessToken()
                if (accessToken.isNullOrEmpty()) {
                    Log.e("CourseDetailFragment", "Access token is null or empty")
                    return@launch
                }
                val response = userService.getProgress("Bearer $accessToken")
                if (response.isSuccessful && response.body() != null) {
                    val progress: ProgressResponse = response.body()!!
                    completedLessons.clear()
                    progress.lessons.forEach { completedLessons.add(it.lesson_uuid) }
                    lessonsAdapter?.notifyDataSetChanged()
                } else {
                    Log.e("CourseDetailFragment", "Ошибка загрузки прогресса: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CourseDetailFragment", "Исключение при загрузке прогресса", e)
            }
        }
    }

    /**
     * Загрузка стрика пользователя.
     */
    private fun loadStreak() {
        lifecycleScope.launch {
            try {
                val token = sessionManager.getAccessToken() ?: return@launch
                val response = userService.getStreak("Bearer $token")
                if (response.isSuccessful) {
                    val streak = response.body()?.days ?: 0
                    withContext(Dispatchers.Main) {
                        binding.streakCircle.setStreak(streak)
                    }
                } else {
                    Log.e("CourseDetailFragment", "Ошибка загрузки стрика: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CourseDetailFragment", "Исключение при загрузке стрика: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lessonsAdapter = null
    }
}
