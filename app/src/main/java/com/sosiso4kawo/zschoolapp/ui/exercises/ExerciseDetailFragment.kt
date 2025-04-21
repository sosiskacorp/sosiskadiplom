package com.sosiso4kawo.zschoolapp.ui.exercises

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.sosiso4kawo.zschoolapp.R
import com.sosiso4kawo.zschoolapp.data.api.ExercisesService
import com.sosiso4kawo.zschoolapp.data.model.Exercise
import com.sosiso4kawo.zschoolapp.data.model.ExerciseFile
import com.sosiso4kawo.zschoolapp.util.MediaHelper
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@Suppress("DEPRECATION")
class ExerciseDetailFragment : Fragment() {

    private val exercisesService: ExercisesService by inject()

    // Аргументы, передаваемые фрагменту
    private var exerciseUuid: String? = null
    private var lessonUuid: String? = null
    private var isSingleExercise: Boolean = false

    // UI элементы
    private lateinit var tvExerciseTitle: TextView
    private lateinit var tvExerciseDescription: TextView
    private lateinit var tvExercisePoints: TextView
    private lateinit var btnLoadQuestions: Button
    private lateinit var mediaScrollView: HorizontalScrollView
    private lateinit var mediaContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Получаем аргументы, передаваемые фрагменту
        exerciseUuid = arguments?.getString("exerciseUuid")
        lessonUuid = arguments?.getString("lessonUuid")
        isSingleExercise = arguments?.getBoolean("isSingleExercise", false) ?: false
        Log.d("ExerciseDetail", "Received lessonUuid: $lessonUuid, exerciseUuid: $exerciseUuid, isSingleExercise: $isSingleExercise")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Раздуваем макет фрагмента
        return inflater.inflate(R.layout.fragment_exercise_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Инициализируем UI элементы
        tvExerciseTitle = view.findViewById(R.id.tvExerciseTitle)
        tvExerciseDescription = view.findViewById(R.id.tvExerciseDescription)
        tvExercisePoints = view.findViewById(R.id.tvExercisePoints)
        btnLoadQuestions = view.findViewById(R.id.btnLoadQuestions)
        mediaScrollView = view.findViewById(R.id.mediaScrollView)
        mediaContainer = view.findViewById(R.id.mediaContainer)

        // Загружаем данные об упражнении
        loadExerciseInfo()

        // Обработчик кнопки для загрузки вопросов
        btnLoadQuestions.setOnClickListener {
            val bundle = Bundle().apply {
                putString("exerciseUuid", exerciseUuid)
                putString("lessonUuid", lessonUuid)
                putBoolean("isSingleExercise", isSingleExercise)
            }
            findNavController().navigate(R.id.exerciseQuestionsFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Если использовался ExoPlayer, MediaHelper уже освобождает ресурсы при закрытии диалога.
    }

    @SuppressLint("SetTextI18n")
    private fun loadExerciseInfo() {
        exerciseUuid?.let { uuid ->
            lifecycleScope.launch {
                val response = exercisesService.getExercise(uuid)
                if (response.isSuccessful) {
                    response.body()?.let { exercise: Exercise ->
                        tvExerciseTitle.text = exercise.title
                        tvExerciseDescription.text = exercise.description.replace("\\n", "\n")
                        tvExercisePoints.text = "${exercise.points} points"

                        // Обработка медиафайлов через MediaHelper
                        handleExerciseMedia(exercise.exerciseFiles)
                    }
                } else {
                    tvExerciseTitle.text = "Ошибка загрузки упражнения"
                }
            }
        }
    }

    // Обработка медиафайлов с учетом количества элементов для выравнивания
    private fun handleExerciseMedia(files: List<ExerciseFile>?) {
        mediaContainer.removeAllViews()

        files?.takeIf { it.isNotEmpty() }?.let { mediaList ->
            // Если медиафайлов только один, выравниваем контейнер по центру, иначе – стандартное выравнивание (слева)
            mediaContainer.gravity = if (mediaList.size == 1) Gravity.CENTER else Gravity.START
            mediaScrollView.visibility = View.VISIBLE

            mediaList.forEach { file ->
                when (getFileType(file.fileUrl)) {
                    FileType.IMAGE -> addImageThumbnail(file.fileUrl)
                    FileType.PDF -> addPdfThumbnail(file.fileUrl)
                    FileType.VIDEO -> addVideoThumbnail(file.fileUrl)
                    FileType.UNKNOWN -> addImageThumbnail(file.fileUrl)
                }
            }
        } ?: run {
            mediaScrollView.visibility = View.GONE
        }
    }

    private enum class FileType { IMAGE, PDF, VIDEO, UNKNOWN }

    // Определение типа файла по расширению URL с учетом регистра
    private fun getFileType(url: String): FileType {
        return when {
            url.endsWith(".jpg", ignoreCase = true) ||
                    url.endsWith(".jpeg", ignoreCase = true) ||
                    url.endsWith(".png", ignoreCase = true) ||
                    url.endsWith(".gif", ignoreCase = true) ||
                    url.endsWith(".webp", ignoreCase = true) -> FileType.IMAGE

            url.endsWith(".pdf", ignoreCase = true) -> FileType.PDF

            url.endsWith(".mp4", ignoreCase = true) ||
                    url.endsWith(".webm", ignoreCase = true) ||
                    url.endsWith(".mov", ignoreCase = true) ||
                    url.endsWith(".avi", ignoreCase = true) ||
                    url.endsWith(".mkv", ignoreCase = true) -> FileType.VIDEO

            else -> FileType.UNKNOWN
        }
    }

    // Добавление миниатюры для изображения с использованием MediaHelper
    private fun addImageThumbnail(imageUrl: String) {
        val imageView = MediaHelper.createThumbnailImageView(requireContext(), R.drawable.ic_image_placeholder)
        Glide.with(this)
            .load(imageUrl)
            .into(imageView)
        imageView.setOnClickListener {
            MediaHelper.showFullScreenImage(requireContext(), imageUrl)
        }
        mediaContainer.addView(imageView)
    }

    // Добавление миниатюры для PDF файла
    private fun addPdfThumbnail(pdfUrl: String) {
        val pdfThumbnail = MediaHelper.createThumbnailImageView(requireContext(), R.drawable.ic_pdf_placeholder)
        pdfThumbnail.setOnClickListener {
            MediaHelper.showPdfViewer(requireContext(), pdfUrl, viewLifecycleOwner.lifecycleScope)
        }
        mediaContainer.addView(pdfThumbnail)
    }

    // Добавление миниатюры для видео
    private fun addVideoThumbnail(videoUrl: String) {
        val videoThumbnail = MediaHelper.createThumbnailImageView(requireContext(), R.drawable.ic_video_placeholder)
        Glide.with(this)
            .load(videoUrl)
            .thumbnail(0.1f)
            .into(videoThumbnail)
        videoThumbnail.setOnClickListener {
            MediaHelper.showVideoPlayer(requireContext(), videoUrl)
        }
        mediaContainer.addView(videoThumbnail)
    }
}
