package com.sosiso4kawo.betaapp.ui.lessons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentLessonCompleteBinding

/**
 * Фрагмент для отображения экрана завершения урока.
 * Ожидает в аргументах:
 * • points – набранные поинты (Int)
 * • correctPercentage – процент правильных ответов (Int)
 * • timeSpent – время прохождения урока (String, например, "05:23")
 */
class LessonCompleteFragment : Fragment() {

    private var _binding: FragmentLessonCompleteBinding? = null
    private val binding get() = _binding!!

    private var points: Int = 0
    private var correctPercentage: Int = 0
    private var timeSpent: String = "00:00"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            points = it.getInt("points", 0)
            correctPercentage = it.getInt("correctPercentage", 0)
            timeSpent = it.getString("timeSpent") ?: "00:00"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLessonCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Установка значений в TextView
        binding.tvLessonComplete.text = getString(R.string.lesson_completed)
        binding.tvPoints.text = getString(R.string.points_earned, points)
        binding.tvCorrectPercentage.text = getString(R.string.correct_percentage, correctPercentage)
        binding.tvTimeSpent.text = getString(R.string.time_spent, timeSpent)

        // Загрузка анимаций
        val fadeInAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        val slideUpAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        binding.tvLessonComplete.startAnimation(fadeInAnim)
        binding.tvPoints.startAnimation(slideUpAnim)
        binding.tvCorrectPercentage.startAnimation(slideUpAnim)
        binding.tvTimeSpent.startAnimation(slideUpAnim)

        // Обработка нажатия на кнопку "Завершить уровень" – переход на главный экран
        binding.btnFinishLevel.setOnClickListener {
            findNavController().navigate(R.id.action_lessonCompleteFragment_to_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(points: Int, correctPercentage: Int, timeSpent: String): LessonCompleteFragment {
            val fragment = LessonCompleteFragment()
            val args = Bundle().apply {
                putInt("points", points)
                putInt("correctPercentage", correctPercentage)
                putString("timeSpent", timeSpent)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
