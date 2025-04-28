@file:Suppress("DEPRECATION")

package com.sosiso4kawo.zschoolapp.util

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.sosiso4kawo.zschoolapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable
import android.util.Log

object MediaHelper {

    private fun createGlideListener(url: String?): RequestListener<Drawable> {
        return object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Log.e("GlideError", "Failed to load fullscreen image: $url", e)
                return false // Позволить Glide показать .error()
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: com.bumptech.glide.load.DataSource,
                isFirstResource: Boolean
            ): Boolean {
                return false // Позволить Glide показать изображение
            }
        }
    }

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
        val photoView = PhotoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            maximumScale = 5f
            minimumScale = 1f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        Log.d("GlideLoad", "Loading fullscreen image: $imageUrl") // Лог перед загрузкой

        Glide.with(context)
            .load(imageUrl)
            // Можете использовать другой плейсхолдер для ошибки полноэкранного режима, если хотите
            .error(R.drawable.ic_image_placeholder) // <<< Убедитесь, что такой drawable есть
            .listener(createGlideListener(imageUrl)) // <<< Добавлено: листенер для логирования
            .into(photoView)

        photoView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(photoView)
        dialog.show()
    }

    @SuppressLint("InflateParams", "SetTextI18n", "ClickableViewAccessibility")
    fun showPdfViewer(
        context: Context,
        pdfUrl: String,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pdf_viewer, null)
        val pdfView = view.findViewById<PhotoView>(R.id.pdfImageView)
        val closeButton = view.findViewById<ImageView>(R.id.ivClosePdf)
        val prevButton = view.findViewById<Button>(R.id.btnPrevPage)
        val nextButton = view.findViewById<Button>(R.id.btnNextPage)
        val pageNumberTxt = view.findViewById<TextView>(R.id.tvPageNumber)

        closeButton.setOnClickListener { dialog.dismiss() }

        lifecycleScope.launch {
            // 1) Скачиваем PDF в кэш
            val pdfFile = withContext(Dispatchers.IO) {
                val bytes = URL(pdfUrl).openStream().use { it.readBytes() }
                File(context.cacheDir, "temp.pdf").apply { writeBytes(bytes) }
            }

            // 2) Пытаемся открыть PdfRenderer — бросаем исключение сразу для теста
            val pfd: ParcelFileDescriptor?
            val renderer: PdfRenderer?
            val pageCount: Int
            var currentPage = 0
            try {
                pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Не удалось открыть PDF встроенным способом, запускаем внешний просмотр",
                    Toast.LENGTH_LONG
                ).show()
                openExternalPdf(context, pdfFile)
                return@launch
            }

            fun renderPage() {
                renderer.openPage(currentPage).use { page ->
                    val metrics = context.resources.displayMetrics
                    val width = metrics.widthPixels
                    val aspect = page.height.toFloat() / page.width
                    val height = (width * aspect).toInt()
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    pdfView.apply {
                        setBackgroundColor(Color.WHITE)
                        setImageBitmap(bmp)
                        minimumScale = 1f
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    pageNumberTxt.text = "Стр. ${currentPage + 1} из $pageCount"
                }
            }

            prevButton.setOnClickListener {
                if (currentPage > 0) {
                    currentPage--
                    renderPage()
                }
            }
            nextButton.setOnClickListener {
                if (currentPage < pageCount - 1) {
                    currentPage++
                    renderPage()
                }
            }

            // показываем первую страницу
            renderPage()

            dialog.setContentView(view)
            dialog.setOnDismissListener {
                renderer.close()
                pfd?.close()
                pdfFile.delete()
            }
            dialog.show()
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun showVideoPlayer(context: Context, videoUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_video_player, null)
        val playerView = view.findViewById<PlayerView>(R.id.videoPlayer)
        val closeButton = view.findViewById<ImageView>(R.id.ivCloseVideo)
        closeButton.setOnClickListener { dialog.dismiss() }

        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            playWhenReady = true
            prepare()
        }
        playerView.player = exo

        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scale = (playerView.scaleX * detector.scaleFactor).coerceIn(0.5f, 3f)
                    playerView.scaleX = scale
                    playerView.scaleY = scale
                    return true
                }
            }
        )
        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }

        dialog.setOnDismissListener { exo.release() }
        dialog.setContentView(view)
        dialog.show()
    }
}

/** Открывает PDF через внешнее приложение */
/**
 * Открывает PDF через внешний просмотрщик, показывая окно выбора приложения.
 */
@SuppressLint("QueryPermissionsNeeded")
private fun openExternalPdf(context: Context, pdfFile: File) {
    try {
        // 1) Проверяем, что файл действительно в кэше
        if (!pdfFile.exists()) {
            throw IllegalStateException("PDF-файл не найден: ${pdfFile.absolutePath}")
        }

        // 2) Получаем защищённый URI
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )

        // 3) Создаём Intent для отправки
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            // Добавляем ClipData — важно для некоторых приложений
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 4) Раздаём URI-permission всем, кто сможет его обработать
        val pm = context.packageManager
        val resList = pm.queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)
        resList.forEach { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 5) Оборачиваем в chooser
        val chooser = Intent.createChooser(sendIntent, "Открыть или поделиться PDF через…")
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        // 6) Запускаем
        context.startActivity(chooser)

    } catch (ex: Exception) {
        Toast.makeText(
            context,
            "Не удалось передать PDF: ${ex.localizedMessage}",
            Toast.LENGTH_LONG
        ).show()
        ex.printStackTrace()
    }
}


fun Context.dpToPx(dp: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
