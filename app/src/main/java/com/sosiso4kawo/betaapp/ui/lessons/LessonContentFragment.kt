package com.sosiso4kawo.betaapp.ui.lessons

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
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LessonContentFragment : Fragment() {

    private val lessonsService: LessonsService by inject()
    private var lessonUuid: String? = null
    private lateinit var exercisesAdapter: ExercisesAdapter
    private val exercisesList = mutableListOf<Exercise>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lessonUuid = arguments?.getString("lessonUuid")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_lesson_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvExercises)
        exercisesAdapter = ExercisesAdapter(exercisesList) { exercise ->
            val bundle = Bundle().apply {
                putString("exerciseUuid", exercise.uuid)
                putString("lessonUuid", lessonUuid) // Добавлено lessonUuid
            }
            findNavController().navigate(R.id.exerciseDetailFragment, bundle)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = exercisesAdapter

        loadLessonContent()
    }

    private fun loadLessonContent() {
        lessonUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = lessonsService.getLessonContent(uuid)
                if (response.isSuccessful) {
                    response.body()?.let { exercises ->
                        val sortedExercises = exercises.sortedBy { it.order }
                        if (sortedExercises.isNotEmpty()) {
                            // Исправленный блок перехода
                            val firstExercise = sortedExercises.first()
                            val bundle = Bundle().apply {
                                putString("exerciseUuid", firstExercise.uuid)
                                putString("lessonUuid", lessonUuid) // Добавлено lessonUuid
                            }
                            findNavController().navigate(R.id.exerciseDetailFragment, bundle)
                        }
                    }
                } else {
                    Log.e("LessonContentFragment", "Error loading lesson content: ${response.code()}")
                }
            }
        }
    }
}
