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

        // Настройка списка уроков – создаём адаптер с передачей callback для обработки нажатия
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
            // Урок пройден – переходим к фрагменту с контентом урока
            findNavController().navigate(R.id.lessonContentFragment, bundle)
        } else {
            // Урок не пройден – открываем диалоговое окно с информацией об уроке
            val dialog = LessonInfoDialogFragment().apply {
                arguments = bundle
            }
            dialog.show(requireActivity().supportFragmentManager, "LessonInfoDialog")
        }
    }
    /**
     * Загрузка уроков курса.
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
