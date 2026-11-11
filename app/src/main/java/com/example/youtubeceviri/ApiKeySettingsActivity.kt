package com.example.youtubeceviri

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Kullanıcının kendi Gemini / NVIDIA API anahtarlarını girdiği ekran.
 * Girilen anahtarlar SharedPreferences'te saklanır (APK dışına çıkmaz).
 */
class ApiKeySettingsActivity : AppCompatActivity() {

    private lateinit var etGeminiKey: EditText
    private lateinit var etNvidiaKey: EditText
    private lateinit var tvKeysStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_keys)

        ApiKeyManager.init(this)

        etGeminiKey = findViewById(R.id.etGeminiKey)
        etNvidiaKey = findViewById(R.id.etNvidiaKey)
        tvKeysStatus = findViewById(R.id.tvKeysStatus)

        // Mevcut kullanıcı anahtarlarını göster (gömülü anahtarlar gösterilmez)
        if (ApiKeyManager.hasCustomGemini()) {
            etGeminiKey.setText(ApiKeyManager.getGeminiKey())
        }
        if (ApiKeyManager.hasCustomNvidia()) {
            etNvidiaKey.setText(ApiKeyManager.getNvidiaKey())
        }
        updateStatus()

        findViewById<Button>(R.id.btnSaveKeys).setOnClickListener {
            val gemini = etGeminiKey.text.toString().trim()
            val nvidia = etNvidiaKey.text.toString().trim()

            if (gemini.isEmpty() && nvidia.isEmpty()) {
                Toast.makeText(this, "En az bir anahtar girin (Gemini önerilir).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            ApiKeyManager.saveGeminiKey(gemini)
            ApiKeyManager.saveNvidiaKey(nvidia)
            GeminiTranslationService.clearCache()
            NvidiaTranslationService.clearCache()
            updateStatus()
            Toast.makeText(this, "Anahtarlar kaydedildi ✅", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnClearKeys).setOnClickListener {
            etGeminiKey.setText("")
            etNvidiaKey.setText("")
            ApiKeyManager.clearAll()
            updateStatus()
            Toast.makeText(this, "Anahtarlar temizlendi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        val geminiCustom = ApiKeyManager.hasCustomGemini()
        val nvidiaCustom = ApiKeyManager.hasCustomNvidia()
        val embedded = BuildConfig.GEMINI_API_KEY.isNotEmpty()

        val status = when {
            geminiCustom && nvidiaCustom -> "✅ Kendi Gemini + NVIDIA anahtarlarınız aktif"
            geminiCustom -> "✅ Kendi Gemini anahtarınız aktif" +
                    if (!nvidiaCustom && embedded) " (NVIDIA: gömülü/yok)" else ""
            else -> "⚠️ Henüz anahtar girmediniz — çeviri çalışmayabilir"
        }
        tvKeysStatus.text = status
    }
}
