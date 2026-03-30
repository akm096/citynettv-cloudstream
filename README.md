# CityNetTV CloudStream Extension

Azərbaycan, Türkiyə və Rusiya TV kanallarını CloudStream vasitəsilə izləmək üçün eklenti.

## 📺 Xüsusiyyətlər

- **Ölkəyə görə kateqoriyalar**: 🇦🇿 Azərbaycan, 🇹🇷 Türkiyə, 🇷🇺 Rusiya kanalları
- **Janra görə kateqoriyalar**: 📰 Xəbərlər, ⚽ İdman, 🎬 Kino, 👶 Uşaq, 🎵 Musiqi, 🎭 Əyləncə, 📚 Sənədli
- **Kanal logoları**: Hər kanalın logosu görünür
- **EPG (Proqram bələdçisi)**: Hal-hazırda oynayan və növbəti proqramlar
- **Widevine DRM**: Şifrələnmiş kanallar dəstəklənir
- **Geri/irəli sarma**: Canlı yayında 10s/30s skip
- **Axtarış**: Kanal adına görə axtarış

## 🚀 Quraşdırma

### 1. CloudStream Yüklə
Android TV-nizdə CloudStream tətbiqini quraşdırın:
- [CloudStream Releases](https://github.com/recloudstream/cloudstream/releases)

### 2. Repo Əlavə Et
CloudStream-də:
1. **Ayarlar** → **Extensions** → **Repo əlavə et**
2. Bu URL-i daxil edin:
   ```
   https://raw.githubusercontent.com/YOUR_USERNAME/citynettv-cloudstream/builds/plugins.json
   ```

### 3. CityNetTV Eklentisini Yüklə
Extension siyahısında **CityNetTV** tapın və yükləyin.

### 4. Giriş Edin
1. **Ayarlar** → **CityNetTV** bölməsi
2. CityNetTV nömrənizi və şifrənizi daxil edin
3. Hazırdır! Ana səhifədə kanallar görünəcək.

## 🛠️ Development

### Build
```bash
# Windows
.\gradlew.bat CityNetTVProvider:make

# Linux/Mac  
./gradlew CityNetTVProvider:make
```

### Deploy (ADB ilə)
```bash
.\gradlew.bat CityNetTVProvider:deployWithAdb
```

### Requirements
- JDK 17
- Android SDK

## 📋 Kanal Kateqoriyaları

| Kateqoriya | Nümunə Kanallar |
|-----------|----------------|
| 🇦🇿 Azərbaycan | AzTV, İdman TV, Xəzər TV, Space TV, CBC, ARB, İctimai TV |
| 🇹🇷 Türkiyə | TRT, Star TV, Show TV, Kanal D, ATV, Fox TV, CNN Türk |
| 🇷🇺 Rusiya | Первый, Россия, НТВ, ТНТ, СТС, РЕН ТВ |
| 📰 Xəbərlər | CNN, BBC, Euronews, Al Jazeera |
| ⚽ İdman | Setanta Sports, beIN Sports, İdman TV |
| 🎬 Kino | FilmBox, Amedia, HBO |
| 👶 Uşaq | Disney, Nickelodeon, Cartoon Network |
| 🎵 Musiqi | MTV, VH1, Bridge TV |
| 📚 Sənədli | Discovery, National Geographic, History |

## ⚠️ Qeydlər

- **Go aboneliyi** lazımdır (mobil abonelik)
- CityNetTV hesabınız olmalıdır
- TV-nin **Widevine** dəstəyi olmalıdır (əksər modern TV-lər dəstəkləyir)
- Extension mobil cihaz kimi davranır ki, Go aboneliyi işləsin

## 📝 Lisenziya

Bu layihə CityNetTV-nin rəsmi tətbiqi deyil. Şəxsi istifadə üçündür.
