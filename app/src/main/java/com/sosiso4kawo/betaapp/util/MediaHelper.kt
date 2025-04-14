@file:Suppress("DEPRECATION")

package com.sosiso4kawo.betaapp.util

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import com.sosiso4kawo.betaapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
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
        val fullScreenImageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(context)
            .load(imageUrl)
            .into(fullScreenImageView)
        fullScreenImageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(fullScreenImageView)
        dialog.show()
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    fun showPdfViewer(context: Context, pdfUrl: String, lifecycleScope: LifecycleCoroutineScope) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pdf_viewer, null)
        val pdfImageView = view.findViewById<ImageView>(R.id.pdfImageView)
        val closeButton = view.findViewById<ImageView>(R.id.ivClosePdf)
        val prevButton = view.findViewById<Button>(R.id.btnPrevPage)
        val nextButton = view.findViewById<Button>(R.id.btnNextPage)
        val pageNumberText = view.findViewById<TextView>(R.id.tvPageNumber)

        closeButton.setOnClickListener { dialog.dismiss() }

        lifecycleScope.launch {
            var currentPage = 0
            var pdfiumCore: PdfiumCore? = null
            var pdfDocument: PdfDocument? = null
            try {
                val pdfBytes = withContext(Dispatchers.IO) {
                    val url = URL(pdfUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.inputStream.use { it.readBytes() }
                }
                pdfiumCore = PdfiumCore(context)
                pdfDocument = pdfiumCore.newDocument(pdfBytes)
                val pageCount = pdfiumCore.getPageCount(pdfDocument)
                pageNumberText.text = "Страница ${currentPage + 1} из $pageCount"

                prevButton.isEnabled = false
                nextButton.isEnabled = pageCount > 1

                fun renderPage() {
                    pdfiumCore.let { core ->
                        pdfDocument?.let { document ->
                            try {
                                core.openPage(document, currentPage)
                                val width = core.getPageWidthPoint(document, currentPage)
                                val height = core.getPageHeightPoint(document, currentPage)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                core.renderPageBitmap(document, bitmap, currentPage, 0, 0, width, height)
                                pdfImageView.setImageBitmap(bitmap)
                                pageNumberText.text = "Страница ${currentPage + 1} из $pageCount"
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка отображения PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                prevButton.setOnClickListener {
                    if (currentPage > 0) {
                        currentPage--
                        renderPage()
                        nextButton.isEnabled = true
                        prevButton.isEnabled = currentPage > 0
                    }
                }

                nextButton.setOnClickListener {
                    if (currentPage < pageCount - 1) {
                        currentPage++
                        renderPage()
                        prevButton.isEnabled = true
                        nextButton.isEnabled = currentPage < pageCount - 1
                    }
                }

                renderPage()

            } catch (e: IOException) {
                Toast.makeText(context, "Ошибка загрузки PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }

            dialog.setOnDismissListener {
                pdfiumCore?.closeDocument(pdfDocument)
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    @SuppressLint("InflateParams")
    fun showVideoPlayer(context: Context, videoUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_video_player, null)
        val playerView = view.findViewById<PlayerView>(R.id.videoPlayer)
        val closeButton = view.findViewById<ImageView>(R.id.ivCloseVideo)
        closeButton.setOnClickListener { dialog.dismiss() }

        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            playWhenReady = true
            prepare()
        }
        playerView.player = exoPlayer

        dialog.setOnDismissListener { exoPlayer.release() }
        dialog.setContentView(view)
        dialog.show()
    }
}

fun Context.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
    ).toInt()
}
