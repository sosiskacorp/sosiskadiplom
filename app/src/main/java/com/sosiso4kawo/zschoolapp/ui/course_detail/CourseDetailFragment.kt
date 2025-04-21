package com.sosiso4kawo.zschoolapp.ui.course_detail

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
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.api.CoursesService
import com.sosiso4kawo.zschoolapp.data.api.UserService
import com.sosiso4kawo.zschoolapp.data.model.Lesson
import com.sosiso4kawo.zschoolapp.data.model.ProgressResponse
import com.sosiso4kawo.zschoolapp.databinding.FragmentCourseDetailBinding
import com.sosiso4kawo.zschoolapp.ui.lessons.LessonInfoDialogFragment
import com.sosiso4kawo.zschoolapp.ui.lessons.LessonsAdapter
import com.sosiso4kawo.zschoolapp.util.SessionManager
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
            showBackButton(true)
            setOnBackClickListener { findNavController().navigateUp() }
        }

        // Настройка круга стрика
        binding.streakCircle.setStreak(0)

        // Настройка кнопки обучения
        binding.startLearningButton.apply {
            text = getString(R.string.start_learning)
            setOnClickListener {
                handleStartLearningClick()
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
     * Обрабатывает нажатие на кнопку "Начать обучение" или "Продолжить обучение".
     * Находит первый непройденный урок и открывает диалог с информацией о нем.
     */
    private fun handleStartLearningClick() {
        Log.d("CourseDetailFragment", "Кнопка обучения нажата")

        if (lessonsList.isEmpty()) {
            Log.d("CourseDetailFragment", "Список уроков пуст")
            return
        }

        // Сортируем уроки по порядку и находим первый непройденный
        val firstUncompletedLesson = lessonsList
            .sortedBy { it.order }
            .firstOrNull { !completedLessons.contains(it.uuid) }

        // Если все уроки завершены, берем последний урок в курсе
        val targetLesson = firstUncompletedLesson ?: lessonsList.sortedBy { it.order }.last()

        Log.d("CourseDetailFragment", "Выбран урок: ${targetLesson.title}, UUID: ${targetLesson.uuid}")

        // Переходим к диалогу информации об уроке, если урок не завершен
        // Иначе - сразу к содержимому урока
        if (completedLessons.contains(targetLesson.uuid)) {
            val bundle = Bundle().apply { putString("lessonUuid", targetLesson.uuid) }
            findNavController().navigate(R.id.lessonContentFragment, bundle)
        } else {
            val bundle = Bundle().apply { putString("lessonUuid", targetLesson.uuid) }
            val dialog = LessonInfoDialogFragment().apply { arguments = bundle }
            dialog.show(requireActivity().supportFragmentManager, "LessonInfoDialog")
        }
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

                    // Обновляем прогрессбар после загрузки списка уроков
                    updateProgressBar()
                    // Обновляем кнопку обучения в зависимости от прогресса
                    updateStartLearningButton()
                } else {
                    Log.e("CourseDetailFragment", "Ошибка загрузки уроков: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CourseDetailFragment", "Исключение при загрузке уроков", e)
            }
        }
    }

    /**
     * Загрузка прогресса пользователя – формирование набора завершённых уроков и обновление прогрессбара.
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

                    // Обновляем прогрессбар на основе процента завершенных уроков в текущем курсе
                    updateProgressBar()
                    // Обновляем кнопку обучения в зависимости от прогресса
                    updateStartLearningButton()

                    // Уведомляем адаптер о изменениях
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
     * Обновление прогрессбара на основе текущего прогресса по курсу
     */
    private suspend fun updateProgressBar() {
        if (lessonsList.isEmpty()) return

        val completedLessonsCount = lessonsList.count { lesson ->
            completedLessons.contains(lesson.uuid)
        }

        val progressPercentage = if (lessonsList.isNotEmpty()) {
            (completedLessonsCount * 100) / lessonsList.size
        } else {
            0
        }

        withContext(Dispatchers.Main) {
            binding.header.setProgress(progressPercentage)
        }
    }

    /**
     * Обновление текста и функционала кнопки "Начать/Продолжить обучение"
     * в зависимости от прогресса пользователя
     */
    private suspend fun updateStartLearningButton() {
        val completedLessonsCount = lessonsList.count { completedLessons.contains(it.uuid) }
        val hasStartedLearning = completedLessonsCount > 0

        withContext(Dispatchers.Main) {
            binding.startLearningButton.text = getString(
                if (hasStartedLearning) R.string.continue_learning
                else R.string.start_learning
            )
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