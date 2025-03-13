package com.sosiso4kawo.betaapp.ui.exercises

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import com.sosiso4kawo.betaapp.data.model.*
import com.sosiso4kawo.betaapp.databinding.FragmentExerciseQuestionsBinding
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

    // Для одиночного выбора
    private var selectedAnswer: String? = null
    // Для множественного выбора
    private var selectedAnswers: MutableSet<String> = mutableSetOf()
    // Для вопросов с сопоставлением:
    // - Храним выбранную кнопку левого ряда
    private var selectedLeftButton: Button? = null
    // - Храним корректно сопоставлённые пары
    private var matchedPairs: MutableMap<String, String> = mutableMapOf()

    // UI элементы
    private lateinit var tvQuestionText: TextView
    private lateinit var ivQuestionImage: ImageView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var btnNext: Button

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
            setOnNotificationClickListener { /* Не требуется */ }
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
        btnNext = binding.btnNext
        tvQuestionText = binding.tvQuestionText
        ivQuestionImage = binding.ivQuestionImage
        optionsContainer = binding.optionsContainer

        // Кнопка "Следующий" изначально скрыта до выбора ответа
        btnNext.visibility = View.GONE

        btnNext.setOnClickListener {
            lifecycleScope.launch {
                processAndCheckAnswerForCurrentQuestion()
                if (currentQuestionIndex < questions.size - 1) {
                    currentQuestionIndex++
                    displayCurrentQuestion()
                } else {
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

    private fun displayCurrentQuestion() {
        if (currentQuestionIndex >= questions.size) return

        // Сброс состояний для нового вопроса
        selectedAnswer = null
        selectedAnswers.clear()
        // Для сопоставления сбрасываем выбранные пары и выделение
        selectedLeftButton = null
        matchedPairs.clear()
        btnNext.visibility = View.GONE

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

    private fun updateUIForQuestion(question: Question) {
        optionsContainer.removeAllViews()
        tvQuestionText.text = question.text

        if (!question.images.isNullOrEmpty()) {
            ivQuestionImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(question.images!![0].imageUrl)
                .into(ivQuestionImage)
        } else {
            ivQuestionImage.visibility = View.GONE
        }

        when (question.type_id) {
            1 -> {
                // Одиночный выбор
                val options = question.questionOptions.orEmpty()
                if (options.isNotEmpty()) {
                    options.forEach { option ->
                        val optionButton = Button(requireContext()).apply {
                            text = option.text
                            tag = option.uuid
                            setBackgroundResource(R.drawable.button_unselected)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.setMargins(0, 8, 0, 8)
                            layoutParams = params
                            setOnClickListener { view ->
                                // Сохраняем выбранный ответ
                                selectedAnswer = view.tag as? String
                                // Сбрасываем стиль всех кнопок
                                for (i in 0 until optionsContainer.childCount) {
                                    val child = optionsContainer.getChildAt(i)
                                    if (child is Button) {
                                        child.setBackgroundResource(R.drawable.button_unselected)
                                    }
                                }
                                // Применяем стиль выбранной кнопки
                                view.setBackgroundResource(R.drawable.button_selected)
                                // Делаем кнопку "Следующий" видимой
                                btnNext.visibility = View.VISIBLE
                            }
                        }
                        optionsContainer.addView(optionButton)
                    }
                } else {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет доступных вариантов ответа"
                    })
                }
            }
            2 -> {
                // Множественный выбор
                val options = question.questionOptions.orEmpty()
                selectedAnswers.clear()
                if (options.isNotEmpty()) {
                    options.forEach { option ->
                        val optionButton = Button(requireContext()).apply {
                            text = option.text
                            tag = option.uuid
                            setBackgroundResource(R.drawable.button_unselected)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.setMargins(0, 8, 0, 8)
                            layoutParams = params
                            setOnClickListener { view ->
                                val answerId = view.tag as? String
                                if (selectedAnswers.contains(answerId)) {
                                    // Снять выбор
                                    selectedAnswers.remove(answerId)
                                    view.setBackgroundResource(R.drawable.button_unselected)
                                } else {
                                    // Добавить выбор
                                    selectedAnswers.add(answerId ?: "")
                                    view.setBackgroundResource(R.drawable.button_selected)
                                }
                                // Если выбран хотя бы один вариант — показываем кнопку "Следующий"
                                btnNext.visibility = if (selectedAnswers.isNotEmpty()) View.VISIBLE else View.GONE
                            }
                        }
                        optionsContainer.addView(optionButton)
                    }
                } else {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет доступных вариантов ответа"
                    })
                }
            }
            3 -> {
                val leftItems = question.matching?.leftSide.orEmpty()
                val rightItemsOriginal = question.matching?.rightSide.orEmpty()

                if (leftItems.isEmpty() || rightItemsOriginal.isEmpty()) {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет данных для сопоставления"
                    })
                } else {
                    val correctMapping = mutableMapOf<String, String>()
                    for (i in leftItems.indices) {
                        correctMapping[leftItems[i]] = rightItemsOriginal.getOrNull(i) ?: ""
                    }
                    val rightItems = rightItemsOriginal.shuffled().toMutableList()

                    val horizontalContainer = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val horizontalMargin = requireContext().dpToPx(8)
                    val buttonMinHeight = requireContext().dpToPx(48)

                    val leftContainer = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            setMargins(0, 0, horizontalMargin, 0)
                        }
                    }

                    val rightContainer = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            setMargins(horizontalMargin, 0, 0, 0)
                        }
                    }

                    // Создание кнопок для левого ряда
                    leftItems.forEach { leftItem ->
                        val leftButton = Button(requireContext()).apply {
                            text = leftItem
                            tag = leftItem
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            maxLines = 3
                            isVerticalScrollBarEnabled = true
                            movementMethod = ScrollingMovementMethod.getInstance()

                            setBackgroundResource(R.drawable.button_unselected)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                minimumHeight = buttonMinHeight
                                setMargins(0, 8, 0, 8)
                            }
                            layoutParams = params

                            setPadding(
                                requireContext().dpToPx(12),
                                requireContext().dpToPx(8),
                                requireContext().dpToPx(12),
                                requireContext().dpToPx(8)
                            )

                            setOnClickListener {
                                selectedLeftButton?.takeIf { it != this }?.setBackgroundResource(R.drawable.button_unselected)
                                selectedLeftButton = if (selectedLeftButton == this) null else this
                                setBackgroundResource(if (selectedLeftButton == this) R.drawable.button_selected else R.drawable.button_unselected)
                            }
                        }
                        leftContainer.addView(leftButton)
                    }

                    // Создание кнопок для правого ряда
                    rightItems.forEach { rightItem ->
                        val rightButton = Button(requireContext()).apply {
                            text = rightItem
                            tag = rightItem
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            maxLines = 3
                            isVerticalScrollBarEnabled = true
                            movementMethod = ScrollingMovementMethod.getInstance()

                            setBackgroundResource(R.drawable.button_unselected)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                minimumHeight = buttonMinHeight
                                setMargins(0, 8, 0, 8)
                            }
                            layoutParams = params

                            setPadding(
                                requireContext().dpToPx(12),
                                requireContext().dpToPx(8),
                                requireContext().dpToPx(12),
                                requireContext().dpToPx(8)
                            )

                            setOnClickListener {
                                if (selectedLeftButton == null) {
                                    Toast.makeText(requireContext(), "Выберите элемент слева", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }

                                val leftValue = selectedLeftButton?.tag as? String
                                val rightValue = tag as? String

                                if (correctMapping[leftValue] == rightValue) {
                                    matchedPairs[leftValue!!] = rightValue!!
                                    leftContainer.removeView(selectedLeftButton)
                                    rightContainer.removeView(this)
                                    selectedLeftButton = null

                                    if (leftContainer.childCount == 0 && rightContainer.childCount == 0) {
                                        btnNext.visibility = View.VISIBLE
                                    }
                                } else {
                                    val leftBtn = selectedLeftButton
                                    val rightBtn = this
                                    listOf(leftBtn, rightBtn).forEach { it?.setBackgroundResource(R.drawable.button_error) }
                                    leftContainer.isEnabled = false
                                    rightContainer.isEnabled = false

                                    Handler(Looper.getMainLooper()).postDelayed({
                                        listOf(leftBtn, rightBtn).forEach { it?.setBackgroundResource(R.drawable.button_unselected) }
                                        leftContainer.isEnabled = true
                                        rightContainer.isEnabled = true
                                        selectedLeftButton = null
                                    }, 1000)
                                }
                            }
                        }
                        rightContainer.addView(rightButton)
                    }

                    horizontalContainer.addView(leftContainer)
                    horizontalContainer.addView(rightContainer)
                    optionsContainer.addView(horizontalContainer)
                }
            }
            else -> {
                optionsContainer.addView(TextView(requireContext()).apply {
                    text = "Неподдерживаемый тип вопроса"
                })
            }
        }
    }

    private suspend fun processAndCheckAnswerForCurrentQuestion() {
        val currentQuestion = questions[currentQuestionIndex]
        when (currentQuestion.type_id) {
            1 -> {
                if (selectedAnswer != null) {
                    try {
                        val response = exercisesService.checkAnswer(
                            currentQuestion.uuid,
                            SingleAnswer(selectedAnswer!!)
                        )
                        if (response.isSuccessful && response.body()?.correct == true) {
                            correctAnswersCount++
                        }
                    } catch (e: Exception) {
                        Log.e("ExerciseQuestions", "Exception while checking answer", e)
                    }
                }
            }
            2 -> {
                if (selectedAnswers.isNotEmpty()) {
                    try {
                        val response = exercisesService.checkAnswer(
                            currentQuestion.uuid,
                            MultipleAnswer(selectedAnswers.toList())
                        )
                        if (response.isSuccessful && response.body()?.correct == true) {
                            correctAnswersCount++
                        }
                    } catch (e: Exception) {
                        Log.e("ExerciseQuestions", "Exception while checking multiple answer", e)
                    }
                }
            }
            3 -> {
                // Отправляем сформированное сопоставление
                if (matchedPairs.isNotEmpty()) {
                    try {
                        val response = exercisesService.checkAnswer(
                            currentQuestion.uuid,
                            MatchingAnswer(matchedPairs)
                        )
                        if (response.isSuccessful && response.body()?.correct == true) {
                            correctAnswersCount++
                        }
                    } catch (e: Exception) {
                        Log.e("ExerciseQuestions", "Exception while checking matching answer", e)
                    }
                }
            }
        }
    }

    fun Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
