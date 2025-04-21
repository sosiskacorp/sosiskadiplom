package com.sosiso4kawo.zschoolapp.ui.lessons

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.databinding.FragmentLessonCompletionBinding
import kotlin.random.Random

class LessonCompletionFragment : Fragment() {

    private var _binding: FragmentLessonCompletionBinding? = null
    private val binding get() = _binding!!

    private val lessonResultViewModel: LessonResultViewModel by activityViewModels()

    companion object {
        const val ARG_TOTAL_POINTS = "totalPoints"
        const val ARG_CORRECT_ANSWERS = "correctAnswers"
        const val ARG_TOTAL_QUESTIONS = "totalQuestions"
        const val ARG_TIME_SPENT = "timeSpent" // время в секундах

    }

    // Фразы для различных градаций (по 10 фраз)
    private val phrases0to25 = listOf(
        "Не отчаивайтесь – каждая ошибка приближает к успеху.",
        "Начните с малого – впереди большие достижения!",
        "Ошибки – это путь к знаниям.",
        "Сегодня – первый шаг, завтра – успех.",
        "Провал – это возможность научиться.",
        "Неудача – это лишь начало пути.",
        "Каждая ошибка делает вас сильнее.",
        "Сейчас сложно, но впереди победы.",
        "Дайте себе шанс – успех не за горами.",
        "Начните заново, и у вас всё получится!"
    )

    private val phrases25to50 = listOf(
        "Неплохо, но можно еще лучше.",
        "Вы уже на полпути к успеху!",
        "Есть над чем поработать, но результат радует.",
        "Неплохой результат – продолжайте в том же духе.",
        "Вы движетесь в правильном направлении.",
        "Немного усилий – и результат улучшится.",
        "Успех требует времени – продолжайте.",
        "Неплохой старт, но впереди лучшие дни.",
        "Ваш результат уже говорит о потенциале.",
        "Поставьте цель выше – вы способны на большее!"
    )

    private val phrases50to75 = listOf(
        "Хорошая работа, продолжайте в том же духе!",
        "Ваши усилия дают отличный результат.",
        "Вы уже почти достигли вершин!",
        "Отличный результат – продолжайте совершенствоваться.",
        "Вы значительно улучшили свои навыки.",
        "Половина успеха уже достигнута.",
        "Видны большие перспективы – так держать!",
        "Ваш результат внушает уважение.",
        "Продолжайте работать – успех гарантирован.",
        "Ваши знания и умения на высоте!"
    )

    private val phrases75to100 = listOf(
        "Отличный результат, вы настоящий мастер!",
        "Вы блистаете успехом – продолжайте в том же духе!",
        "Ваш результат – это образец для подражания.",
        "Вы достигли высот, о которых мечтают многие.",
        "Потрясающий результат – вы звезда!",
        "Ваш успех вдохновляет окружающих.",
        "Вы на пике возможностей – так держать!",
        "Каждая минута работы оправдывается успехом.",
        "Вы доказали, что совершенство возможно.",
        "Вы – настоящий эксперт в своем деле!"
    )

    // Массивы drawable-ресурсов для изображений (по 5 на каждую градацию)
    private val images0to25 = listOf(
        R.drawable.ic_motivation_0_1,
        R.drawable.ic_motivation_0_2,
        R.drawable.ic_motivation_0_3,
        R.drawable.ic_motivation_0_4,
        R.drawable.ic_motivation_0_5
    )

    private val images25to50 = listOf(
        R.drawable.ic_motivation_25_1,
        R.drawable.ic_motivation_25_2,
        R.drawable.ic_motivation_25_3,
        R.drawable.ic_motivation_25_4,
        R.drawable.ic_motivation_25_5
    )

    private val images50to75 = listOf(
        R.drawable.ic_motivation_50_1,
        R.drawable.ic_motivation_50_2,
        R.drawable.ic_motivation_50_3,
        R.drawable.ic_motivation_50_4,
        R.drawable.ic_motivation_50_5
    )

    private val images75to100 = listOf(
        R.drawable.ic_motivation_75_1,
        R.drawable.ic_motivation_75_2,
        R.drawable.ic_motivation_75_3,
        R.drawable.ic_motivation_75_4,
        R.drawable.ic_motivation_75_5
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLessonCompletionBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем аргументы
        val totalPoints = arguments?.getInt(ARG_TOTAL_POINTS) ?: 0
        val correctAnswers = arguments?.getInt(ARG_CORRECT_ANSWERS) ?: 0
        val totalQuestions = arguments?.getInt(ARG_TOTAL_QUESTIONS) ?: 0
        val timeSpent = arguments?.getLong(ARG_TIME_SPENT) ?: 0L

        // Вычисляем процент правильных ответов
        val percentCorrect = if (totalQuestions > 0) (correctAnswers * 100 / totalQuestions) else 0

        // Устанавливаем мотивирующий текст и изображение
        binding.tvMotivation.text = getMotivationalText(percentCorrect)
        binding.ivMotivationalImage.setImageResource(getMotivationalImage(percentCorrect))

        // Обновляем UI – значения устанавливаем в карточки
        binding.tvCorrectPercentage.text = "$percentCorrect%"
        binding.tvPoints.text = "$totalPoints"
        binding.tvTimeSpent.text = formatTime(timeSpent)

        // Запускаем анимацию появления элементов
        val fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        binding.tvLessonStatus.startAnimation(fadeIn)
        binding.ivMotivationalImage.startAnimation(fadeIn)
        binding.tvMotivation.startAnimation(fadeIn)
        binding.resultsContainer.startAnimation(fadeIn)
        binding.btnContinue.startAnimation(fadeIn)

        // Обработчик клика для кнопки "Продолжить"
        binding.btnContinue.setOnClickListener {
            lessonResultViewModel.reset()
            findNavController().navigate(R.id.action_lessonCompletionFragment_to_home)
        }
    }

    // Генерация мотивирующего текста по диапазону процента
    private fun getMotivationalText(percent: Int): String {
        val phrases = when (percent) {
            in 0..24 -> phrases0to25
            in 25..49 -> phrases25to50
            in 50..74 -> phrases50to75
            in 75..100 -> phrases75to100
            else -> listOf("Отличный результат!")
        }
        return phrases[Random.nextInt(phrases.size)]
    }

    // Генерация изображения по диапазону процента
    private fun getMotivationalImage(percent: Int): Int {
        val images = when (percent) {
            in 0..24 -> images0to25
            in 25..49 -> images25to50
            in 50..74 -> images50to75
            in 75..100 -> images75to100
            else -> listOf(R.drawable.placeholder_avatar)
        }
        return images[Random.nextInt(images.size)]
    }

    // Форматирование времени (MM:SS)
    @SuppressLint("DefaultLocale")
    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
