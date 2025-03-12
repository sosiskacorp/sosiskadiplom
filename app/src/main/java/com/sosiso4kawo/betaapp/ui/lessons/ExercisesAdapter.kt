package com.sosiso4kawo.betaapp.ui.lessons

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.data.model.Exercise

class ExercisesAdapter(
    private val exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit
) : RecyclerView.Adapter<ExercisesAdapter.ExerciseViewHolder>() {

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvExerciseTitle: TextView = itemView.findViewById(R.id.tvExerciseTitle)
        private val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)

        fun bind(exercise: Exercise) {
            tvExerciseTitle.text = exercise.title
            tvPoints.text = "${exercise.points} pts"
            itemView.setOnClickListener { onExerciseClick(exercise) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(exercises[position])
    }

    override fun getItemCount(): Int = exercises.size
}
