package com.sosiso4kawo.betaapp.ui.exercises

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.api.ExercisesService
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class QuestionDetailFragment : Fragment() {

    private val exercisesService: ExercisesService by inject()
    private var questionUuid: String? = null

    private lateinit var tvQuestionDetail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        questionUuid = arguments?.getString("questionUuid")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_question_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvQuestionDetail = view.findViewById(R.id.tvQuestionDetail)
        loadQuestionDetail()
    }

    @SuppressLint("SetTextI18n")
    private fun loadQuestionDetail() {
        questionUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = exercisesService.getQuestion(uuid)
                if (response.isSuccessful) {
                    response.body()?.let { question ->
                        tvQuestionDetail.text = "Вопрос: ${question.text}\nТип: ${question.type.title}"
                    }
                } else {
                    tvQuestionDetail.text = "Ошибка загрузки деталей вопроса"
                }
            }
        }
    }
}
