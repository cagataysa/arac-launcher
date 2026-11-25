# Araç Ekranı

Tesla tipi dikey Android multimedya ekranı için sade ana ekran (launcher).

**Ekranda neler var:** cihazın duvar kağıdı üzerinde yarı saydam kartlar; saat ve tarih, hava durumu (Open-Meteo), çalan şarkı kartı (kapak, şarkı/sanatçı, ilerleme, önceki/oynat/sonraki), ilk sırası navigasyon olan en fazla 6 kısayol (Google Maps / Yandex / Waze — hangisi yüklüyse) ve tüm uygulamalar listesi.

## Kurulum
1. GitHub'da **Releases → "Araç Ekranı (son sürüm)"** sayfasından `AracEkrani.apk` dosyasını indir.
2. Dosyayı USB belleğe at, belleği ekrana tak ve dosya yöneticisinden APK'yı aç.
   - "Bilinmeyen kaynaklar" izni sorarsa izin ver.
3. Ev (Home) tuşuna bas, **Araç Ekranı**'nı seç ve **Her zaman** de.
4. Konum izni istendiğinde **İzin ver** de (hava durumu için).
5. Müzik kartına dokun → **Bildirim erişimi** ekranında **Araç Ekranı**'nı aç (çalan şarkıyı göstermek için gerekli, bir kez yapılır).

## Kullanım
- **Kısayol eklemek:** Tüm uygulamalar → uygulamaya uzun bas.
- **Kısayol çıkarmak:** Ana ekrandaki kısayola uzun bas.
- **Hava durumunu yenilemek:** Hava kartına dokun.
- **Duvar kağıdı değiştirmek:** Saate uzun bas → resim seç.
- **Çalan uygulamayı açmak:** Şarkı adına/kapağa dokun.

## Eski ana ekrana dönmek
Ayarlar → Uygulamalar → Varsayılan uygulamalar → Ana ekran uygulaması → orijinal launcher'ı seç. Ya da Araç Ekranı'nı kaldır.

Kodda her `push`'tan sonra APK, GitHub Actions'ta otomatik olarak yeniden derlenir.
