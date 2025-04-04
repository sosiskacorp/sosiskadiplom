package com.sosiso4kawo.betaapp.ui.lessons

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.LessonsService
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LessonInfoDialogFragment : DialogFragment() {

    private val lessonsService: LessonsService by inject()
    private var lessonUuid: String? = null

    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var btnStartLesson: Button
    private lateinit var btnClose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lessonUuid = arguments?.getString("lessonUuid")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setTitle("Информация об уроке")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_lesson_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvTitle = view.findViewById(R.id.tvLessonTitle)
        tvDescription = view.findViewById(R.id.tvLessonDescription)
        btnStartLesson = view.findViewById(R.id.btnStartLesson)
        btnClose = view.findViewById(R.id.btnClose)

        btnClose.setOnClickListener { dismiss() }

        btnStartLesson.setOnClickListener {
            startLesson()
        }

        loadLessonInfo()
    }

    private fun loadLessonInfo() {
        lessonUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = lessonsService.getLessonInfo(uuid)
                if (response.isSuccessful) {
                    response.body()?.let { lessonInfo ->
                        tvTitle.text = lessonInfo.title
                        tvDescription.text = lessonInfo.description.replace("\\n", "\n")
                    }
                } else {
                    tvTitle.text = "Ошибка загрузки"
                    tvDescription.text = "Не удалось загрузить информацию об уроке."
                }
            }
        }
    }

    /**
     * При нажатии на кнопку "Начать урок" загружаем список упражнений,
     * выбираем первое (по порядку) и переходим к экрану ExerciseDetailFragment.
     */
    @SuppressLint("SetTextI18n")
    private fun startLesson() {
        lessonUuid?.let { uuid ->
            lifecycleScope.launch {
                try {
                    val response = lessonsService.getLessonContent(uuid)
                    if (response.isSuccessful) {
                        val exercises = response.body()
                        if (!exercises.isNullOrEmpty()) {
                            // Сортировка по порядку и выбор первого упражнения
                            val firstExercise = exercises.sortedBy { it.order }.first()
                            val bundle = Bundle().apply {
                                putString("exerciseUuid", firstExercise.uuid)
                                putString("lessonUuid", uuid)
                            }
                            findNavController().navigate(R.id.exerciseDetailFragment, bundle)
                            dismiss() // Закрываем диалог после навигации
                        } else {
                            tvDescription.text = "Нет упражнений для этого урока."
                        }
                    } else {
                        tvDescription.text = "Ошибка загрузки контента урока."
                    }
                } catch (e: Exception) {
                    tvDescription.text = "Ошибка: ${e.message}"
                }
            }
        }
    }
}
