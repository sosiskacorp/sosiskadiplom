package com.sosiso4kawo.betaapp.ui.lessons

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.LessonsService
import com.sosiso4kawo.betaapp.data.model.Exercise
import com.sosiso4kawo.betaapp.databinding.FragmentLessonContentBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LessonContentFragment : Fragment() {

    private var _binding: FragmentLessonContentBinding? = null
    private val binding get() = _binding!!
    private val lessonsService: LessonsService by inject()
    private var lessonUuid: String? = null
    private lateinit var exercisesAdapter: ExercisesAdapter
    private val exercisesList = mutableListOf<Exercise>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Получаем UUID урока из аргументов
        lessonUuid = arguments?.getString("lessonUuid")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLessonContentBinding.inflate(inflater, container, false)
        setupInitialViews()
        return binding.root
    }

    private fun setupInitialViews() {
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
            showBackButton()
            setOnBackClickListener {
                findNavController().navigate(R.id.action_lessonContentFragment_to_home)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvExercises)
        // При выборе упражнения передаём флаг isSingleExercise=true
        exercisesAdapter = ExercisesAdapter(exercisesList) { exercise ->
            val bundle = Bundle().apply {
                putString("exerciseUuid", exercise.uuid)
                putString("lessonUuid", lessonUuid)
                putBoolean("isSingleExercise", true)
            }
            findNavController().navigate(R.id.exerciseDetailFragment, bundle)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = exercisesAdapter

        loadLessonContent()
    }

    /**
     * Загрузка списка упражнений для урока.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadLessonContent() {
        lessonUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = lessonsService.getLessonContent(uuid)
                if (response.isSuccessful) {
                    response.body()?.let { exercises ->
                        // Сортируем упражнения по порядку
                        exercisesList.clear()
                        exercisesList.addAll(exercises.sortedBy { it.order })
                        exercisesAdapter.notifyDataSetChanged()
                    }
                } else {
                    Log.e("LessonContentFragment", "Error loading lesson content: ${response.code()}")
                }
            }
        }
    }
}
