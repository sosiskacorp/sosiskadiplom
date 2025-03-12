package com.sosiso4kawo.betaapp.ui.exercises

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import com.sosiso4kawo.betaapp.data.model.*
import com.sosiso4kawo.betaapp.databinding.FragmentExerciseQuestionsBinding
import com.sosiso4kawo.betaapp.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ExerciseQuestionsFragment : Fragment() {

    private var _binding: FragmentExerciseQuestionsBinding? = null
    private val binding get() = _binding!!

    private val exercisesService: ExercisesService by inject()
    private var exerciseUuid: String? = null
    private var questions: List<Question> = emptyList()
    private var currentQuestionIndex = 0
    private var correctAnswersCount = 0

    // UI элементы
    private lateinit var tvQuestionText: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var btnNext: Button
    // Кастомный хедер
    private lateinit var header: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exerciseUuid = arguments?.getString("exerciseUuid")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentExerciseQuestionsBinding.inflate(inflater, container, false)
        setupInitialViews()
        return binding.root
    }

    private fun setupInitialViews() {
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
            showCloseButton()
            setOnNotificationClickListener { /* Пусто, если не нужен */ }

            // Устанавливаем обработчик закрытия
            setOnCloseClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Подтверждение")
                    .setMessage("При завершении теста весь прогресс не будет сохранён. Вы действительно хотите выйти?")
                    .setPositiveButton("Да") { _, _ ->
                        findNavController().navigate(R.id.action_exerciseQuestionsFragment_to_home)
                    }
                    .setNegativeButton("Нет", null)
                    .show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnNext = binding.btnNext  // Initialize btnNext from binding
        tvQuestionText = binding.tvQuestionText  // Similarly, initialize other views if needed
        optionsContainer = binding.optionsContainer
        btnNext.setOnClickListener {
            lifecycleScope.launch {
                processAndCheckAnswerForCurrentQuestion()
                if (currentQuestionIndex < questions.size - 1) {
                    currentQuestionIndex++
                    displayCurrentQuestion()
                } else {
                    // Тест пройден – выполняется переход (например, на CourseDetailFragment)
                    Toast.makeText(
                        requireContext(),
                        "Тест завершён. Правильных ответов: $correctAnswersCount из ${questions.size}",
                        Toast.LENGTH_LONG
                    ).show()
                    findNavController().navigate(R.id.action_exerciseQuestionsFragment_to_home)
                }
            }
        }

        loadQuestions()
    }

    private fun loadQuestions() {
        if (exerciseUuid == null) {
            Toast.makeText(requireContext(), "Неверный идентификатор упражнения", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val response = exercisesService.getExerciseQuestions(exerciseUuid!!)
                if (response.isSuccessful) {
                    response.body()?.let { questionList ->
                        questions = questionList.sortedBy { it.order }
                        if (questions.isNotEmpty()) {
                            currentQuestionIndex = 0
                            displayCurrentQuestion()
                        } else {
                            Toast.makeText(requireContext(), "В этом упражнении нет вопросов", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Toast.makeText(requireContext(), "Не удалось загрузить вопросы", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки вопросов", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Отображаем текущий вопрос с детальной информацией
    private fun displayCurrentQuestion() {
        if (currentQuestionIndex >= questions.size) return

        val summaryQuestion = questions[currentQuestionIndex]
        lifecycleScope.launch {
            try {
                val response = exercisesService.getQuestion(summaryQuestion.uuid)
                val questionToDisplay = if (response.isSuccessful && response.body() != null) {
                    response.body()!!
                } else {
                    summaryQuestion
                }
                updateUIForQuestion(questionToDisplay)
            } catch (e: Exception) {
                updateUIForQuestion(summaryQuestion)
            }
        }
    }

    // Обновление UI в зависимости от типа вопроса
    private fun updateUIForQuestion(question: Question) {
        optionsContainer.removeAllViews()
        tvQuestionText.text = question.text

        when (question.type_id) {
            1 -> { // Один вариант ответа
                val radioGroup = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
                val options = question.questionOptions.orEmpty()
                if (options.isNotEmpty()) {
                    options.forEach { option ->
                        RadioButton(requireContext()).apply {
                            text = option.text
                            tag = option.uuid
                            id = View.generateViewId()
                            radioGroup.addView(this)
                        }
                    }
                    optionsContainer.addView(radioGroup)
                } else {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет доступных вариантов ответа"
                    })
                }
            }
            2 -> { // Несколько вариантов ответа
                question.questionOptions?.forEach { option ->
                    CheckBox(requireContext()).apply {
                        text = option.text
                        tag = option.uuid
                        optionsContainer.addView(this)
                    }
                } ?: run {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет доступных вариантов ответа"
                    })
                }
            }
            3 -> { // Сопоставление
                val leftSide = question.matching?.leftSide.orEmpty()
                val rightSide = question.matching?.rightSide.orEmpty()
                if (leftSide.isNotEmpty() && rightSide.isNotEmpty()) {
                    val randomizedRight = rightSide.shuffled()
                    leftSide.forEach { leftItem ->
                        LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 8, 0, 8) }
                            TextView(requireContext()).apply {
                                text = leftItem
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }.also { addView(it) }
                            Spinner(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                adapter = ArrayAdapter(
                                    requireContext(),
                                    android.R.layout.simple_spinner_item,
                                    randomizedRight
                                ).apply {
                                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                }
                            }.also { addView(it) }
                            optionsContainer.addView(this)
                        }
                    }
                } else {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет данных для сопоставления"
                    })
                }
            }
            else -> {
                optionsContainer.addView(TextView(requireContext()).apply {
                    text = "Неподдерживаемый тип вопроса"
                })
            }
        }
    }

    // Обработка и проверка ответа для текущего вопроса
    private suspend fun processAndCheckAnswerForCurrentQuestion() {
        val currentQuestion = questions[currentQuestionIndex]
        when (currentQuestion.type_id) {
            1 -> { // Один вариант ответа
                val radioGroup = optionsContainer.getChildAt(0) as? RadioGroup
                val selectedId = radioGroup?.checkedRadioButtonId
                if (selectedId != null && selectedId != -1) {
                    val selectedRadio = radioGroup.findViewById<RadioButton>(selectedId)
                    val answer = selectedRadio.tag as? String
                    if (answer != null) {
                        try {
                            val response = exercisesService.checkAnswer(
                                currentQuestion.uuid,
                                SingleAnswer(answer)
                            )
                            if (response.isSuccessful && response.body()?.correct == true) {
                                correctAnswersCount++
                            }
                        } catch (e: Exception) {
                            Log.e("ExerciseQuestions", "Exception while checking answer", e)
                        }
                    }
                }
            }
            2 -> { // Несколько вариантов ответа
                val selectedAnswers = mutableListOf<String>()
                for (i in 0 until optionsContainer.childCount) {
                    val child = optionsContainer.getChildAt(i)
                    if (child is CheckBox && child.isChecked) {
                        selectedAnswers.add(child.tag as String)
                    }
                }
                if (selectedAnswers.isNotEmpty()) {
                    try {
                        val response = exercisesService.checkAnswer(
                            currentQuestion.uuid,
                            MultipleAnswer(selectedAnswers)
                        )
                        if (response.isSuccessful && response.body()?.correct == true) {
                            correctAnswersCount++
                        }
                    } catch (e: Exception) {
                        Log.e("ExerciseQuestions", "Exception while checking answer", e)
                    }
                }
            }
            3 -> { // Сопоставление
                val matchingAnswers = mutableMapOf<String, String>()
                for (i in 0 until optionsContainer.childCount) {
                    val row = optionsContainer.getChildAt(i) as? LinearLayout ?: continue
                    if (row.childCount >= 2) {
                        val leftTv = row.getChildAt(0) as? TextView
                        val spinner = row.getChildAt(1) as? Spinner
                        val leftValue = leftTv?.text.toString()
                        val selectedRight = spinner?.selectedItem as? String ?: ""
                        matchingAnswers[leftValue] = selectedRight
                    }
                }
                if (matchingAnswers.isNotEmpty()) {
                    try {
                        val response = exercisesService.checkAnswer(
                            currentQuestion.uuid,
                            MatchingAnswer(matchingAnswers)
                        )
                        if (response.isSuccessful && response.body()?.correct == true) {
                            correctAnswersCount++
                        }
                    } catch (e: Exception) {
                        Log.e("ExerciseQuestions", "Exception while checking answer", e)
                    }
                }
            }
        }
    }
}
