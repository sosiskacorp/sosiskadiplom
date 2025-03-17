package com.sosiso4kawo.betaapp.ui.exercises

import android.app.AlertDialog
import android.app.Dialog
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
import android.view.Window
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

        // Обработка изображений
        if (!question.images.isNullOrEmpty()) {
            if (question.images!!.size == 1) {
                ivQuestionImage.visibility = View.VISIBLE
                ivQuestionImage.adjustViewBounds = true
                ivQuestionImage.maxHeight = requireContext().dpToPx(200) // ограничение по высоте
                ivQuestionImage.scaleType = ImageView.ScaleType.FIT_CENTER
                Glide.with(this)
                    .load(question.images!![0].imageUrl)
                    .into(ivQuestionImage)

                ivQuestionImage.setOnClickListener {
                    // Создаём диалог для отображения изображения во весь экран
                    val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    val fullScreenImageView = ImageView(requireContext()).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    Glide.with(requireContext())
                        .load(question.images!![0].imageUrl)
                        .into(fullScreenImageView)
                    // При нажатии на полноэкранное изображение закрываем диалог
                    fullScreenImageView.setOnClickListener { dialog.dismiss() }
                    dialog.setContentView(fullScreenImageView)
                    dialog.show()
                }
            } else {
                // Если несколько изображений, можно аналогично добавить обработчик на каждое изображение
                ivQuestionImage.visibility = View.GONE
                val horizontalScrollView = HorizontalScrollView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val imagesContainer = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                question.images!!.forEach { image ->
                    val imageView = ImageView(requireContext()).apply {
                        adjustViewBounds = true
                        maxHeight = requireContext().dpToPx(200)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        val params = LinearLayout.LayoutParams(
                            requireContext().dpToPx(100),
                            requireContext().dpToPx(100)
                        )
                        params.setMargins(8, 8, 8, 8)
                        layoutParams = params
                    }
                    Glide.with(this)
                        .load(image.imageUrl)
                        .into(imageView)
                    // Обработчик для увеличения при нажатии на картинку
                    imageView.setOnClickListener {
                        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                        val fullScreenImageView = ImageView(requireContext()).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        Glide.with(requireContext())
                            .load(image.imageUrl)
                            .into(fullScreenImageView)
                        fullScreenImageView.setOnClickListener { dialog.dismiss() }
                        dialog.setContentView(fullScreenImageView)
                        dialog.show()
                    }
                    imagesContainer.addView(imageView)
                }
                horizontalScrollView.addView(imagesContainer)
                optionsContainer.addView(horizontalScrollView, 0)
            }
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
// В методе updateUIForQuestion для вопросов с type_id == 3:
            3 -> {
                val leftItems = question.matching?.leftSide.orEmpty()
                val rightItemsOriginal = question.matching?.rightSide.orEmpty()

                if (leftItems.isEmpty() || rightItemsOriginal.isEmpty()) {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет данных для сопоставления"
                    })
                } else {
                    // Формируем корректное сопоставление для последующей проверки
                    val correctMapping = mutableMapOf<String, String>()
                    for (i in leftItems.indices) {
                        correctMapping[leftItems[i]] = rightItemsOriginal.getOrNull(i) ?: ""
                    }
                    val rightItems = rightItemsOriginal.shuffled().toMutableList()

                    // Очищаем ранее сохранённые пары
                    matchedPairs.clear()
                    // Локальная карта для хранения ссылок на кнопки выбранных пар:
                    val matchedButtons = mutableMapOf<String, Pair<Button, Button>>()

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
                        ).apply { setMargins(0, 0, horizontalMargin, 0) }
                    }

                    val rightContainer = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply { setMargins(horizontalMargin, 0, 0, 0) }
                    }

                    // Создаём кнопки для левого ряда
                    leftItems.forEach { leftItem ->
                        val leftButton = Button(requireContext()).apply {
                            text = leftItem
                            tag = leftItem
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            maxLines = 3
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
                                // Выбираем левую кнопку (очищаем предыдущий выбор, если есть)
                                selectedLeftButton?.setBackgroundResource(R.drawable.button_unselected)
                                selectedLeftButton = this
                                setBackgroundResource(R.drawable.button_selected)
                            }
                        }
                        leftContainer.addView(leftButton)
                    }

                    // Создаём кнопки для правого ряда
                    rightItems.forEach { rightItem ->
                        val rightButton = Button(requireContext()).apply {
                            text = rightItem
                            tag = rightItem
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            maxLines = 3
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
                                if (leftValue != null && rightValue != null) {
                                    // Сохраняем выбранную пару и сохраняем ссылки на кнопки
                                    matchedPairs[leftValue] = rightValue
                                    matchedButtons[leftValue] = Pair(selectedLeftButton!!, this)
                                    // Обновляем визуальное выделение для выбранной пары
                                    selectedLeftButton?.setBackgroundResource(R.drawable.button_selected)
                                    this.setBackgroundResource(R.drawable.button_selected)
                                    selectedLeftButton = null
                                    // Если сопоставлены все пары, делаем кнопку "Следующий" видимой
                                    btnNext.visibility = if (matchedPairs.size == leftItems.size) View.VISIBLE else View.GONE
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
                // Одиночный выбор (без изменений)
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
                // Множественный выбор (без изменений)
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
                val leftItems = questions[currentQuestionIndex].matching?.leftSide.orEmpty()
                if (matchedPairs.size != leftItems.size) {
                    Toast.makeText(requireContext(), "Сопоставьте все пары", Toast.LENGTH_SHORT).show()
                    return
                }
                // Формируем корректное сопоставление для сравнения
                val correctMapping = mutableMapOf<String, String>()
                leftItems.forEachIndexed { index, leftItem ->
                    val correctRight = questions[currentQuestionIndex].matching?.rightSide?.getOrNull(index) ?: ""
                    correctMapping[leftItem] = correctRight
                }
                var allCorrect = true
                // Проверяем каждую пару
                for ((left, userRight) in matchedPairs) {
                    if (correctMapping[left] != userRight) {
                        // Подсвечиваем неверную пару красным.
                        // Для этого, если у вас сохранены ссылки на кнопки (например, в matchedButtons),
                        // можно установить для них фон ошибки:
                        // matchedButtons[left]?.first?.setBackgroundResource(R.drawable.button_error)
                        // matchedButtons[left]?.second?.setBackgroundResource(R.drawable.button_error)
                        allCorrect = false
                    }
                }
                if (allCorrect) {
                    // Отправляем ответ на сервер
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
                    // Если ответ верный, можно, например, очистить UI или перейти к следующему вопросу
                } else {
                    Toast.makeText(requireContext(), "Есть ошибки в сопоставлении", Toast.LENGTH_SHORT).show()
                    // Неверные пары остаются подсвеченными красным, чтобы пользователь мог их исправить.
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
