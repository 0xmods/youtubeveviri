package com.example.youtubeceviri

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnOpenAccessibilitySettings: Button
    private lateinit var btnOpenOverlaySettings: Button
    private lateinit var btnStartTranslation: Button
    private lateinit var btnStopTranslation: Button

    private lateinit var etTestInput: EditText
    private lateinit var btnTestTranslate: Button
    private lateinit var tvTestResult: TextView
    private lateinit var tvApiKeyStatus: TextView

    private var overlayManager: SubtitleOverlayManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayManager = SubtitleOverlayManager(this)

        initViews()
        setupListeners()
        ApiKeyManager.init(this)
    }

    override fun onResume() {
        super.onResume()
        // api_keys.txt'i otomically ara: USB/depolamaya yeni dosya konmuşsa
        // anahtarlar sessizce güncellenir, kullanıcı hiçbir şeye basmaz.
        if (KeyFileImporter.autoImport(this)) {
            Toast.makeText(this, "Anahtarlar api_keys.txt'ten otomically güncellendi ✅", Toast.LENGTH_LONG).show()
        }
        checkPermissionsAndStatus()
        updateButtonState()
    }

    private fun initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        btnOpenOverlaySettings = findViewById(R.id.btnOpenOverlaySettings)
        btnStartTranslation = findViewById(R.id.btnStartTranslation)
        btnStopTranslation = findViewById(R.id.btnStopTranslation)

        etTestInput = findViewById(R.id.etTestInput)
        btnTestTranslate = findViewById(R.id.btnTestTranslate)
        tvTestResult = findViewById(R.id.tvTestResult)
        tvApiKeyStatus = findViewById(R.id.tvApiKeyStatus)

        findViewById<Button>(R.id.btnOpenApiKeys).setOnClickListener {
            startActivity(Intent(this, ApiKeySettingsActivity::class.java))
        }
    }

    private fun setupListeners() {
        btnOpenAccessibilitySettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Erişilebilirlik ayarları açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenOverlaySettings.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Overlay ayarları açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestTranslate.setOnClickListener {
            val inputText = etTestInput.text.toString().trim()
            if (inputText.isEmpty()) {
                etTestInput.error = "Lütfen İngilizce bir metin girin"
                return@setOnClickListener
            }

            tvTestResult.text = "Çevriliyor... (Gemini + NVIDIA zinciri)"
            lifecycleScope.launch {
                TranslationManager.init(this@MainActivity)
                val translation = TranslationManager.translateToTurkish(inputText)
                tvTestResult.text = if (translation != null) "Türkçe Çeviri: $translation"
                                    else "Çeviri başarısız (tüm sağlayıcılar hata veri)"

                // Çeviriyi ekran altyazı çerçevesinde göster
                if (translation != null && Settings.canDrawOverlays(this@MainActivity)) {
                    overlayManager?.showSubtitle(translation)
                } else {
                    Toast.makeText(this@MainActivity, "Altyazı çerçevesini ekranda görmek için Overlay iznini verin.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnStartTranslation.setOnClickListener {
            startService()
        }

        btnStopTranslation.setOnClickListener {
            stopService()
        }
    }

    private fun checkPermissionsAndStatus() {
        // 1. Erişilebilirlik Hizmeti Durumu
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        if (isAccessibilityEnabled) {
            tvAccessibilityStatus.text = "Durum: AKTİF (YouTube Altyazıları Dinleniyor)"
            tvAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
        } else {
            tvAccessibilityStatus.text = "Durum: PASİF (Devre Dışı - Etkinleştirmek İçin Butona Basın)"
            tvAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
        }

        // 2. Overlay İzni Durumu
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        if (hasOverlayPermission) {
            tvOverlayStatus.text = "Durum: İZİN VERİLDİ (Ekran Altyazı Çerçevesi Hazır)"
            tvOverlayStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
        } else {
            tvOverlayStatus.text = "Durum: İZİN GEREKLİ (Devre Dışı - Etkinleştirmek İçin Butona Basın)"
            tvOverlayStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
        }

        // 3. API Anahtar Durumu
        val geminiCustom = ApiKeyManager.hasCustomGemini()
        val nvidiaCustom = ApiKeyManager.hasCustomNvidia()
        val hasEmbedded = BuildConfig.GEMINI_API_KEY.isNotEmpty()
        when {
            geminiCustom || nvidiaCustom -> {
                tvApiKeyStatus.text = "Durum: ✅ KENDİ ANAHTARINIZ AKTİF (kota sizde)"
                tvApiKeyStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
            }
            hasEmbedded -> {
                tvApiKeyStatus.text = "Durum: 🔧 Geliştirici modu (gömülü anahtarlar)"
                tvApiKeyStatus.setTextColor(android.graphics.Color.parseColor("#FFD700"))
            }
            else -> {
                tvApiKeyStatus.text = "Durum: ⚠️ ANAHTAR GEREKLİ — Ayarlardan girin"
                tvApiKeyStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val expectedServiceName = "$packageName/${YouTubeSubtitleService::class.java.canonicalName}"

        for (service in enabledServices) {
            val serviceId = service.id
            if (serviceId.equals(expectedServiceName, ignoreCase = true) || serviceId.contains(YouTubeSubtitleService::class.java.simpleName)) {
                return true
            }
        }
        return YouTubeSubtitleService.isServiceRunning
    }

    private fun startService() {
        // AccessibilityService sistem tarafından yönetilir; start/stopService
        // çağırmak yerine duraklatma bayrağını kullanıyoruz.
        YouTubeSubtitleService.isPaused = false
        updateButtonState()
        Toast.makeText(this, "Çeviri başlatıldı", Toast.LENGTH_SHORT).show()
    }

    private fun stopService() {
        YouTubeSubtitleService.isPaused = true
        overlayManager?.hideSubtitle()
        updateButtonState()
        Toast.makeText(this, "Çeviri durduruldu", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonState() {
        val isRunning = !YouTubeSubtitleService.isPaused
        btnStartTranslation.visibility = if (isRunning) View.GONE else View.VISIBLE
        btnStopTranslation.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager?.removeOverlay()
    }
}