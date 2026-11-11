package com.example.youtubeceviri

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 1. seviye çeviri: Gemini API.
 * Başarısız olursa null döner; zincir bir sonraki servise (NVIDIA) geçer.
 */
object GeminiTranslationService {

    private const val TAG = "GeminiService"
    private val API_KEY get() = ApiKeyManager.getGeminiKey()
    // Flash-Lite: daha yüksek ücretsiz kota limiti + daha düşük gecikme (canlı altyazı için ideal)
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val translationCache = ConcurrentHashMap<String, String>()

    suspend fun translateToTurkish(englishText: String): String? = withContext(Dispatchers.IO) {
        val trimmedInput = englishText.trim()
        if (trimmedInput.isEmpty()) return@withContext null

        translationCache[trimmedInput]?.let { return@withContext it }

        try {
            val prompt = "Translate this English subtitle to natural, fluent Turkish. Output ONLY the Turkish translation, nothing else:\n\n$trimmedInput"

            val partObj = JsonObject().apply { addProperty("text", prompt) }
            val partsArray = JsonArray().apply { add(partObj) }
            val contentObj = JsonObject().apply { add("parts", partsArray) }
            val contentsArray = JsonArray().apply { add(contentObj) }
            val configObj = JsonObject().apply {
                // Gemini 3.x'te "thinking" varsayılan açıktır ve thinking tokenları
                // maxOutputTokens'a dahildir. Minimal thinking + yeterli token limiti
                // olmadan yanıt kesilir / boş döner.
                add("thinkingConfig", JsonObject().apply {
                    addProperty("thinkingLevel", "minimal")
                })
                addProperty("maxOutputTokens", 256)
            }

            val requestJson = JsonObject().apply {
                add("contents", contentsArray)
                add("generationConfig", configObj)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", API_KEY)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val bodyString = resp.body?.string() ?: ""
                if (resp.code == 429) {
                    Log.w(TAG, "Gemini kotası doldu (429).")
                    delay(300)
                    return@use
                } else if (!resp.isSuccessful) {
                    Log.w(TAG, "Gemini API hatası kod=${resp.code}")
                    return@use
                }
                if (bodyString.isBlank()) return@use

                val root = JsonParser.parseString(bodyString).asJsonObject
                val candidates = root.getAsJsonArray("candidates")
                if (candidates == null || candidates.size() == 0) {
                    Log.w(TAG, "Gemini yanıtında candidates yok: ${bodyString.take(300)}")
                    return@use
                }
                val candidateObj = candidates[0].asJsonObject
                val content = candidateObj.getAsJsonObject("content")
                if (content == null) {
                    Log.w(TAG, "Gemini candidate content yok, finishReason=${candidateObj.get("finishReason")}")
                    return@use
                }
                val parts = content.getAsJsonArray("parts")
                if (parts == null || parts.size() == 0) {
                    Log.w(TAG, "Gemini content içinde parts yok: ${content}")
                    return@use
                }

                // thoughtSignature içeren part'ları atla, metin part'larını birleştir
                val textBuilder = StringBuilder()
                for (i in 0 until parts.size()) {
                    val part = parts[i].asJsonObject
                    if (part.has("text") && !part.has("thoughtSignature")) {
                        textBuilder.append(part.get("text").asString)
                    }
                }
                val rawText = textBuilder.toString().trim()
                val translatedText = rawText
                    .replace("**", "")
                    .replace("*", "")
                    .trim()

                // Çıktı sağlama kontrolleri: boş, girdiyle birebir aynı (İngilizce
                // geçişi) veya bozuk karakterli çıktı çeviri sayılmaz
                if (translatedText.isBlank()) return@use
                if (translatedText.contains('\uFFFD')) {
                    Log.w(TAG, "Bozuk karakterli Gemini çıktısı reddedildi: [$translatedText]")
                    return@use
                }
                if (translatedText.equals(trimmedInput, ignoreCase = true)) {
                    Log.w(TAG, "Gemini girdiyi değiştirmeden döndürdü (İngilizce geçişi), reddedildi.")
                    return@use
                }

                translationCache[trimmedInput] = translatedText
                return@withContext translatedText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API hatası: ${e.message}")
        }

        return@withContext null
    }

    fun clearCache() {
        translationCache.clear()
    }
}
