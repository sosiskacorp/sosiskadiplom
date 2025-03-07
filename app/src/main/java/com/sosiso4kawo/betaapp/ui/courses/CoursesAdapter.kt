package com.sosiso4kawo.betaapp.ui.courses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.Course

class CoursesAdapter(
    private val courses: List<Course>,
    private val onCourseClick: (Course) -> Unit,
    private val onInfoClick: (Course) -> Unit
) : RecyclerView.Adapter<CoursesAdapter.CourseViewHolder>() {

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCourseTitle: TextView = itemView.findViewById(R.id.tvCourseTitle)
        val btnInfo: Button = itemView.findViewById(R.id.btnInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]
        holder.tvCourseTitle.text = course.title

        // При клике по элементу курса – переходим к урокам курса
        holder.itemView.setOnClickListener { onCourseClick(course) }
        // При клике по кнопке "Инфо" – показываем подробности курса
        holder.btnInfo.setOnClickListener { onInfoClick(course) }
    }

    override fun getItemCount(): Int = courses.size
}
