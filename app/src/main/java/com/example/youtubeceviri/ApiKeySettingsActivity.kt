package com.example.youtubeceviri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
 * İçe aktarma yöntemleri (TV'lerde yazma zor olduğundan):
 * 1. Doğrudan yol taraması (USB kökleri + Download) — Android 12 ve altında
 * 2. Sistem dosya seçici (GET_CONTENT) — cihazda dosya yöneticisi varsa
 * 3. SAF klasör seçici — DocumentsUI olan cihazlarda
 * 4. "Tüm dosyalara erişim" izni (MANAGE_EXTERNAL_STORAGE) — Android 13+ TV'ler
 *    için son çare (bu TV: Android 14, READ_EXTERNAL_STORAGE geçersiz)
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

    /** Sistem dosya seçici: GET_CONTENT herhangi bir dosyayı döndürür */
    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importKeysFromUri(uri)
        }
    }

    /** "Tüm dosyalara erişim" ayar ekranından dönünce tekrar tarar */
    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasAllFilesAccess()) {
            importKeysByPathScan()
        } else {
            Toast.makeText(
                this,
                "İzin verilmedi — dosyadan yükleme yapılamaz.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Android 12- cihazlar için runtime depolama izni */
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            if (!importKeysByPathScan()) {
                tryGetContent()
            }
        } else {
            Toast.makeText(
                this,
                "Depolama izni verilmedi — dosyadan okuyamıyorum.",
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

    override fun onResume() {
        super.onResume()
        // Dosyadan anahtar güncellenmiş olabilir — sessizce kontrol et
        if (KeyFileImporter.autoImport(this)) {
            if (ApiKeyManager.hasCustomGemini()) etGeminiKey.setText(ApiKeyManager.getGeminiKey())
            if (ApiKeyManager.hasCustomNvidia()) etNvidiaKey.setText(ApiKeyManager.getNvidiaKey())
            updateStatus()
        }
    }

    private fun startFileImport() {
        // 1. Doğrudan yol taraması (izinler uygunsa çalışır: Android 12- veya
        //    tüm-dosyalar izni verilmiş Android 13+)
        if (canReadSharedStorage() && importKeysByPathScan()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (özellikle 13/14 TV'ler): GET_CONTENT fotoğraf
            // seçicisine yönlendirilir (txt göstermez), SAF çoğu TV'de yok.
            // Doğrudan "Tüm dosyalara erişim" ayar ekranına git; dönünce
            // otomatik tarama yapılır. (Arka arkaya başarısız seçici
            // açma girişimleri düşük RAM'li TV'yi donduruyordu.)
            Toast.makeText(
                this,
                "Açılan ekranda 'Tüm dosyalara erişime izin ver' deyin — api_keys.txt otomatik bulunacak.",
                Toast.LENGTH_LONG
            ).show()
            allFilesAccessLauncher.launch(manageAllFilesIntent())
            return
        }

        // Android 10 ve altı: sistem dosya seçici (dosya yöneticileri) dene
        if (tryGetContent()) return

        // SAF klasör seçici (DocumentsUI olan cihazlarda)
        try {
            importFileLauncher.launch(null)
        } catch (e: Exception) {
            Log.w(TAG, "SAF seçici de açılamadı: ${e.message}")
        }

        if (!canReadSharedStorage()) {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun tryGetContent(): Boolean {
        return try {
            getContentLauncher.launch("*/*")
            true
        } catch (e: Exception) {
            Log.w(TAG, "GET_CONTENT seçici yok: ${e.message}")
            false
        }
    }

    private fun manageAllFilesIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    private fun canReadSharedStorage(): Boolean {
        if (hasAllFilesAccess()) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: READ_EXTERNAL_STORAGE artık yeterli değil
            false
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Bilinen yollarda api_keys.txt (veya keys.txt) arar:
     * - Dahili depolama kökü ve /Download
     * - /storage altındaki tüm birimler (USB bellekler burada mount edilir) ve /Download
     *
     * @return dosya bulundu ve anahtarlar uygulandı ise true
     */
    private fun importKeysByPathScan(): Boolean {
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
                if (file.isFile) {
                    val content = try {
                        file.readText()
                    } catch (e: Exception) {
                        Log.w(TAG, "${file.absolutePath} okunamadı: ${e.message}")
                        continue
                    }
                    if (applyKeyFileContent(content)) {
                        Toast.makeText(this, "Anahtarlar yüklendi: ${file.absolutePath} ✅", Toast.LENGTH_LONG).show()
                        return true
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
        return false
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

            importKeysFromUri(keysFile.uri)
        } catch (e: Exception) {
            Log.e(TAG, "Dosya içe aktarma hatası: ${e.message}", e)
            Toast.makeText(this, "Dosya okunamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Tek bir dosyanın içeriğini (content:// URI) okuyup uygular */
    private fun importKeysFromUri(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)
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
            Log.e(TAG, "URI okuma hatası: ${e.message}", e)
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
