package com.sosiso4kawo.zschoolapp.data.model

data class CourseProgress(
    val uuid: String,
    val course_uuid: String,
    val total_points: Int,
    val completed_at: String
)

data class ExerciseProgress(
    val uuid: String,
    val exercise_uuid: String,
    val total_points: Int,
    val completed_at: String
)

data class LessonProgress(
    val uuid: String,
    val lesson_uuid: String,
    val total_points: Int,
    val completed_at: String
)

data class ProgressResponse(
    val courses: List<CourseProgress>,
    val exercises: List<ExerciseProgress>,
    val lessons: List<LessonProgress>
)
