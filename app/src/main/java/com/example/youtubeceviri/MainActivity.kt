package com.example.youtubeceviri

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
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

    private lateinit var etTestInput: EditText
    private lateinit var btnTestTranslate: Button
    private lateinit var tvTestResult: TextView
    private lateinit var tvApiKeyStatus: TextView
    private lateinit var btnStopService: Button

    private var overlayManager: SubtitleOverlayManager? = null
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handler = Handler(Looper.getMainLooper())

        overlayManager = SubtitleOverlayManager(this)

        initViews()
        setupListeners()
        ApiKeyManager.init(this)
    }

    override fun onResume() {
        super.onResume()
        // api_keys.txt'i otomatik ara: USB/depolamaya yeni dosya konmuÅŸsa
        // anahtarlar sessizce gÃ¼ncellenir, kullanÄ±cÄ± hiÃ§bir ÅŸeye basmaz.
        if (KeyFileImporter.autoImport(this)) {
            Toast.makeText(this, "Anahtarlar api_keys.txt'ten otomatik gÃ¼ncellendi âœ…", Toast.LENGTH_LONG).show()
        }
        checkPermissionsAndStatus()
        updateStopButtonState()
    }

    private fun initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        btnOpenOverlaySettings = findViewById(R.id.btnOpenOverlaySettings)

        etTestInput = findViewById(R.id.etTestInput)
        btnTestTranslate = findViewById(R.id.btnTestTranslate)
        tvTestResult = findViewById(R.id.tvTestResult)
        tvApiKeyStatus = findViewById(R.id.tvApiKeyStatus)
        btnStopService = findViewById(R.id.btnStopService)

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
                Toast.makeText(this, "EriÅŸilebilirlik ayarlarÄ± aÃ§Ä±lamadÄ±: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Overlay ayarlarÄ± aÃ§Ä±lamadÄ±: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestTranslate.setOnClickListener {
            val inputText = etTestInput.text.toString().trim()
            if (inputText.isEmpty()) {
                etTestInput.error = "LÃ¼tfen Ä°ngilizce bir metin girin"
                return@setOnClickListener
            }

            tvTestResult.text = "Ã‡evriliyor... (Gemini + NVIDIA zinciri)"
            lifecycleScope.launch {
                TranslationManager.init(this@MainActivity)
                val translation = TranslationManager.translateToTurkish(inputText)
                tvTestResult.text = if (translation != null) "TÃ¼rkÃ§e Ã‡eviri: $translation"
                                    else "Ã‡eviri baÅŸarÄ±sÄ±z (tÃ¼m saÄŸlayÄ±cÄ±lar hata verdi)"

                // Ã‡eviriyi ekran altyazÄ± Ã§erÃ§evesinde gÃ¶ster
                if (translation != null && Settings.canDrawOverlays(this@MainActivity)) {
                    overlayManager?.showSubtitle(translation)
                } else {
                    Toast.makeText(this@MainActivity, "AltyazÄ± Ã§erÃ§evesini ekranda gÃ¶rmek iÃ§in Overlay iznini verin.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnStopService.setOnClickListener {
            stopService()
        }
    }

    private fun checkPermissionsAndStatus() {
        // 1. EriÅŸilebilirlik Hizmeti Durumu
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        if (isAccessibilityEnabled) {
            tvAccessibilityStatus.text = "Durum: AKTÄ°F (YouTube AltyazÄ±larÄ± Dinleniyor)"
            tvAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
        } else {
            tvAccessibilityStatus.text = "Durum: PASÄ°F (Devre DÄ±ÅŸÄ± - EtkinleÅŸtirmek Ä°Ã§in Butona BasÄ±n)"
            tvAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
        }

        // 2. Overlay Ä°zni Durumu
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        if (hasOverlayPermission) {
            tvOverlayStatus.text = "Durum: Ä°ZÄ°N VERÄ°LDÄ° (Ekran AltyazÄ± Ã‡erÃ§evesi HazÄ±r)"
            tvOverlayStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
        } else {
            tvOverlayStatus.text = "Durum: Ä°ZÄ°N GEREKLÄ° (Devre DÄ±ÅŸÄ± - EtkinleÅŸtirmek Ä°Ã§in Butona BasÄ±n)"
            tvOverlayStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
        }

        // 3. API Anahtar Durumu
        val geminiCustom = ApiKeyManager.hasCustomGemini()
        val nvidiaCustom = ApiKeyManager.hasCustomNvidia()
        val hasEmbedded = BuildConfig.GEMINI_API_KEY.isNotEmpty()
        when {
            geminiCustom || nvidiaCustom -> {
                tvApiKeyStatus.text = "Durum: âœ… KENDÄ° ANAHTARINIZ AKTÄ°F (kota sizde)"
                tvApiKeyStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
            }
            hasEmbedded -> {
                tvApiKeyStatus.text = "Durum: ğŸ”§ GeliÅŸtirici modu (gÃ¶mÃ¼lÃ¼ anahtarlar)"
                tvApiKeyStatus.setTextColor(android.graphics.Color.parseColor("#FFD700"))
            }
            else -> {
                tvApiKeyStatus.text = "Durum: âš ï¸ ANAHTAR GEREKLÄ° â€” Ayarlardan girin"
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

    private fun stopService() {
        val serviceComponent = ComponentName(this, YouTubeSubtitleService::class.java)
        stopService(Intent(this, serviceComponent::class.java))
        // Companion objesinden eriÅŸilemediÄŸi iÃ§in field'Ä± Java refleksiyonu ile sÄ±fÄ±rla
        try {
            val field = YouTubeSubtitleService::class.java.getDeclaredField("isServiceRunning")
            field.isAccessible = true
            field.set(null, false)
        } catch (e: Exception) {
            Log.w("MainActivity", "isServiceRunning field ayarlanamadÄ±: ${e.message}")
        }
        updateStopButtonState()
        Toast.makeText(this, "Ã‡eviri durduruldu", Toast.LENGTH_SHORT).show()
    }

    private fun updateStopButtonState() {
        val isRunning = YouTubeSubtitleService.isServiceRunning
        btnStopService.text = if (isRunning) "Ã‡eviriyi Durdur" else "Ã‡eviriyi BaÅŸlat"
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager?.removeOverlay()
    }
}
