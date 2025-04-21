package com.sosiso4kawo.zschoolapp.ui.courses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.model.Course

class CoursesAdapter(
    private val courses: List<Course>,
    var progressMap: Map<String, Int>,
    private val onCourseClick: (Course) -> Unit
) : RecyclerView.Adapter<CoursesAdapter.CourseViewHolder>() {

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCourseTitle: TextView = itemView.findViewById(R.id.tvCourseTitle)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]
        holder.tvCourseTitle.text = course.title

        val progress = progressMap[course.uuid] ?: 0
        holder.progressBar.progress = progress

        val (iconRes, iconColor) = when {
            progress >= 100 -> R.drawable.ic_check to android.R.color.holo_green_dark
            progress > 0 -> R.drawable.ic_star to android.R.color.holo_orange_light
            else -> R.drawable.ic_lock to android.R.color.darker_gray
        }

        holder.statusIcon.apply {
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(context, iconColor))
            alpha = if (progress == 0) 0.5f else 1.0f
        }

        holder.itemView.setOnClickListener { onCourseClick(course) }
    }

    override fun getItemCount(): Int = courses.size
}
