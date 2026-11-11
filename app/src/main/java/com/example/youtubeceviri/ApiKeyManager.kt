package com.example.youtubeceviri

import android.content.Context
import android.content.SharedPreferences

/**
 * API anahtarlarını yönetir.
 * Kullanıcı ayarlardan kendi anahtarını girdiyse onu kullanır,
 * girmediyse derleme sırasında APK'ya gömülen (BuildConfig) anahtarı kullanır.
 */
object ApiKeyManager {

    private const val PREFS = "api_keys"
    private const val KEY_GEMINI = "gemini_api_key"
    private const val KEY_NVIDIA = "nvidia_api_key"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                }
            }
        }
    }

    /** Gemini anahtarı: kullanıcı anahtarı > gömülü anahtar sırasıyla döner. */
    fun getGeminiKey(): String {
        val custom = prefs?.getString(KEY_GEMINI, "")?.trim().orEmpty()
        return custom.ifEmpty { BuildConfig.GEMINI_API_KEY }
    }

    /** NVIDIA anahtarı: kullanıcı anahtarı > gömülü anahtar sırasıyla döner. */
    fun getNvidiaKey(): String {
        val custom = prefs?.getString(KEY_NVIDIA, "")?.trim().orEmpty()
        return custom.ifEmpty { BuildConfig.NVIDIA_API_KEY }
    }

    fun hasCustomGemini(): Boolean =
        prefs?.getString(KEY_GEMINI, "")?.trim().orEmpty().isNotEmpty()

    fun hasCustomNvidia(): Boolean =
        prefs?.getString(KEY_NVIDIA, "")?.trim().orEmpty().isNotEmpty()

    fun saveGeminiKey(key: String) {
        prefs?.edit()?.apply {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) remove(KEY_GEMINI) else putString(KEY_GEMINI, trimmed)
            apply()
        }
    }

    fun saveNvidiaKey(key: String) {
        prefs?.edit()?.apply {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) remove(KEY_NVIDIA) else putString(KEY_NVIDIA, trimmed)
            apply()
        }
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
