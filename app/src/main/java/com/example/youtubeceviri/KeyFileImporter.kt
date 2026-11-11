package com.example.youtubeceviri

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * api_keys.txt dosyasını bilinen konumlarda otomatik arar ve bulunursa
 * anahtarları sessizce içe aktarır. Uygulama her açıldığında çağrılır:
 * kullanıcı USB'ye/dahili depolamaya yeni dosya koyarsa yenisi devreye girer.
 *
 * Taranan konumlar:
 * - Uygulamaya özel klasör: Android/data/com.example.youtubeceviri/files/
 *   (HERHANGİ BİR İZNE GEREK YOK — adb push ile dosya koymak için garantili yol)
 * - Dahili depolama kökü + /Download
 * - /storage altındaki tüm birimler (USB bellekler) + /Download
 *
 * Not: Android 13+ cihazlarda paylaşılan depolamayı okumak için "Tüm dosyalara
 * erişim" izni gerekir; verilene kadar yalnızca uygulamaya özel klasör taranır.
 */
object KeyFileImporter {

    private const val TAG = "KeyFileImporter"
    val CANDIDATE_NAMES = setOf("api_keys.txt", "livesub_keys.txt", "keys.txt")

    /**
     * @return true ise dosya bulundu ve anahtarlar uygulandı (değişiklik oldu).
     */
    fun autoImport(context: Context): Boolean {
        val searchPaths = buildSearchPaths(context)

        for (file in searchPaths) {
            if (!file.isFile) continue
            val content = try {
                file.readText()
            } catch (e: Exception) {
                Log.w(TAG, "${file.absolutePath} okunamadı: ${e.message}")
                continue
            }
            if (applyContent(content)) {
                Log.i(TAG, "Anahtarlar otomatik yüklendi: ${file.absolutePath}")
                return true
            }
        }
        return false
    }

    private fun buildSearchPaths(context: Context): List<File> {
        val paths = mutableListOf<File>()

        // 1. Uygulamaya özel klasör — izne gerek yok, her zaman okunabilir
        context.getExternalFilesDir(null)?.let { appDir ->
            for (name in CANDIDATE_NAMES) paths += File(appDir, name)
        }

        // 2. Dahili depolama kökü + Download
        val root = Environment.getExternalStorageDirectory() ?: File("/sdcard")
        for (name in CANDIDATE_NAMES) {
            paths += File(root, name)
            paths += File(root, "Download/$name")
        }

        // 3. USB birimleri (/storage/XXXX-XXXX) + Download
        try {
            File("/storage").listFiles()?.forEach { volume ->
                for (name in CANDIDATE_NAMES) {
                    paths += File(volume, name)
                    paths += File(volume, "Download/$name")
                }
            }
        } catch (_: Exception) {
        }

        return paths
    }

    private fun applyContent(content: String): Boolean {
        if (content.isBlank()) return false

        var foundAny = false
        var gemini: String? = null
        var nvidia: String? = null

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim().uppercase()
            val value = line.substring(eq + 1).trim()
            if (value.isEmpty()) continue
            when (key) {
                "GEMINI_API_KEY", "GEMINI", "GOOGLE_API_KEY" -> { gemini = value; foundAny = true }
                "NVIDIA_API_KEY", "NVIDIA", "NIM_API_KEY" -> { nvidia = value; foundAny = true }
            }
        }

        if (!foundAny) return false

        if (gemini != null) ApiKeyManager.saveGeminiKey(gemini)
        if (nvidia != null) ApiKeyManager.saveNvidiaKey(nvidia)
        GeminiTranslationService.clearCache()
        NvidiaTranslationService.clearCache()
        return true
    }
}
