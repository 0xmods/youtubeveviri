package com.example.youtubeceviri

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class SubtitleOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var textView: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideSubtitle() }

    private var isAttached = false
    private var lastShownText: String? = null

    fun initOverlay() {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        if (overlayView != null) return

        val container = FrameLayout(context)

        // Şık, kompakt TV Altyazı Çerçevesi
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * context.resources.displayMetrics.density
            setColor(Color.parseColor("#E6121212")) // %90 şeffaf koyu zemin
            setStroke(
                (2f * context.resources.displayMetrics.density).toInt(),
                Color.parseColor("#FFD700") // Altın sarısı çerçeve
            )
        }
        container.background = backgroundDrawable

        val density = context.resources.displayMetrics.density
        val paddingHorizontal = (16 * density).toInt()
        val paddingVertical = (8 * density).toInt()
        container.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        // Altyazı Metin Alanı
        val tv = TextView(context).apply {
            setTextColor(Color.parseColor("#FFFF00")) // Canlı Sarı altyazı rengi
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f) // Kompakt TV font boyutu
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
            // Uzun cümleler için 3 satıra izin ver
            maxLines = 3
            // Gerekirse sağa sola uzayabilsin: ekran genişliğinin %92'sine kadar
            maxWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        }
        container.addView(tv)
        this.textView = tv

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // Altyazı bandının (ekranın ~%78-94'ü) TAM ÜSTÜNE yerleş: OCR bölgesiyle
            // hiç kesişmez, ekran görüntüsü sırasında gizlemeye gerek kalmaz (titreme yok).
            // Uzayan cümleler yukarı doğru büyür, asla altyazı bandına inmez.
            y = (context.resources.displayMetrics.heightPixels * 0.22f).toInt()
        }

        container.visibility = View.GONE
        this.overlayView = container

        try {
            windowManager.addView(container, params)
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showSubtitle(text: String) {
        if (text.isBlank()) return

        handler.post {
            if (!isAttached || overlayView == null) {
                initOverlay()
            }

            // Aynı metni tekrar tekrar basma (titreme önlenir)
            if (text == lastShownText && overlayView?.visibility == View.VISIBLE) {
                return@post
            }
            lastShownText = text

            textView?.text = text

            // Metin uzunluğuna göre font boyutunu ayarla: kısa metin büyük, uzun
            // metin kompakt kalır; çerçeve metne göre sağa sola esner
            textView?.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                when {
                    text.length <= 40 -> 22f
                    text.length <= 80 -> 19f
                    else -> 16f
                }
            )

            overlayView?.visibility = View.VISIBLE

            // 5 saniye sonra altyazıyı otomatik gizle (uzun cümleler için daha fazla süre)
            val hideDelayMs = if (text.length > 60) 6000L else 4000L
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, hideDelayMs)
        }
    }

    fun hideSubtitle() {
        handler.post {
            overlayView?.visibility = View.GONE
        }
    }

    /**
     * Overlay artık altyazı bandının üstünde olduğu için tarama sırasında gizlemeye
     * gerek yok; OCR kendi çerçevesini okuyamaz. Metotlar geriye uyumluluk için korunuyor.
     */
    fun hideForScan() { /* no-op */ }

    fun restoreFromScan() { /* no-op */ }

    fun removeOverlay() {
        handler.post {
            handler.removeCallbacks(hideRunnable)
            if (isAttached && overlayView != null) {
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                overlayView = null
                textView = null
                isAttached = false
            }
        }
    }
}
