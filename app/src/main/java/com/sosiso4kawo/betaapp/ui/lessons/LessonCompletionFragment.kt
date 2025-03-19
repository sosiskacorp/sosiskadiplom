package com.sosiso4kawo.betaapp.ui.lessons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentLessonCompletionBinding

class LessonCompletionFragment : Fragment() {

    private var _binding: FragmentLessonCompletionBinding? = null
    private val binding get() = _binding!!

    private val lessonResultViewModel: LessonResultViewModel by activityViewModels()

    companion object {
        const val ARG_TOTAL_POINTS = "totalPoints"
        const val ARG_CORRECT_ANSWERS = "correctAnswers"
        const val ARG_TOTAL_QUESTIONS = "totalQuestions"
        const val ARG_TIME_SPENT = "timeSpent" // время в секундах

        fun newInstance(
            totalPoints: Int,
            correctAnswers: Int,
            totalQuestions: Int,
            timeSpent: Long
        ): LessonCompletionFragment {
            val fragment = LessonCompletionFragment()
            val args = Bundle()
            args.putInt(ARG_TOTAL_POINTS, totalPoints)
            args.putInt(ARG_CORRECT_ANSWERS, correctAnswers)
            args.putInt(ARG_TOTAL_QUESTIONS, totalQuestions)
            args.putLong(ARG_TIME_SPENT, timeSpent)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLessonCompletionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем аргументы, переданные в фрагмент
        val totalPoints = arguments?.getInt(ARG_TOTAL_POINTS) ?: 0
        val correctAnswers = arguments?.getInt(ARG_CORRECT_ANSWERS) ?: 0
        val totalQuestions = arguments?.getInt(ARG_TOTAL_QUESTIONS) ?: 0
        val timeSpent = arguments?.getLong(ARG_TIME_SPENT) ?: 0L

        // Вычисляем процент правильных ответов
        val percentCorrect = if (totalQuestions > 0) (correctAnswers * 100 / totalQuestions) else 0
        // Обновляем UI
        binding.tvLessonStatus.text = "Урок завершён"
        binding.tvPoints.text = "Получено поинтов: $totalPoints"
        binding.tvCorrectPercentage.text = "Правильных ответов: $percentCorrect%"
        binding.tvTimeSpent.text = "Время прохождения: ${formatTime(timeSpent)}"

        // Запускаем анимацию для плавного появления элементов
        val fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        binding.tvLessonStatus.startAnimation(fadeIn)
        binding.tvPoints.startAnimation(fadeIn)
        binding.tvCorrectPercentage.startAnimation(fadeIn)
        binding.tvTimeSpent.startAnimation(fadeIn)

        // Пример действия по клику – возвращаемся на главный экран или переходим к следующему уроку
        binding.btnContinue.setOnClickListener {
            lessonResultViewModel.reset()
            findNavController().navigate(R.id.action_lessonCompletionFragment_to_home)
        }
    }

    // Форматирование времени из секунд в формат MM:SS
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
