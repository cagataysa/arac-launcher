# Araç Ekranı

Tesla tipi dikey Android multimedya ekranı için sade ana ekran (launcher).

**Ekranda neler var:** saat ve tarih, hava durumu (Open-Meteo), GPS'ten hız (km/sa), büyük navigasyon butonu (Google Maps, Yandex veya Waze'den hangisi yüklüyse), müzik tuşları (önceki, oynat/duraklat, sonraki), en fazla 6 uygulama kısayolu ve tüm uygulamalar listesi.

## Kurulum
1. GitHub'da **Releases → "Araç Ekranı (son sürüm)"** sayfasından `AracEkrani.apk` dosyasını indir.
2. Dosyayı USB belleğe at, belleği ekrana tak ve dosya yöneticisinden APK'yı aç.
   - "Bilinmeyen kaynaklar" izni sorarsa izin ver.
3. Ev (Home) tuşuna bas, **Araç Ekranı**'nı seç ve **Her zaman** de.
4. Konum izni istendiğinde **İzin ver** de. Hız ve hava durumu için gerekli.

## Kullanım
- **Kısayol eklemek:** Tüm uygulamalar → uygulamaya uzun bas.
- **Kısayol çıkarmak:** Ana ekrandaki kısayola uzun bas.
- **Hava durumunu yenilemek:** Hava kartına dokun.

## Eski ana ekrana dönmek
Ayarlar → Uygulamalar → Varsayılan uygulamalar → Ana ekran uygulaması → orijinal launcher'ı seç. Ya da Araç Ekranı'nı kaldır.

Kodda her `push`'tan sonra APK, GitHub Actions'ta otomatik olarak yeniden derlenir.
