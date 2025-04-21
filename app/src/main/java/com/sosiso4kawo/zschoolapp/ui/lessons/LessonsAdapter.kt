package com.sosiso4kawo.zschoolapp.ui.lessons

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.model.Lesson
import com.sosiso4kawo.zschoolapp.ui.custom.LevelButtonView

class LessonsAdapter(
    private val lessons: List<Lesson>,
    private val completedLessons: Set<String>,
    private val onLessonClick: (Lesson) -> Unit
) : RecyclerView.Adapter<LessonsAdapter.LessonViewHolder>() {

    inner class LessonViewHolder(private val levelButton: LevelButtonView) : RecyclerView.ViewHolder(levelButton) {
        fun bind(lesson: Lesson, position: Int) {
            // Проверяем, пройден ли урок (используем поле lesson.uuid)
            val isCompleted = completedLessons.contains(lesson.uuid)
            // Первый урок всегда доступен; для остальных проверяем, что предыдущий урок завершён
            val previousLessonCompleted = if (position == 0) true
            else completedLessons.contains(lessons[position - 1].uuid)

            val status = when {
                isCompleted -> LevelButtonView.LevelStatus.COMPLETED
                previousLessonCompleted -> LevelButtonView.LevelStatus.AVAILABLE
                else -> LevelButtonView.LevelStatus.LOCKED
            }

            levelButton.setLevel(lesson.order, status)

            levelButton.setOnClickListener {
                // Если урок заблокирован, не реагируем на нажатие
                if (status == LevelButtonView.LevelStatus.LOCKED) return@setOnClickListener
                // Вызываем callback, чтобы родительский фрагмент сам решил, что делать
                onLessonClick(lesson)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val levelButton = LevelButtonView(parent.context)

        // Рассчитываем размер кнопки для квадратной формы
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val horizontalPadding = parent.context.resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin) * 2
        val availableWidth = screenWidth - horizontalPadding
        val margin = parent.context.resources.getDimensionPixelSize(R.dimen.level_button_margin)
        val spanCount = 3 // 3 колонки

        // Вычисляем размер кнопки с учетом марджинов между ними
        val totalMargins = margin * 2 * spanCount
        val buttonSize = (availableWidth - totalMargins) / spanCount

        val layoutParams = RecyclerView.LayoutParams(buttonSize, buttonSize)
        layoutParams.setMargins(margin, margin, margin, margin)

        levelButton.layoutParams = layoutParams
        return LessonViewHolder(levelButton)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(lessons[position], position)
    }

    override fun getItemCount(): Int = lessons.size
}
