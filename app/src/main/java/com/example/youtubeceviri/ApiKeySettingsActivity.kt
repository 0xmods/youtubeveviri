package com.example.youtubeceviri

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Kullanıcının kendi Gemini / NVIDIA API anahtarlarını girdiği ekran.
 *
 * İki yöntem sunar:
 * 1. USB/Depolama'dan "api_keys.txt" dosyası içe aktarma (TV'de yazmaya gerek yok)
 * 2. Elle yazma (kumanda ile)
 *
 * Girilen anahtarlar SharedPreferences'te saklanır (APK dışına çıkmaz).
 */
class ApiKeySettingsActivity : AppCompatActivity() {

    private lateinit var etGeminiKey: EditText
    private lateinit var etNvidiaKey: EditText
    private lateinit var tvKeysStatus: TextView

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importKeysFromDirectory(uri)
        }
    }

    /**
     * TV'lerde SAF dosya seçici (DocumentsUI) çoğu zaman bulunmaz.
     * Bu durumda depolama okuma izni alıp bilinen yolları (USB kökleri,
     * /sdcard/Download vb.) doğrudan tararız.
     */
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            importKeysByPathScan()
        } else {
            Toast.makeText(
                this,
                "Depolama izni verilmedi — USB'den okuyamıyorum.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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

        findViewById<Button>(R.id.btnImportFile).setOnClickListener {
            startFileImport()
        }
    }

    private fun startFileImport() {
        // 1. Önce SAF dosya seçici dene (telefonlarda / dosya seçici olan TV'lerde çalışır)
        try {
            importFileLauncher.launch(null)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "SAF dosya seçici yok, yol taramasına geçiliyor: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Dosya seçici açılamadı, yol taramasına geçiliyor: ${e.message}")
        }

        // 2. Fallback: dosya seçici yok — depolama izniyle doğrudan yolları tara
        if (hasStoragePermission()) {
            importKeysByPathScan()
        } else {
            Toast.makeText(
                this,
                "Dosya seçici yok; USB'deki api_keys.txt için depolama izni istiyorum…",
                Toast.LENGTH_LONG
            ).show()
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Bilinen yollarda api_keys.txt (veya keys.txt) arar:
     * - Dahili depolama kökü ve /Download
     * - /storage altındaki tüm birimler (USB bellekler burada mount edilir) ve /Download
     */
    private fun importKeysByPathScan() {
        try {
            val candidateNames = setOf("api_keys.txt", "livesub_keys.txt", "keys.txt")
            val roots = mutableListOf(
                Environment.getExternalStorageDirectory() ?: File("/sdcard")
            )
            File("/storage").listFiles()?.let { roots.addAll(it) }

            val searchPaths = mutableListOf<File>()
            for (root in roots) {
                for (name in candidateNames) {
                    searchPaths += File(root, name)
                    searchPaths += File(root, "Download/$name")
                }
            }

            for (file in searchPaths) {
                if (file.isFile && file.canRead()) {
                    val content = try {
                        file.readText()
                    } catch (e: Exception) {
                        Log.w(TAG, "${file.absolutePath} okunamadı: ${e.message}")
                        continue
                    }
                    if (applyKeyFileContent(content)) {
                        Toast.makeText(this, "Anahtarlar yüklendi: ${file.absolutePath} ✅", Toast.LENGTH_LONG).show()
                        return
                    }
                }
            }

            Toast.makeText(
                this,
                "api_keys.txt bulunamadı. Dosyayı USB belleğin KÖK dizinine veya Download klasörüne koyun.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Yol taraması hatası: ${e.message}", e)
            Toast.makeText(this, "Tarama hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Seçilen klasörde (USB bellek kökü veya depolama klasörü) api_keys.txt
     * (veya livesub_keys.txt / keys.txt) arar; bulursa anahtarları içe aktarır.
     */
    private fun importKeysFromDirectory(treeUri: Uri) {
        try {
            // Kalıcı izin al: sonraki açılışlarda tekrar seçime gerek kalmasın
            runCatching {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val root = DocumentFile.fromTreeUri(this, treeUri)
            if (root == null) {
                Toast.makeText(this, "Klasör okunamadı.", Toast.LENGTH_LONG).show()
                return
            }

            val candidateNames = setOf("api_keys.txt", "livesub_keys.txt", "keys.txt")
            val keysFile = root.listFiles().firstOrNull {
                it.name != null && it.name!!.lowercase() in candidateNames
            }

            if (keysFile == null) {
                Toast.makeText(
                    this,
                    "api_keys.txt bulunamadı. Dosyayı seçtiğiniz klasörün köküne koyun.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val content = contentResolver.openInputStream(keysFile.uri)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (applyKeyFileContent(content)) {
                Toast.makeText(this, "Anahtarlar dosyadan yüklendi ✅", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this,
                    "Dosyada geçerli anahtar bulunamadı. Format: GEMINI_API_KEY=... / NVIDIA_API_KEY=...",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dosya içe aktarma hatası: ${e.message}", e)
            Toast.makeText(this, "Dosya okunamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * KEY=VALUE satırlarını ayrıştırıp anahtarları kaydeder.
     * Doğrudan metin içeriği verildiğinde de çalışır.
     */
    private fun applyKeyFileContent(content: String): Boolean {
        if (content.isBlank()) return false

        var foundAny = false
        var geminiFound: String? = null
        var nvidiaFound: String? = null

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim().uppercase()
            val value = line.substring(eq + 1).trim()
            if (value.isEmpty()) continue
            when (key) {
                "GEMINI_API_KEY", "GEMINI", "GOOGLE_API_KEY" -> { geminiFound = value; foundAny = true }
                "NVIDIA_API_KEY", "NVIDIA", "NIM_API_KEY" -> { nvidiaFound = value; foundAny = true }
            }
        }

        if (geminiFound != null) ApiKeyManager.saveGeminiKey(geminiFound)
        if (nvidiaFound != null) ApiKeyManager.saveNvidiaKey(nvidiaFound)

        if (foundAny) {
            GeminiTranslationService.clearCache()
            NvidiaTranslationService.clearCache()
            if (geminiFound != null) etGeminiKey.setText(geminiFound)
            if (nvidiaFound != null) etNvidiaKey.setText(nvidiaFound)
            updateStatus()
        }
        return foundAny
    }

    private fun updateStatus() {
        val geminiCustom = ApiKeyManager.hasCustomGemini()
        val nvidiaCustom = ApiKeyManager.hasCustomNvidia()

        val status = when {
            geminiCustom && nvidiaCustom -> "✅ Kendi Gemini + NVIDIA anahtarlarınız aktif"
            geminiCustom -> "✅ Kendi Gemini anahtarınız aktif"
            else -> "⚠️ Henüz anahtar girmediniz — çeviri çalışmayabilir"
        }
        tvKeysStatus.text = status
    }

    companion object {
        private const val TAG = "ApiKeySettings"
    }
}
