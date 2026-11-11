# 📺 YoutubeCanlıÇeviri — TV için Anlık Altyazı Çevirisi

Android TV'de **YouTube** (ve diğer uygulamalarda) oynayan İngilizce altyazıları
gerçek zamanlı olarak Türkçe'ye çeviren açık kaynak Android uygulaması.

> Bu uygulama YouTube veya Google ile bağlantılı değildir. Tüm telif hakları
> ilgili sahiplerine aittir.

---

## 📥 APK ile Kurulum (Önerilen — PC gerekmez)

En kolay yol: hazır APK'yı indirip TV'nize kurmak. 5 dakika sürer.

### 1. APK'yı indirin

- [Releases](https://github.com/0xmods/youtubeveviri/releases) sayfasına gidin
- En güncel sürümün altındaki **`YoutubeCanliCeviri-v1.0.apk`** dosyasını indirin

### 2. API anahtar dosyasını hazırlayın (TV'de yazma yok!)

PC'de veya telefonda **Not Defteri** ile `api_keys.txt` adında bir dosya oluşturun,
içine aşağıdaki 2 satırı kendi anahtarlarınızla yazın:

```
GEMINI_API_KEY=AIza...sizin-anahtariniz
NVIDIA_API_KEY=nvapi-...sizin-anahtariniz
```

> ⚠️ **Dikkat:** `AIza...` ile başlayan anahtar **Gemini**'nindir,
> `nvapi-` ile başlayan ise **NVIDIA**'nındır. Satırları karıştırmayın.
> (Sadece Gemini satırı da yeterli — NVIDIA isteğe bağlı yedek motordur.)

Anahtarları nasıl alacağınızı 🔑 bölümünde adım adım anlattık.

### 3. Dosyayı TV'nin depolamasına koyun

Dosyayı şu konumlardan birine bırakın — uygulama **otomatik olarak** bulur:

| Konum | Nasıl konur? |
|---|---|
| **`/sdcard/Download/api_keys.txt`** ⭐ | Telefondan "Send Files to TV" ile gönderin, veya USB'ye `Download` klasörü açıp için koyun |
| **USB belleğin kökü** | USB'ye `api_keys.txt`'i kopyalayın, TV'ye takın |
| **USB'deki `Download`** | USB'de `Download` klasörüne koyun |
| **`/sdcard/api_keys.txt`** | adb push veya dosya yöneticisiyle ana depolama köküne |

**Yöntemler:**

| Yöntem | Nasıl? |
|---|---|
| **Send Files to TV** ⭐ | TV'ye ve telefona "Send Files to TV" uygulamasını kurup dosyayı gönderin (en kolayı) |
| **USB bellek** | APK + `api_keys.txt`'i USB'ye kopyalayın, TV'ye takın |
| **Downloader** | TV'deki Downloader uygulamasına dosyanın indirme linkini girin |
| **adb** | `adb push api_keys.txt /sdcard/Download/api_keys.txt` |

### 4. APK'yı kurun — anahtarlar kendiliğinden yüklenir

1. Dosya yöneticisiyle `YoutubeCanliCeviri-v1.0.apk`'yı kurun
   (ilk kurulumda "bilinmeyen kaynak" izni istenir → verin)
2. **Bir kere** dosya izni verin: Uygulamayı açın → **"API Anahtarlarını Ayarla"** →
   **"Dosyadan Yükle (USB/Depolama)"** → açılan ekranda **YoutubeCanlıÇeviri**'yi
   bulup **"Tüm dosyalara erişime izin ver"** deyin (Android 13+ TV'lerde gerekir)
3. Uygulama `api_keys.txt`'i bulur, anahtarları kutulara doldurur → **Kaydet**

> 🔄 **Otomotik:** Bundan sonra dosyayı güncellerseniz (ör. yeni anahtar),
> uygulamayı bir kez açıp kapatmanız yeterli — anahtarlar **kendiliğinden**
> güncellenir, hiçbir butona basmanıza gerek kalmaz.

> **Not:** Dağıtılan APK'da **API anahtarı yoktur** — herkes kendi ücretsiz
> anahtarını kullanır, kimse kimseyi bekletmez. Anahtarlar sadece cihazınızda
> saklanır, kimseyle paylaşılmaz.

### 5. TV'de izinler

Uygulamayı ilk açtığınızda iki izin gereklidir:

1. **Erişilebilirlik Hizmeti** *(zorunlu)* — altyazıları ekrandan taramak için.
   Uygulamadaki "Erişilebilirlik Ayarları" butonu sizi doğru ekrana götürür;
   listeden **YoutubeCanlıÇeviri** hizmetini etkinleştirin.
2. **Ekran Üstü Çizim (Overlay)** *(zorunlu)* — çeviri çerçevesini göstermek için.
   "Altyazı Çerçevesi Ayarları" butonundan izin verin.

### 6. Hazır! 🎉

YouTube'u açıp İngilizce altyazılı bir video oynatın — orijinal altyazının
hemen üstünde, sağa sola esneyen altın çerçeveli Türkçe çeviriyi göreceksiniz.

---

## 🔑 Ücretsiz API Anahtarları (APK kullanıcileri + geliştiriciler)

Uygulama çeviri için iki ücretsiz API kullanır. **Kendi anahtarınızı girmeden
çeviri yapılmaz** — herkes kendi kotunu kullanır, kimse kimseyi bekletmez.

### Adım adım: Kendi anahtarınızı uygulama içine girin

1. Aşağıdaki iki ücretsiz anahtarı alın (2-3 dakika)
2. Uygulamayı açın → **"API Anahtarlarını Ayarla"** → **"Dosyadan Yükle (USB/Depolama)"**
   ile `api_keys.txt` dosyanızı içe aktarın (veya kumandayla elle girin)
3. YouTube'a dönün — çeviriler sizin kotanızdan çalışır 🎉

### 1️⃣ Google Gemini Anahtarı (Birincil çeviri motoru)

1. **https://aistudio.google.com/apikey** adresine gidin
2. Google hesabınızla giriş yapın (kart bilgisi **gerekmez**)
3. **"Create API key"** (API anahtarı oluştur) butonuna basın
4. Yeni bir proje seçin veya mevcut projenizde oluşturun
5. `AIza...` ile başlayan anahtarı kopyalayın

**Ücretsiz kota:** Gemini 3.5 Flash-Lite modeli ücretsiz katmanda dakikada
sınırlı sayıda istek verir (günlük binlerce istek, canlı altyazı için yeterli).

### 2️⃣ NVIDIA Riva Anahtarı (Yedek çeviri motoru)

1. **https://build.nvidia.com** adresine gidin
2. Hesap oluşturun / giriş yapın (kart bilgisi **gerekmez**)
3. **riva-translate-4b-instruct-v2** modelini aratın:
   https://build.nvidia.com/nvidia/riva-translate-4b-instruct-v2
4. Model sayfasındaki **"Get API Key"** (API anahtarı al) butonuna basın
5. `nvapi-...` ile başlayan anahtarı kopyalayın

**Ücretsiz kota:** NVIDIA, deneme döneminde API katalogundaki tüm modeller için
cömert bir ücretsiz kota sunar (1000 istek/75 saniye aralıklarla yenilenir).

### Anahtarları yerleştirme

**Geliştiriciler** anahtarları `local.properties`'e (git'e girmez) yazarsa
debug build'e gömülür — ama **release build'e ASLA anahtar gömülmez**:

```properties
GEMINI_API_KEY=AIza...your-key-here
NVIDIA_API_KEY=nvapi-...your-key-here
```

**Son kullanıcılar** dosyayı TV'ye koyar (aşağıdaki akış), uygulama otomatik yükler:

1. `api_keys.txt` → `/sdcard/Download/` veya USB kökü
2. Uygulamayı açın → **"API Anahtarlarını Ayarla"** → **"Dosyadan Yükle"** →
   bir kere depolama izni verin
3. Anahtarlar otomatik yüklenir; dosya değişirse uygulamayı açmak yeterli
4. (İsterseniz kumandayla elle de girebilirsiniz → **Kaydet**)

(Anahtarlar cihazda SharedPreferences'te saklanır, repo'ya girmez.)

---

## ✨ Özellikler

- 🎯 **Anlık çeviri** — Ekranın alt bölgesi her 0.8-2.5 saniyede bir taranır,
  İngilizce altyazı OCR ile okunur ve Türkçe çevirisi ekran üstünde gösterilir
- 🧠 **Çift çeviri motoru:**
  1. **Google Gemini** (`gemini-3.5-flash-lite`) — birincil, doğal çeviri
  2. **NVIDIA Riva** (`riva-translate-4b-instruct-v2`) — Gemini kotası biterse devreye girer
- ✍️ **Akıllı cümle takibi** — Cümleler nokta/virgül gibi işaretler gelene kadar
  biriktirilir; cümle oluşmadan çeviri yapılmaz (anlamlı, bütün cümleler)
- 📏 **Dinamik altyazı çerçevesi** — Metin uzunluğuna göre font boyutu ve genişlik
  otomatik ayarlanır; uzun cümleler 3 satıra kadar genişler
- 🔋 **Düşük RAM'li TV'ler için optimize** — Uyarlanabilir tarama aralığı, daraltılmış
  OCR bölgesi, yerel ML Kit çevirisi yok (tamamı API tabanlı)

## 🛠️ Geliştirici Kurulumu

1. Repoyu klonlayın ve Android Studio ile açın
2. `local.properties` dosyasına kendi API anahtarlarınızı ekleyin (yukarıdaki 🔑 bölümü)
3. Uygulamayı derleyin ve Android TV cihazınıza yükleyin:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### TV'de izinler

Uygulamayı ilk açtığınızda iki izin vermeniz gerekir:

- **Erişilebilirlik Hizmeti** — ekranı taramak için (zorunlu)
- **Ekran üstü çizim (Overlay)** — çeviri çerçevesini göstermek için (zorunlu)

## 🧪 Nasıl Çalışır?

```
┌─────────────────────────────────────────────────────┐
│  AccessibilityService.takeScreenshot (800-2500ms)   │
│              ↓                                      │
│  Alt bölge kırpar (ekranın %78-94'ü)                │
│              ↓                                      │
│  ML Kit OCR → İngilizce metin                       │
│              ↓                                      │
│  Cümle biriktirici (nokta/virgüle kadar)            │
│              ↓                                      │
│  Gemini API → (başarısızsa) NVIDIA Riva API         │
│              ↓                                      │
│  Overlay çerçevesinde Türkçe çeviri                 │
└─────────────────────────────────────────────────────┘
```

## ⚙️ Mimari

| Dosya | Görev |
|---|---|
| `YouTubeSubtitleService.kt` | Erişilebilirlik servisi: ekran tarama döngüsü + cümle takibi |
| `SubtitleOCRScanner.kt` | Alt bölge kırpma + ML Kit OCR |
| `TranslationManager.kt` | Çeviri zinciri: Gemini → NVIDIA |
| `GeminiTranslationService.kt` | Gemini API istemcisi |
| `NvidiaTranslationService.kt` | NVIDIA NIM (Riva) API istemcisi |
| `SubtitleOverlayManager.kt` | Ekran üstü çeviri çerçevesi |

## 💖 Bağış

Bu uygulamayı ücretsiz ve açık kaynak olarak geliştiriyorum. Faydalı bulduysan
destek olmayı düşünebilirsin 🙏

### ₿ Kripto (ERC-20 — ETH, USDT, USDC vb.)

```
0xb20bc1a5d27d9e57ad5a320c672f3c4ccf3a5e6a
```

> Herhangi bir cüzdana (MetaMask, Trust Wallet, Binance vb.) ERC-20 ağında
> ETH, USDT, USDC veya başka bir token gönderebilirsin. Bağışlarını koda
> katkı zamanı satın almak için kullanıyorum ☕

Katkı yapmak istersen Pull Request'ler de her zaman açık!

## 📄 Lisans

[MIT](LICENSE)
