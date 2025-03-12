package com.sosiso4kawo.betaapp.ui.exercises

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import com.sosiso4kawo.betaapp.data.model.Exercise
import com.sosiso4kawo.betaapp.data.model.Question
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ExerciseDetailFragment : Fragment() {

    private val exercisesService: ExercisesService by inject()
    private var exerciseUuid: String? = null

    private lateinit var tvExerciseTitle: TextView
    private lateinit var tvExerciseDescription: TextView
    private lateinit var tvExercisePoints: TextView
    private lateinit var btnLoadQuestions: Button
    private lateinit var tvQuestions: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exerciseUuid = arguments?.getString("exerciseUuid")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_exercise_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvExerciseTitle = view.findViewById(R.id.tvExerciseTitle)
        tvExerciseDescription = view.findViewById(R.id.tvExerciseDescription)
        tvExercisePoints = view.findViewById(R.id.tvExercisePoints)
        btnLoadQuestions = view.findViewById(R.id.btnLoadQuestions)
        tvQuestions = view.findViewById(R.id.tvQuestions)

        loadExerciseInfo()

        btnLoadQuestions.setOnClickListener {
            val bundle = Bundle().apply { putString("exerciseUuid", exerciseUuid) }
            findNavController().navigate(R.id.exerciseQuestionsFragment, bundle)
        }

    }

    private fun loadExerciseInfo() {
        exerciseUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = exercisesService.getExercise(uuid)
                if (response.isSuccessful) {
                    response.body()?.let { exercise: Exercise ->
                        tvExerciseTitle.text = exercise.title
                        tvExerciseDescription.text = exercise.description
                        tvExercisePoints.text = "${exercise.points} points"
                    }
                } else {
                    tvExerciseTitle.text = "Ошибка загрузки упражнения"
                }
            }
        }
    }

    private fun loadExerciseQuestions() {
        exerciseUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = exercisesService.getExerciseQuestions(uuid)
                if (response.isSuccessful) {
                    val questions: List<Question> = response.body() ?: emptyList()
                    // Выводим список вопросов – можно реализовать кастомное отображение вместо простого текста
                    val questionsText = questions.sortedBy { it.order }
                        .joinToString(separator = "\n") { question ->
                            "${question.order}. ${question.text} (${question.type.title})"
                        }
                    tvQuestions.text = questionsText
                } else {
                    tvQuestions.text = "Ошибка загрузки вопросов"
                }
            }
        }
    }
}
