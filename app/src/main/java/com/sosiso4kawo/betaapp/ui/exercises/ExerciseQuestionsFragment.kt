package com.sosiso4kawo.betaapp.ui.exercises

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import com.sosiso4kawo.betaapp.data.api.LessonsService
import com.sosiso4kawo.betaapp.data.model.AnswerRequest
import com.sosiso4kawo.betaapp.data.model.FinishAttemptRequest
import com.sosiso4kawo.betaapp.data.model.Question
import com.sosiso4kawo.betaapp.databinding.FragmentExerciseQuestionsBinding
import com.sosiso4kawo.betaapp.ui.lessons.LessonCompletionFragment
import com.sosiso4kawo.betaapp.ui.lessons.LessonResultViewModel
import com.sosiso4kawo.betaapp.util.MediaHelper
import com.sosiso4kawo.betaapp.util.dpToPx
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@Suppress("DEPRECATION")
class ExerciseQuestionsFragment : Fragment() {

    private var _binding: FragmentExerciseQuestionsBinding? = null
    private val binding get() = _binding!!
    private var sessionId: String? = null

    private val exercisesService: ExercisesService by inject()
    private val lessonsService: LessonsService by inject()
    private val lessonResultViewModel: LessonResultViewModel by activityViewModels()

    private var exerciseUuid: String? = null
    private var lessonUuid: String? = null
    private var isSingleExercise: Boolean = false
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
    // Контейнер для дополнительных медиа (вкладки PDF, видео, изображения)
    private lateinit var mediaContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exerciseUuid = arguments?.getString("exerciseUuid")
        lessonUuid = arguments?.getString("lessonUuid")
        isSingleExercise = arguments?.getBoolean("isSingleExercise", false) ?: false

        if (lessonResultViewModel.lessonStartTime == 0L) {
            lessonResultViewModel.lessonStartTime = System.currentTimeMillis()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseQuestionsBinding.inflate(inflater, container, false)
        setupInitialViews()
        return binding.root
    }

    private fun setupInitialViews() {
        binding.header.apply {
            setHeaderBackgroundColor(R.color.header_home)
            showCloseButton()
            setOnNotificationClickListener { }
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
        btnNext = binding.btnNext
        tvQuestionText = binding.tvQuestionText
        ivQuestionImage = binding.ivQuestionImage
        optionsContainer = binding.optionsContainer
        mediaContainer = binding.mediaContainer // Подключаем контейнер для медиа

        btnNext.visibility = View.GONE

        btnNext.setOnClickListener {
            lifecycleScope.launch {
                val isAnswerCorrect = processAndCheckAnswerForCurrentQuestion()
                lessonResultViewModel.aggregatedCorrectAnswers += if (isAnswerCorrect) 1 else 0
                lessonResultViewModel.aggregatedTotalQuestions++
                lessonResultViewModel.aggregatedPoints += calculatePointsForExercise()

                if (currentQuestionIndex < questions.size - 1) {
                    currentQuestionIndex++
                    displayCurrentQuestion()
                } else {
                    if (isSingleExercise) {
                        val totalTime = (System.currentTimeMillis() - lessonResultViewModel.lessonStartTime) / 1000
                        navigateToLessonCompletion(totalTime)
                    } else {
                        lessonUuid?.let { lessonId ->
                            exerciseUuid?.let { currentExerciseId ->
                                handleNextExercise(lessonId, currentExerciseId)
                            } ?: run { navigateToHome() }
                        } ?: run { navigateToHome() }
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
                    }
                }
            } catch (_: Exception) { }
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
                } ?: run { navigateToHome() }
            } else {
                navigateToHome()
            }
        } catch (e: Exception) { navigateToHome() }
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
                        putBoolean("isSingleExercise", isSingleExercise)
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
                val startResponse = exercisesService.startAttempt(exerciseUuid!!)
                if (!startResponse.isSuccessful || startResponse.body() == null) {
                    Toast.makeText(requireContext(), "Не удалось начать попытку: ${startResponse.message()}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                sessionId = startResponse.body()!!.session_id
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
                    }
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки вопросов: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayCurrentQuestion() {
        if (currentQuestionIndex >= questions.size) return

        selectedAnswer = null
        selectedAnswers.clear()
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

        // Обработка основного изображения вопроса
        if (!question.images.isNullOrEmpty()) {
            val mainImageUrl = question.images[0].imageUrl
            ivQuestionImage.visibility = View.VISIBLE
            ivQuestionImage.adjustViewBounds = true
            ivQuestionImage.maxHeight = requireContext().dpToPx(200)
            ivQuestionImage.scaleType = ImageView.ScaleType.FIT_CENTER
            Glide.with(this)
                .load(mainImageUrl)
                .into(ivQuestionImage)
            ivQuestionImage.setOnClickListener {
                MediaHelper.showFullScreenImage(requireContext(), mainImageUrl)
            }
        } else {
            ivQuestionImage.visibility = View.GONE
        }

        // Отображение дополнительных медиа (начиная со второго элемента)
        if (!question.images.isNullOrEmpty() && question.images.size > 1) {
            mediaContainer.visibility = View.VISIBLE
            mediaContainer.removeAllViews()
            // Определяем количество дополнительных элементов
            val additionalMedia = question.images.drop(1)
            // Если только один элемент, устанавливаем центрирование, иначе выравнивание слева
            mediaContainer.gravity = if (additionalMedia.size == 1) Gravity.CENTER else Gravity.START

            additionalMedia.forEach { image ->
                val url = image.imageUrl
                when {
                    url.endsWith(".pdf", ignoreCase = true) -> {
                        val pdfThumbnail = MediaHelper.createThumbnailImageView(requireContext(), R.drawable.ic_pdf_placeholder)
                        pdfThumbnail.setOnClickListener {
                            MediaHelper.showPdfViewer(requireContext(), url, viewLifecycleOwner.lifecycleScope)
                        }
                        mediaContainer.addView(pdfThumbnail)
                    }
                    url.endsWith(".mp4", ignoreCase = true) ||
                            url.endsWith(".webm", ignoreCase = true) ||
                            url.endsWith(".mov", ignoreCase = true) ||
                            url.endsWith(".avi", ignoreCase = true) ||
                            url.endsWith(".mkv", ignoreCase = true) -> {
                        val videoThumbnail = MediaHelper.createThumbnailImageView(requireContext(), R.drawable.ic_video_placeholder)
                        Glide.with(this)
                            .load(url)
                            .thumbnail(0.1f)
                            .into(videoThumbnail)
                        videoThumbnail.setOnClickListener {
                            MediaHelper.showVideoPlayer(requireContext(), url)
                        }
                        mediaContainer.addView(videoThumbnail)
                    }
                    else -> {
                        val imageThumbnail = MediaHelper.createThumbnailImageView(requireContext(), R.drawable.ic_image_placeholder)
                        Glide.with(this)
                            .load(url)
                            .into(imageThumbnail)
                        imageThumbnail.setOnClickListener {
                            MediaHelper.showFullScreenImage(requireContext(), url)
                        }
                        mediaContainer.addView(imageThumbnail)
                    }
                }
            }
        } else {
            mediaContainer.visibility = View.GONE
        }

        // Обработка вариантов ответов и типов вопросов (одиночный/множественный выбор, сопоставление)
        when (question.type_id) {
            1 -> { // Одиночный выбор
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
                            ).apply { setMargins(0, 8, 0, 8) }
                            layoutParams = params
                            setOnClickListener { view ->
                                selectedAnswer = view.tag as? String
                                for (i in 0 until optionsContainer.childCount) {
                                    val child = optionsContainer.getChildAt(i)
                                    if (child is Button) {
                                        child.setBackgroundResource(R.drawable.button_unselected)
                                    }
                                }
                                view.setBackgroundResource(R.drawable.button_selected)
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
            2 -> { // Множественный выбор
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
                            ).apply { setMargins(0, 8, 0, 8) }
                            layoutParams = params
                            setOnClickListener { view ->
                                val answerId = view.tag as? String
                                if (selectedAnswers.contains(answerId)) {
                                    selectedAnswers.remove(answerId)
                                    view.setBackgroundResource(R.drawable.button_unselected)
                                } else {
                                    selectedAnswers.add(answerId ?: "")
                                    view.setBackgroundResource(R.drawable.button_selected)
                                }
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
            3 -> { // Сопоставление
                val leftItems = question.matching?.leftSide.orEmpty()
                val rightItemsOriginal = question.matching?.rightSide.orEmpty()
                if (leftItems.isEmpty() || rightItemsOriginal.isEmpty()) {
                    optionsContainer.addView(TextView(requireContext()).apply {
                        text = "Нет данных для сопоставления"
                    })
                } else {
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
                    leftItems.forEach { leftItem ->
                        val leftButton = Button(requireContext()).apply {
                            text = leftItem
                            tag = leftItem
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            maxLines = 3
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
                                if (selectedLeftButton == this) {
                                    selectedLeftButton = null
                                    setBackgroundResource(R.drawable.button_unselected)
                                    return@setOnClickListener
                                }
                                if (matchedPairs.containsKey(leftKey)) {
                                    matchedPairs.remove(leftKey)
                                    btnNext.visibility = if (matchedPairs.size == leftItems.size) View.VISIBLE else View.GONE
                                    return@setOnClickListener
                                }
                                selectedLeftButton?.setBackgroundResource(R.drawable.button_unselected)
                                selectedLeftButton = this
                                setBackgroundResource(R.drawable.button_selected)
                            }
                        }
                        leftContainer.addView(leftButton)
                    }
                    rightItemsOriginal.shuffled().forEach { rightItem ->
                        val rightButton = Button(requireContext()).apply {
                            text = rightItem
                            tag = rightItem
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            maxLines = 3
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
                                if (selectedLeftButton == null) {
                                    Toast.makeText(requireContext(), "Выберите элемент слева", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                } else {
                                    val leftValue = selectedLeftButton?.tag as String
                                    matchedPairs[leftValue] = rightValue
                                    selectedLeftButton?.setBackgroundResource(R.drawable.button_selected)
                                    setBackgroundResource(R.drawable.button_selected)
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
            1 -> currentQuestion.uuid to selectedAnswer!!
            2 -> currentQuestion.uuid to selectedAnswers.toList()
            3 -> currentQuestion.uuid to matchedPairs
            else -> return false
        }
        sessionId?.let { sessionId ->
            try {
                val request = AnswerRequest(
                    questionId = answerBody.first,
                    answer = answerBody.second
                )
                val response = exercisesService.submitAnswer(sessionId, request)
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        return if (body.correct) {
                            correctAnswersCount++
                            true
                        } else {
                            false
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
