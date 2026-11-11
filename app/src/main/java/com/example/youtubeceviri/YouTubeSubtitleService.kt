package com.example.youtubeceviri

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class YouTubeSubtitleService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayManager: SubtitleOverlayManager? = null

    private var currentAccumulatedText = ""
    private var lastProcessedText = ""
    private var lastDetectionTime = 0L
    private var isScanning = false

    companion object {
        private const val TAG = "YouTubeSubtitle"
        var isServiceRunning: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "YouTubeSubtitleService bağlandı, akıllı cümle yöneticisi aktif.")

        TranslationManager.init(this)

        overlayManager = SubtitleOverlayManager(this).apply {
            initOverlay()
        }

        startScreenCaptureLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Ekran taraması AccessibilityService.takeScreenshot ile yapılıyor.
        // NOT: TV'de YouTube tam ekran oynatmada pencere olayları gelmediği için
        // ön plan kontrolü kullanılmıyor; maliyet kontrolü uyarlanabilir gecikme
        // ve daraltılmış OCR bölgesiyle yapılıyor.
    }

    private fun startScreenCaptureLoop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Android 11 (API 30) ve üzeri gereklidir.")
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            while (isServiceRunning) {
                try {
                    // Ekran boşken tembel modda taramaya devam et (döngüyü asla durdurma:
                    // YouTube tam ekranda pencere olayı göndermiyor)
                    if (!isScanning) {
                        isScanning = true
                        // Ekran görüntüsü sırasında overlay gizle (OCR kendi çerçevesini okumasın)
                        overlayManager?.hideForScan()
                        takeScreenshot(
                            Display.DEFAULT_DISPLAY,
                            mainExecutor,
                            object : TakeScreenshotCallback {
                                override fun onSuccess(result: ScreenshotResult) {
                                    try {
                                        val hardwareBuffer = result.hardwareBuffer
                                        val colorSpace = result.colorSpace
                                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)

                                        if (bitmap != null) {
                                            serviceScope.launch(Dispatchers.Default) {
                                                try {
                                                    // Görüntü alındı, overlay'i hemen geri getir
                                                    overlayManager?.restoreFromScan()
                                                    val detectedSubtitle = SubtitleOCRScanner.scanSubtitleFromBitmap(bitmap)
                                                    handleDetectedText(detectedSubtitle)
                                                    bitmap.recycle()
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "OCR/çeviri hatası: ${e.message}")
                                                } finally {
                                                    isScanning = false
                                                }
                                            }
                                        } else {
                                            overlayManager?.restoreFromScan()
                                            isScanning = false
                                        }
                                        hardwareBuffer.close()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Screenshot işleme hatası: ${e.message}")
                                        isScanning = false
                                    }
                                }

                                override fun onFailure(errorCode: Int) {
                                    Log.e(TAG, "Screenshot alınamadı, hata kodu: $errorCode")
                                    overlayManager?.restoreFromScan()
                                    isScanning = false
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OCR Loop Hatası: ${e.message}")
                    isScanning = false
                }
                // Uyarlanabilir aralık: altyazı görünürken hızlı, boş ekranDAYken tembel
                // (düşük RAM'li TV'lerde sürekli OCR cihazı dondurur)
                delay(if (currentAccumulatedText.isNotBlank()) 800L else 2500L)
            }
        }
    }

    private suspend fun handleDetectedText(newText: String) {
        val trimmed = newText.trim()
        val now = System.currentTimeMillis()

        if (trimmed.length < 3) return

        // Eğer yeni metin önceki ile benzer veya devamı niteliğindeyse biriktir
        val isContinuation = currentAccumulatedText.isNotBlank() &&
                (trimmed.contains(currentAccumulatedText, ignoreCase = true) ||
                        currentAccumulatedText.contains(trimmed, ignoreCase = true) ||
                        computeSimilarity(trimmed, currentAccumulatedText) > 0.5)

        if (isContinuation) {
            if (trimmed.length >= currentAccumulatedText.length) {
                currentAccumulatedText = trimmed
            }
            lastDetectionTime = now
        } else {
            // Yeni bir altyazı cümlesine geçildi! Önceki birikimi hemen çevir
            flushAccumulated()
            // Yeni cümleyi başlat
            currentAccumulatedText = trimmed
            lastProcessedText = "" // Yeni cümle için kilidi sıfırla
            lastDetectionTime = now
        }

        val isSentenceComplete = endsWithSentenceBreak(currentAccumulatedText)

        // 1) Cümle noktalama işaretiyle (. ! ? , ; :) tamamlandıysa ve anlamlı bir
        //    uzunluğa ulaştıysa hemen çevir (cümle oluşmadan çeviri yapılmaz)
        if (currentAccumulatedText.isNotBlank() && isSentenceComplete &&
            currentAccumulatedText.length >= 12 && currentAccumulatedText != lastProcessedText) {
            translateAndShow(currentAccumulatedText)
        }
        // 2) Noktalama hiç gelmediyse ve 1.2 saniyedir yeni karakter eklenmediyse,
        //    birikmiş metni çevir (altyazılarda noktalama sık eksik olur)
        else if (currentAccumulatedText.isNotBlank() &&
            (now - lastDetectionTime) > 1200 && currentAccumulatedText != lastProcessedText) {
            translateAndShow(currentAccumulatedText)
        }
    }

    /** Metin bir cümle sonu (veya virgül) işaretiyle bitiyor mu? */
    private fun endsWithSentenceBreak(text: String): Boolean {
        val t = text.trimEnd()
        if (t.isEmpty()) return false
        return t.last() in ".!?,:;"
    }

    /** Birikmiş cümleyi, daha önce çevrilmemişse çeviriye gönder. */
    private suspend fun flushAccumulated() {
        if (currentAccumulatedText.isNotBlank() && currentAccumulatedText != lastProcessedText) {
            translateAndShow(currentAccumulatedText)
        }
    }

    private fun computeSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.contains(shorter)) return 0.8
        return 0.3
    }

    private suspend fun translateAndShow(textToTranslate: String) {
        if (textToTranslate.isBlank() || textToTranslate == lastProcessedText) return

        lastProcessedText = textToTranslate
        Log.d(TAG, "Çeviriye Gönderilen Cümle: [$textToTranslate]")

        val turkishTranslation = TranslationManager.translateToTurkish(textToTranslate)
        Log.d(TAG, "Türkçe Çeviri: [$turkishTranslation]")

        // Tüm sağlayıcılar başarısız olduysa son gösterilen çeviriyi koru (İngilizce
        // metin ekrana geçmesin)
        if (!turkishTranslation.isNullOrBlank()) {
            overlayManager?.showSubtitle(turkishTranslation)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "YouTubeSubtitleService kesintiye uğradı.")
    }

    /** Servisi durdurmak için PUBLIC fonksiyon */
    fun forceStop() {
        isServiceRunning = false
        overlayManager?.removeOverlay()
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "YouTubeSubtitleService durduruldu.")
        overlayManager?.removeOverlay()
        serviceScope.cancel()
    }
}
