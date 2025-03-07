package com.sosiso4kawo.betaapp.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.CoursesService
import com.sosiso4kawo.betaapp.data.model.Course
import com.sosiso4kawo.betaapp.databinding.FragmentHomeBinding
import com.sosiso4kawo.betaapp.ui.course_detail.CourseDetailFragment
import com.sosiso4kawo.betaapp.ui.courses.CoursesAdapter
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import retrofit2.Retrofit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var coursesAdapter: CoursesAdapter
    private val coursesList = mutableListOf<Course>()

    // Получаем Retrofit через DI (Koin)
    private val retrofit: Retrofit by inject()
    // Создаём сервис курсов через Retrofit
    private val coursesService: CoursesService by lazy {
        retrofit.create(CoursesService::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupInitialViews()
        return binding.root
    }

    private fun setupInitialViews() {
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
            // Если в CustomHeaderView есть элементы progress_bar и notification_button, то находим их по id
            findViewById<android.widget.ProgressBar>(R.id.progress_bar)?.visibility = View.VISIBLE
            findViewById<android.widget.ImageButton>(R.id.notification_button)?.visibility = View.VISIBLE
            setOnNotificationClickListener { }
        }
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
        // Настраиваем RecyclerView для курсов
        binding.rvCourses.layoutManager = LinearLayoutManager(context)
        coursesAdapter = CoursesAdapter(coursesList,
            onCourseClick = { course ->
                // Переход к экрану уроков выбранного курса
                val bundle = Bundle().apply {
                    putString("courseUuid", course.uuid)
                    putString("courseTitle", course.title)
                }
                // Используем Navigation Component для перехода
                findNavController().navigate(
                    R.id.action_navigation_home_to_courseDetailFragment,
                    bundle
                )
            },
            onInfoClick = { course ->
                // Отображаем диалог с информацией о курсе (как ранее)
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(course.title)
                    .setMessage("Описание: ${course.description}\nСложность: ${course.difficulty.title}")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        )
        binding.rvCourses.adapter = coursesAdapter

        loadCourses()
    }

    private fun loadCourses() {
        lifecycleScope.launch {
            try {
                val response = coursesService.getCourses()
                if (response.isSuccessful) {
                    response.body()?.let { list ->
                        coursesList.clear()
                        coursesList.addAll(list)
                        coursesAdapter.notifyDataSetChanged()
                    }
                } else {
                    Log.e("HomeFragment", "Ошибка загрузки курсов: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Исключение при загрузке курсов: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
