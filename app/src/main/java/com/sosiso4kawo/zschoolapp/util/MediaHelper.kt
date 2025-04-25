@file:Suppress("DEPRECATION")

package com.sosiso4kawo.zschoolapp.util

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.github.chrisbanes.photoview.PhotoView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.sosiso4kawo.zschoolapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object MediaHelper {

    fun createThumbnailImageView(context: Context, placeholderResId: Int): ImageView {
        return ImageView(context).apply {
            adjustViewBounds = true
            maxHeight = context.dpToPx(150)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                context.dpToPx(120),
                context.dpToPx(120)
            ).apply {
                setMargins(8, 8, 8, 8)
            }
            setImageResource(placeholderResId)
        }
    }

    fun showFullScreenImage(context: Context, imageUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        // используем PhotoView вместо ImageView
        val photoView = PhotoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            maximumScale = 5f  // макс zoom
            minimumScale = 1f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(context).load(imageUrl).into(photoView)
        photoView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(photoView)
        dialog.show()
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    fun showPdfViewer(
        context: Context,
        pdfUrl: String,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pdf_viewer, null)
        val pdfView       = view.findViewById<PhotoView>(R.id.pdfImageView)
        val closeButton   = view.findViewById<ImageView>(R.id.ivClosePdf)
        val prevButton    = view.findViewById<Button>(R.id.btnPrevPage)
        val nextButton    = view.findViewById<Button>(R.id.btnNextPage)
        val pageNumberTxt = view.findViewById<TextView>(R.id.tvPageNumber)
        closeButton.setOnClickListener { dialog.dismiss() }

        lifecycleScope.launch {
            // скачиваем и открываем PDF, как раньше…
            val pdfFile: File = withContext(Dispatchers.IO) {
                val bytes = URL(pdfUrl).openStream().use { it.readBytes() }
                val file = File(context.cacheDir, "temp.pdf")
                file.writeBytes(bytes)
                file
            }
            val pfd      = ParcelFileDescriptor.open(pdfFile, MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            var currentPage = 0
            val pageCount   = renderer.pageCount

            fun renderPage() {
                renderer.openPage(currentPage).use { page ->
                    val metrics = context.resources.displayMetrics

                    // 1) Ширина экрана в пикселях
                    val screenWidth = metrics.widthPixels

                    // 2) Соотношение сторон PDF-страницы (ширина/высота)
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()

                    // 3) Рассчитываем размер Bitmap: ширина = экран, высота = по аспекту
                    val width  = screenWidth
                    val height = (screenWidth * aspectRatio).toInt()

                    // 4) Создаём Bitmap и рендерим в режиме "для печати"
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    // 5) Ставим белый фон и показываем
                    pdfView.apply {
                        setBackgroundColor(Color.WHITE)
                        setImageBitmap(bmp)
                        // Чтобы по умолчанию вместилось по ширине
                        minimumScale = 1f
                        scaleType   = ImageView.ScaleType.FIT_CENTER
                        // Можно сразу подогнать scale, но у PhotoView по умолчанию FIT_CENTER = scale-to-fit
                    }

                    pageNumberTxt.text = "Стр. ${currentPage + 1} из $pageCount"
                }
            }

            prevButton.setOnClickListener {
                if (currentPage>0) { currentPage--; renderPage() }
            }
            nextButton.setOnClickListener {
                if (currentPage<pageCount-1) { currentPage++; renderPage() }
            }

            renderPage()
            dialog.setOnDismissListener {
                renderer.close(); pfd.close(); pdfFile.delete()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun showVideoPlayer(context: Context, videoUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_video_player, null)
        val playerView = view.findViewById<PlayerView>(R.id.videoPlayer)
        val closeButton = view.findViewById<ImageView>(R.id.ivCloseVideo)
        closeButton.setOnClickListener { dialog.dismiss() }

        // 1) устанавливаем плеер
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            playWhenReady = true; prepare()
        }
        playerView.player = exo

        // 2) ScaleGestureDetector для зума
        val scaleDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    // ограничиваем масштабы
                    val scale = (playerView.scaleX * detector.scaleFactor).coerceIn(0.5f, 3f)
                    playerView.scaleX = scale
                    playerView.scaleY = scale
                    return true
                }
            }
        )
        // 3) ловим тач на корневом вью
        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event); true
        }

        dialog.setOnDismissListener { exo.release() }
        dialog.setContentView(view)
        dialog.show()
    }
}

fun Context.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
    ).toInt()
}
