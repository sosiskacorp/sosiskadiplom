package com.sosiso4kawo.betaapp.ui.lessons

import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.Lesson
import com.sosiso4kawo.betaapp.ui.custom.LevelButtonView

class LessonsAdapter(
    private val lessons: List<Lesson>
) : RecyclerView.Adapter<LessonsAdapter.LessonViewHolder>() {

    inner class LessonViewHolder(val levelButton: LevelButtonView) : RecyclerView.ViewHolder(levelButton) {
        fun bind(lesson: Lesson) {
            levelButton.setLevel(lesson.order, LevelButtonView.LevelStatus.AVAILABLE)
            levelButton.setOnClickListener {
                // Получаем Activity из контекста для показа диалога
                val activity = levelButton.context as? FragmentActivity
                activity?.let {
                    val dialog = LessonInfoDialogFragment().apply {
                        arguments = Bundle().apply { putString("lessonUuid", lesson.uuid) }
                    }
                    dialog.show(it.supportFragmentManager, "LessonInfoDialog")
                }
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
        holder.bind(lessons[position])
    }

    override fun getItemCount(): Int = lessons.size
}
