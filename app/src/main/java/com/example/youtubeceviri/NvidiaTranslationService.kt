package com.example.youtubeceviri

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 2. seviye çeviri: NVIDIA Riva-Translate-4B-Instruct-v2 (build.nvidia.com NIM API).
 * Yerel ML Kit çevirisi KULLANILMAZ (düşük RAM'li TV'lerde cihazı kastığı için).
 * Başarısız olursa null döner.
 */
object NvidiaTranslationService {

    private const val TAG = "RivaTranslate"
    private val NIM_API_KEY get() = ApiKeyManager.getNvidiaKey()
    private const val NIM_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val NIM_MODEL = "nvidia/riva-translate-4b-instruct-v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val translationCache = ConcurrentHashMap<String, String>()

    suspend fun translateToTurkish(englishText: String): String? = withContext(Dispatchers.IO) {
        val trimmedInput = englishText.trim()
        if (trimmedInput.isEmpty()) return@withContext null

        translationCache[trimmedInput]?.let { return@withContext it }

        try {
            val messages = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", "en-tr")
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", trimmedInput)
                })
            }
            val requestJson = JsonObject().apply {
                addProperty("model", NIM_MODEL)
                add("messages", messages)
                addProperty("temperature", 0.0)
                addProperty("max_tokens", 512)
                addProperty("stream", false)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(NIM_URL)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $NIM_API_KEY")
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val bodyString = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.w(TAG, "NVIDIA NIM hatası kod=${resp.code}")
                    return@use null
                }
                if (bodyString.isBlank()) return@use null

                val root = JsonParser.parseString(bodyString).asJsonObject
                val choices = root.getAsJsonArray("choices")
                if (choices == null || choices.size() == 0) return@use null
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                if (message == null) return@use null
                val contentElem = message.get("content")
                if (contentElem == null || contentElem.isJsonNull) return@use null

                val translated = contentElem.asString.trim()
                if (translated.isBlank()) return@use null
                if (translated.contains('\uFFFD')) {
                    Log.w(TAG, "Bozuk karakterli NIM çıktısı reddedildi: [$translated]")
                    return@use null
                }
                if (translated.equals(trimmedInput, ignoreCase = true)) {
                    Log.w(TAG, "NIM girdiyi değiştirmeden döndürdü, reddedildi.")
                    return@use null
                }

                if (translationCache.size > 300) translationCache.clear()
                translationCache[trimmedInput] = translated
                return@withContext translated
            }
        } catch (e: Exception) {
            Log.w(TAG, "NVIDIA NIM çağrısı başarısız: ${e.message}")
        }

        return@withContext null
    }

    fun clearCache() {
        translationCache.clear()
    }
}
