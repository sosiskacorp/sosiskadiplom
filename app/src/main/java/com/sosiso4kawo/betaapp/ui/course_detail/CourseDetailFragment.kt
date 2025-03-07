package com.sosiso4kawo.betaapp.ui.course_detail

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
import com.sosiso4kawo.betaapp.data.model.Lesson
import com.sosiso4kawo.betaapp.databinding.FragmentCourseDetailBinding
import com.sosiso4kawo.betaapp.ui.lessons.LessonsAdapter
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import retrofit2.Retrofit

class CourseDetailFragment : Fragment() {

    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!

    private val retrofit: Retrofit by inject()
    private val coursesService: CoursesService by lazy {
        retrofit.create(CoursesService::class.java)
    }

    private var lessonsAdapter: LessonsAdapter? = null
    private val lessonsList = mutableListOf<Lesson>()

    private var courseUuid: String? = null
    private var courseTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            courseUuid = it.getString("courseUuid")
            courseTitle = it.getString("courseTitle")
        }
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

        lessonsAdapter = LessonsAdapter(lessonsList) { lesson ->
            Log.d("CourseDetailFragment", "Lesson selected: ${lesson.title}")
        }
        binding.rvLessons.adapter = lessonsAdapter
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lessonsAdapter = null
    }
}
