package com.sosiso4kawo.betaapp.ui.lessons

import androidx.lifecycle.ViewModel

class LessonResultViewModel : ViewModel() {
    var aggregatedPoints: Int = 0
    var aggregatedCorrectAnswers: Int = 0
    var aggregatedTotalQuestions: Int = 0
    var lessonStartTime: Long = 0

    fun reset() {
        aggregatedPoints = 0
        aggregatedCorrectAnswers = 0
        aggregatedTotalQuestions = 0
        lessonStartTime = 0
    }
}
