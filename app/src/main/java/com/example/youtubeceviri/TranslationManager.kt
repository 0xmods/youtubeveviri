package com.example.youtubeceviri

import android.content.Context
import android.util.Log

/**
 * Çeviri zinciri: Gemini (1. seviye) -> NVIDIA Riva NIM (2. seviye).
 * Yerel ML Kit çevirisi kullanılmaz (TV'yi kastığı için).
 * Tüm seviyeler başarısız olursa null döner; arayüzdeki son çeviri korunur.
 */
object TranslationManager {

    private const val TAG = "TranslationManager"

    fun init(context: Context) {
        ApiKeyManager.init(context)
    }

    suspend fun translateToTurkish(englishText: String): String? {
        val trimmed = englishText.trim()
        if (trimmed.isEmpty()) return null

        GeminiTranslationService.translateToTurkish(trimmed)?.let {
            Log.d(TAG, "Kaynak: Gemini")
            return it
        }

        NvidiaTranslationService.translateToTurkish(trimmed)?.let {
            Log.d(TAG, "Kaynak: NVIDIA Riva")
            return it
        }

        Log.w(TAG, "Tüm çeviri sağlayıcıları başarısız oldu.")
        return null
    }
}
