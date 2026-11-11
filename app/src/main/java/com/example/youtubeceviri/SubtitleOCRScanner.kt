package com.example.youtubeceviri

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object SubtitleOCRScanner {

    private const val TAG = "SubtitleOCRScanner"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scanSubtitleFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height

            // TV'de ölçülen gerçek altyazı bandı: ekran yüksekliğinin %78-94'ü.
            // (1280x720 test TV'de metin satırları y=620-676 aralığında bulundu)
            // Overlay en altta olduğu için bu bölgeye karışmaz.
            val startY = (height * 0.78).toInt()
            val cropHeight = (height * 0.165).toInt()
            val cropWidth = (width * 0.90).toInt()
            val startX = (width * 0.05).toInt()

            if (startX + cropWidth <= width && startY + cropHeight <= height && cropWidth > 0 && cropHeight > 0) {
                // Hardware bitmap bazı cihazlarda okunamaz; başarısız olursa yazılım kopyasına düş
                var croppedBitmap: Bitmap = try {
                    Bitmap.createBitmap(bitmap, startX, startY, cropWidth, cropHeight)
                } catch (e: Exception) {
                    val softwareCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    try {
                        Bitmap.createBitmap(softwareCopy, startX, startY, cropWidth, cropHeight)
                    } finally {
                        softwareCopy.recycle()
                    }
                }

                // OCR doğruluğu için küçültmeyi sınırlı tut: altyazı metni küçük
                // olduğu için agresif küçültme yanlış okumaya yol açar
                val maxOcrWidth = 960
                if (croppedBitmap.width > maxOcrWidth) {
                    val scale = maxOcrWidth.toFloat() / croppedBitmap.width
                    val scaled = Bitmap.createScaledBitmap(
                        croppedBitmap,
                        maxOcrWidth,
                        (croppedBitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled != croppedBitmap) croppedBitmap.recycle()
                    croppedBitmap = scaled
                }

                val image = InputImage.fromBitmap(croppedBitmap, 0)

                return@withContext suspendCancellableCoroutine { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val detectedText = visionText.text.trim()
                            val cleanedSubtitle = cleanDetectedSubtitleText(detectedText)
                            if (continuation.isActive) {
                                continuation.resume(cleanedSubtitle)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "ML Kit OCR Hatası: ${e.message}")
                            if (continuation.isActive) {
                                continuation.resume("")
                            }
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kırpma Hatası: ${e.message}", e)
        }
        return@withContext ""
    }

    private fun cleanDetectedSubtitleText(rawText: String): String {
        if (rawText.isBlank()) return ""

        val cleaned = rawText
            .replace(Regex("""\[.*?\]"""), "") // [music] vb. temizle
            .replace(Regex("""\(.*?\)"""), "") // (applause) vb. temizle
            .replace(">>", "")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.length < 3) return ""
        if (cleaned.matches(Regex("""^\d{1,2}:\d{2}.*"""))) return ""
        if (cleaned.equals("YouTube", ignoreCase = true)) return ""

        // Büyük/küçük harf karmaşasını önlemek için düzgün cümle formatına sok
        return normalizeCasing(cleaned)
    }

    private fun normalizeCasing(text: String): String {
        if (text.isBlank()) return text
        // İlk harfi büyük, geri kalanları doğal akışa bırak
        val lower = text.lowercase()
        return lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
