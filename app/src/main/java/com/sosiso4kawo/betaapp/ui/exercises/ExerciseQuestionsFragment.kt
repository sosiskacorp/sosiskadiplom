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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import com.sosiso4kawo.betaapp.data.api.LessonsService
import com.sosiso4kawo.betaapp.data.model.*
import com.sosiso4kawo.betaapp.databinding.FragmentExerciseQuestionsBinding
import com.sosiso4kawo.betaapp.ui.lessons.LessonCompletionFragment
import com.sosiso4kawo.betaapp.ui.lessons.LessonResultViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ExerciseQuestionsFragment : Fragment() {

    private var _binding: FragmentExerciseQuestionsBinding? = null
    private val binding get() = _binding!!
    private var sessionId: String? = null

    private val exercisesService: ExercisesService by inject()
    private val lessonsService: LessonsService by inject()

    private val lessonResultViewModel: LessonResultViewModel by activityViewModels()

    private var exerciseUuid: String? = null
    private var lessonUuid: String? = null // Новая переменная для идентификатора урока
    private var questions: List<Question> = emptyList()
    private var currentQuestionIndex = 0
    private var correctAnswersCount = 0

    // Для одиночного выбора
    private var selectedAnswer: String? = null
    // Для множественного выбора
    private var selectedAnswers: MutableSet<String> = mutableSetOf()
    // Для вопросов с сопоставлением:
    private var selectedLeftButton: Button? = null
    private var matchedPairs: MutableMap<String, String> = mutableMapOf()

    // UI элементы
    private lateinit var tvQuestionText: TextView
    private lateinit var ivQuestionImage: ImageView
    private lateinit var optionsContainer: ViewGroup
    private lateinit var btnNext: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exerciseUuid = arguments?.getString("exerciseUuid")
        lessonUuid = arguments?.getString("lessonUuid")
        // Если это начало урока, зафиксировать время старта
        if (lessonResultViewModel.lessonStartTime == 0L) {
            lessonResultViewModel.lessonStartTime = System.currentTimeMillis()
        }
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

        btnNext.visibility = View.GONE

        btnNext.setOnClickListener {
            lifecycleScope.launch {
                val isAnswerCorrect = processAndCheckAnswerForCurrentQuestion()

                // Если вам не нужно отдельно проверять, можно просто положиться на корректное обновление correctAnswersCount
                lessonResultViewModel.aggregatedCorrectAnswers += if (isAnswerCorrect) 1 else 0
                lessonResultViewModel.aggregatedTotalQuestions++  // Добавляем один вопрос за раз
                lessonResultViewModel.aggregatedPoints += calculatePointsForExercise()

                if (currentQuestionIndex < questions.size - 1) {
                    currentQuestionIndex++
                    displayCurrentQuestion()
                } else {
                    lessonUuid?.let { lessonId ->
                        exerciseUuid?.let { currentExerciseId ->
                            handleNextExercise(lessonId, currentExerciseId)
                        } ?: run {
                            Log.e("Navigation", "exerciseUuid is null")
                            navigateToHome()
                        }
                    } ?: run {
                        Log.e("Navigation", "lessonUuid is null")
                        navigateToHome()
                    }
                }
            }
        }
        loadQuestions()
    }


    private suspend fun handleNextExercise(lessonId: String, currentExerciseId: String) {
        sessionId?.let { sessionId ->
            val finishRequest = FinishAttemptRequest(
                correct_answers = correctAnswersCount,
                is_finished = true,
                questions = questions.size
            )

            try {
                val finishResponse = exercisesService.finishAttempt(sessionId, finishRequest)

                if (finishResponse.isSuccessful) {
                    finishResponse.body()?.lessons?.let {
                        lessonResultViewModel.aggregatedCorrectAnswers += it.sumOf { it.total_points }
                    } ?: Log.e("API", "Отсутствуют данные lessons в ответе")
                } else {
                    Log.e("API", "Ошибка завершения: ${finishResponse.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("API", "Ошибка завершения попытки", e)
            }
        }

        try {
            val response = lessonsService.getLessonContent(lessonId)
            if (response.isSuccessful) {
                response.body()?.let { exercisesList ->
                    val sortedExercises = exercisesList.sortedBy { it.order }
                    val currentIndex = sortedExercises.indexOfFirst { it.uuid == currentExerciseId }

                    if (currentIndex != -1 && currentIndex < sortedExercises.lastIndex) {
                        val nextExercise = sortedExercises[currentIndex + 1]
                        navigateToExercise(nextExercise.uuid, lessonId)
                    } else {
                        val totalTime = (System.currentTimeMillis() - lessonResultViewModel.lessonStartTime) / 1000
                        navigateToLessonCompletion(totalTime)
                    }
                } ?: run {
                    Log.e("NextExercise", "Пустое тело ответа")
                    navigateToHome()
                }
            } else {
                Log.e("NextExercise", "Ошибка сервера: ${response.errorBody()?.string()}")
                navigateToHome()
            }
        } catch (e: Exception) {
            Log.e("NextExercise", "Исключение: ${e.javaClass.simpleName}", e)
            navigateToHome()
        }
    }

    private fun navigateToLessonCompletion(totalTime: Long) {
        val bundle = Bundle().apply {
            putInt(LessonCompletionFragment.ARG_TOTAL_POINTS, lessonResultViewModel.aggregatedPoints)
            putInt(LessonCompletionFragment.ARG_CORRECT_ANSWERS, lessonResultViewModel.aggregatedCorrectAnswers)
            putInt(LessonCompletionFragment.ARG_TOTAL_QUESTIONS, lessonResultViewModel.aggregatedTotalQuestions)
            putLong(LessonCompletionFragment.ARG_TIME_SPENT, totalTime)
        }
        findNavController().navigate(R.id.lessonCompletionFragment, bundle)
    }

    private fun navigateToExercise(exerciseUuid: String, lessonUuid: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (findNavController().currentDestination?.id == R.id.exerciseQuestionsFragment) {
                findNavController().navigate(
                    R.id.action_exerciseQuestionsFragment_to_exerciseDetailFragment,
                    Bundle().apply {
                        putString("exerciseUuid", exerciseUuid)
                        putString("lessonUuid", lessonUuid)
                    }
                )
            }
        }, 1500)
    }

    private fun navigateToHome() {
        Handler(Looper.getMainLooper()).postDelayed({
            findNavController().navigate(R.id.action_exerciseQuestionsFragment_to_home)
        }, 1500)
    }

    private suspend fun calculatePointsForExercise(): Int {
        exerciseUuid?.let { uuid ->
            val response = exercisesService.getExercise(uuid)
            if (response.isSuccessful && response.body() != null) {
                val exercise = response.body()!!
                val maxPoints = exercise.points
                Log.d("Points", "correctAnswersCount: $correctAnswersCount, totalQuestions: ${questions.size}, maxPoints: $maxPoints")
                // Если количество правильных ответов равно числу вопросов,
                // значит упражнение пройдено полностью правильно и начисляются все поинты.
                return if (correctAnswersCount == questions.size) maxPoints else 0
            }
        }
        return 0
    }

    private fun loadQuestions() {
        if (exerciseUuid == null) {
            Toast.makeText(requireContext(), "Неверный идентификатор упражнения", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Начинаем новую попытку
                val startResponse = exercisesService.startAttempt(exerciseUuid!!)

                if (!startResponse.isSuccessful || startResponse.body() == null) {
                    Toast.makeText(requireContext(), "Не удалось начать попытку: ${startResponse.message()}", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                sessionId = startResponse.body()!!.session_id

                // Загружаем вопросы
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
                    Toast.makeText(requireContext(), "Ошибка загрузки вопросов: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("API", "Ошибка при старте попытки", e)
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
// В блоке для вопросов с type_id == 3:
            3 -> {
                val leftItems = question.matching?.leftSide.orEmpty()
                val rightItemsOriginal = question.matching?.rightSide.orEmpty()

                if (leftItems.isEmpty() || rightItemsOriginal.isEmpty()) {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет данных для сопоставления"
                    })
                } else {
                    // Формируем корректное сопоставление для последующей проверки (не используется в логике сопоставления)
                    val correctMapping = mutableMapOf<String, String>()
                    for (i in leftItems.indices) {
                        correctMapping[leftItems[i]] = rightItemsOriginal.getOrNull(i) ?: ""
                    }
                    val rightItems = rightItemsOriginal.shuffled().toMutableList()

                    // Очищаем ранее сохранённые пары
                    matchedPairs.clear()
                    // Локальная карта для хранения ссылок на кнопки выбранных пар
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

                    // Создаём кнопки для левого ряда с возможностью отмены выбора/сопоставления
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
                                val leftKey = tag as String
                                // Если кнопка уже выбрана как активная – отменяем выбор
                                if (selectedLeftButton == this) {
                                    selectedLeftButton = null
                                    setBackgroundResource(R.drawable.button_unselected)
                                    Log.d("MatchingPairs", "Отмена выбора левого элемента: $leftKey")
                                    return@setOnClickListener
                                }
                                // Если кнопка уже участвует в сопоставлении – удаляем существующую пару
                                if (matchedPairs.containsKey(leftKey)) {
                                    matchedPairs.remove(leftKey)
                                    matchedButtons[leftKey]?.let { pair ->
                                        pair.first.setBackgroundResource(R.drawable.button_unselected)
                                        pair.second.setBackgroundResource(R.drawable.button_unselected)
                                        matchedButtons.remove(leftKey)
                                        Log.d("MatchingPairs", "Удалено сопоставление для левого элемента: $leftKey")
                                    }
                                    btnNext.visibility = if (matchedPairs.size == leftItems.size) View.VISIBLE else View.GONE
                                    return@setOnClickListener
                                }
                                // Устанавливаем данную кнопку как активную для сопоставления
                                selectedLeftButton?.setBackgroundResource(R.drawable.button_unselected)
                                selectedLeftButton = this
                                setBackgroundResource(R.drawable.button_selected)
                                Log.d("MatchingPairs", "Выбран левый элемент: $leftKey")
                            }
                        }
                        leftContainer.addView(leftButton)
                    }

                    // Создаём кнопки для правого ряда с возможностью отмены сопоставления
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
                                val rightValue = tag as String
                                // Проверяем, существует ли уже сопоставление для этой правой кнопки
                                var alreadyPairedLeft: String? = null
                                for ((leftKey, pair) in matchedButtons) {
                                    if (pair.second == this) {
                                        alreadyPairedLeft = leftKey
                                        break
                                    }
                                }
                                if (selectedLeftButton == null) {
                                    // Если активного левого выбора нет, а кнопка уже сопоставлена – отменяем сопоставление
                                    if (alreadyPairedLeft != null) {
                                        matchedPairs.remove(alreadyPairedLeft)
                                        matchedButtons[alreadyPairedLeft]?.let { pair ->
                                            pair.first.setBackgroundResource(R.drawable.button_unselected)
                                            pair.second.setBackgroundResource(R.drawable.button_unselected)
                                            matchedButtons.remove(alreadyPairedLeft)
                                            Log.d("MatchingPairs", "Отмена сопоставления правого элемента: $rightValue, ранее связанного с левым: $alreadyPairedLeft")
                                        }
                                        btnNext.visibility = if (matchedPairs.size == leftItems.size) View.VISIBLE else View.GONE
                                    } else {
                                        Toast.makeText(requireContext(), "Выберите элемент слева", Toast.LENGTH_SHORT).show()
                                    }
                                    return@setOnClickListener
                                } else {
                                    // Если активный левый выбран, создаём новое сопоставление
                                    val leftValue = selectedLeftButton?.tag as String
                                    // Если выбранный правый элемент уже был сопоставлен – сначала удаляем старую пару
                                    if (alreadyPairedLeft != null) {
                                        matchedPairs.remove(alreadyPairedLeft)
                                        matchedButtons[alreadyPairedLeft]?.let { pair ->
                                            pair.first.setBackgroundResource(R.drawable.button_unselected)
                                            pair.second.setBackgroundResource(R.drawable.button_unselected)
                                            matchedButtons.remove(alreadyPairedLeft)
                                            Log.d("MatchingPairs", "Переназначение: правый элемент $rightValue ранее был связан с левым $alreadyPairedLeft")
                                        }
                                    }
                                    // Сохраняем новое сопоставление
                                    matchedPairs[leftValue] = rightValue
                                    matchedButtons[leftValue] = Pair(selectedLeftButton!!, this)
                                    // Обновляем визуальное выделение для сопоставленной пары
                                    selectedLeftButton?.setBackgroundResource(R.drawable.button_selected)
                                    this.setBackgroundResource(R.drawable.button_selected)
                                    Log.d("MatchingPairs", "Сопоставлено: левый элемент $leftValue с правым элементом $rightValue")
                                    selectedLeftButton = null
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

    private suspend fun processAndCheckAnswerForCurrentQuestion(): Boolean {
        val currentQuestion = questions[currentQuestionIndex]
        val answerBody = when (currentQuestion.type_id) {
            1 -> currentQuestion.uuid to selectedAnswer!! // Pair<String, String>
            2 -> currentQuestion.uuid to selectedAnswers.toList() // Pair<String, List<String>>
            3 -> currentQuestion.uuid to matchedPairs // Pair<String, Map<String, String>>
            else -> {
                Log.e("API", "Неизвестный тип вопроса: ${currentQuestion.type_id}")
                return false
            }
        }

        sessionId?.let { sessionId ->
            try {
                val request = AnswerRequest(
                    questionId = answerBody.first,
                    answer = answerBody.second
                )

                Log.d("API", "Отправляемый JSON: ${Gson().toJson(request)}")

                val response = exercisesService.submitAnswer(sessionId, request)

                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        return if (body.correct) {
                            correctAnswersCount++  // Увеличиваем счетчик при верном ответе
                            Log.d("API", "Ответ верный: $body")
                            true
                        } else {
                            Log.d("API", "Ответ неверный: $body")
                            false
                        }
                    }
                } else {
                    Log.e("API", "Ошибка ${response.code()}: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("API", "Ошибка отправки ответа", e)
            }
        }
        return false
    }

    fun Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
