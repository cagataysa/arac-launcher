package com.cagatay.aracekran;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.view.View;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.session.MediaSession;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {

    private static final int IZIN_KODU = 42;
    private static final long HAVA_ARALIGI_MS = 30 * 60 * 1000L; // 30 dk
    private static final Locale TR = new Locale("tr", "TR");

    private TextView saat, tarih, sicaklik, havaDurum;
    private TextView kaynak, sarki, sanatci, gecen, toplam, oynat;
    private ImageView kapak;
    private ProgressBar ilerleme;
    private LinearLayout dok;
    private View kok, havaKart, muzikKart, ayrac;
    private ImageView havaIkon, navBtn, telBtn;
    private TextView oncekiV, sonrakiV;
    private int tusRenk = 0xE626313F;

    // Tema
    private static final int[] VURGU_RENKLERI = {0xFF3DA9FC, 0xFFE8452C, 0xFF2BB673, 0xFFF2A93B};
    private static final String[] VURGU_ADLARI = {"Mavi", "Kırmızı", "Yeşil", "Turuncu"};
    private int vurgu = 0xFF3DA9FC;
    private int yaziRenk = 0xFFF2F5F8;
    private boolean gece = false;
    private int sonSaat = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat saatFmt = new SimpleDateFormat("HH:mm", TR);
    private final SimpleDateFormat tarihFmt = new SimpleDateFormat("d MMMM EEEE", TR);

    private LocationManager konum;
    private Location sonHavaKonumu;
    private long sonHavaZamani = 0;
    private boolean havaYukleniyor = false;

    // Müzik
    private MediaSessionManager oturumYoneticisi;
    private ComponentName dinleyici;
    private MediaController aktif;
    private boolean bildirimIzniVar = false;

    // Medya oturumu açmayan Bluetooth müzik uygulamaları için yedek bilgi
    private String harBaslik, harKisi, harKaynak;
    private long harZaman = 0;
    private static final long HAR_GECERLILIK_MS = 15 * 60 * 1000L;
    private final ArrayDeque<String> sonYayinlar = new ArrayDeque<>();
    private boolean aliciKayitli = false;

    private static final String[] YAYIN_EYLEMLERI = {
            "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
            "android.bluetooth.avrcp-controller.profile.action.PLAYER_SETTING",
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
            "com.android.music.queuechanged",
            "com.spotify.music.metadatachanged",
            "com.htc.music.metachanged",
            "com.miui.player.metachanged",
            "com.sec.android.app.music.metachanged",
            "com.syu.music.info",
            "com.syu.bt.music",
            "com.microntek.bt.music",
            "com.microntek.music.report",
            "com.fyt.bt.music",
            "com.ts.bt.music",
            "android.intent.action.BT_MUSIC_INFO",
            "com.android.bluetooth.music.id3",
            "com.nwd.bt.music.id3"
    };

    private final BroadcastReceiver yayinAlici = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            yayinIsle(i);
        }
    };

    private final MediaController.Callback medyaGeriCagri = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { muzikGoster(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { muzikGoster(); }
        @Override public void onSessionDestroyed() { oturumlariYenile(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener oturumDinleyici =
            liste -> oturumlariYenile();

    private final Runnable saniyelik = new Runnable() {
        @Override
        public void run() {
            Date simdi = new Date();
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (h != sonSaat) {
                sonSaat = h;
                temaUygula();
            }
            saat.setText(saatFmt.format(simdi));
            tarih.setText(tarihFmt.format(simdi));
            ilerlemeGuncelle();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        saat = findViewById(R.id.saat);
        tarih = findViewById(R.id.tarih);
        sicaklik = findViewById(R.id.sicaklik);
        havaDurum = findViewById(R.id.havaDurum);
        kaynak = findViewById(R.id.kaynak);
        sarki = findViewById(R.id.sarki);
        sanatci = findViewById(R.id.sanatci);
        gecen = findViewById(R.id.gecen);
        toplam = findViewById(R.id.toplam);
        oynat = findViewById(R.id.oynat);
        kapak = findViewById(R.id.kapak);
        kapak.setClipToOutline(true);
        ilerleme = findViewById(R.id.ilerleme);
        dok = findViewById(R.id.dok);
        kok = findViewById(R.id.kok);
        havaKart = findViewById(R.id.havaKart);
        muzikKart = findViewById(R.id.muzikKart);
        havaIkon = findViewById(R.id.havaIkon);
        navBtn = findViewById(R.id.navBtn);
        telBtn = findViewById(R.id.telBtn);
        ayrac = findViewById(R.id.ayrac);
        navBtn.setOnClickListener(v -> {
            if (!Kisayollar.baslat(this, Kisayollar.NAV)) {
                Toast.makeText(this, "Navigasyon uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
            }
        });
        telBtn.setOnClickListener(v -> telefonAc());
        // Kapak kare olsun: yüksekliği kadar genişlik
        kapak.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            View ebeveyn = (View) v.getParent();
            int sinir = ebeveyn.getWidth() - ebeveyn.getPaddingLeft() - ebeveyn.getPaddingRight();
            if (sinir > 0) h = Math.min(h, sinir);
            if (h > 0 && v.getLayoutParams().width != h) {
                v.getLayoutParams().width = h;
                v.post(v::requestLayout);
            }
        });
        oncekiV = findViewById(R.id.onceki);
        sonrakiV = findViewById(R.id.sonraki);

        konum = (LocationManager) getSystemService(LOCATION_SERVICE);
        oturumYoneticisi = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        dinleyici = new ComponentName(this, MedyaDinleyici.class);

        findViewById(R.id.onceki).setOnClickListener(v -> onceki());
        oynat.setOnClickListener(v -> oynatDuraklat());
        findViewById(R.id.sonraki).setOnClickListener(v -> sonraki());
        findViewById(R.id.sarkiBilgi).setOnClickListener(v -> muzikUygulamasiniAc());
        findViewById(R.id.sarkiBilgi).setOnLongClickListener(v -> {
            teshisGoster();
            return true;
        });

        findViewById(R.id.havaKart).setOnClickListener(v -> {
            sonHavaZamani = 0;
            havaGuncelle(true);
        });

        // Saate uzun basınca duvar kağıdı seçici açılır
        findViewById(R.id.saatAlani).setOnLongClickListener(v -> {
            ayarlarGoster();
            return true;
        });

        SharedPreferences sp = ayarlar();
        if (!sp.getBoolean("duvarSoruldu", false)) {
            sp.edit().putBoolean("duvarSoruldu", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Duvar kağıdı")
                    .setMessage("Kırmızı–mavi duvar kağıdı yüklensin mi? Sonradan saate ya da \"Tümü\"ne uzun basarak değiştirebilirsin.")
                    .setPositiveButton("Yükle", (d, w) -> hazirDuvarKagidi())
                    .setNegativeButton("Şimdi değil", null)
                    .show();
        }

        if (!izinVar()) {
            if (Build.VERSION.SDK_INT >= 31) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_CONNECT}, IZIN_KODU);
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, IZIN_KODU);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(saniyelik);
        sonSaat = -1;
        handler.post(saniyelik);
        konumBaslat();
        havaGuncelle(false);
        muzikBaslat();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(saniyelik);
        try {
            konum.removeUpdates(this);
        } catch (Exception ignored) {
        }
        muzikDurdur();
    }

    @Override
    public void onBackPressed() {
        // Ana ekranda geri tuşu bir şey yapmasın.
    }

    @Override
    public void onRequestPermissionsResult(int kod, String[] izinler, int[] sonuclar) {
        super.onRequestPermissionsResult(kod, izinler, sonuclar);
        if (kod == IZIN_KODU && izinVar()) {
            konumBaslat();
            havaGuncelle(true);
        }
    }

    private void duvarKagidiSec() {
        try {
            startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SET_WALLPAPER), "Duvar kağıdı seç"));
        } catch (Exception e) {
            Toast.makeText(this, "Duvar kağıdı ayarı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- Çalan müzik ----------------

    private void muzikBaslat() {
        MedyaDinleyici.dinleyen = () -> handler.post(this::oturumlariYenile);
        if (!aliciKayitli) {
            IntentFilter f = new IntentFilter();
            for (String e : YAYIN_EYLEMLERI) f.addAction(e);
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(yayinAlici, f, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(yayinAlici, f);
                }
                aliciKayitli = true;
            } catch (Exception ignored) {
            }
        }
        try {
            oturumYoneticisi.addOnActiveSessionsChangedListener(oturumDinleyici, dinleyici, handler);
            bildirimIzniVar = true;
        } catch (SecurityException e) {
            bildirimIzniVar = false;
        }
        oturumlariYenile();
    }

    private void muzikDurdur() {
        MedyaDinleyici.dinleyen = null;
        if (aliciKayitli) {
            try {
                unregisterReceiver(yayinAlici);
            } catch (Exception ignored) {
            }
            aliciKayitli = false;
        }
        try {
            oturumYoneticisi.removeOnActiveSessionsChangedListener(oturumDinleyici);
        } catch (Exception ignored) {
        }
        if (aktif != null) {
            aktif.unregisterCallback(medyaGeriCagri);
            aktif = null;
        }
    }

    private void oturumlariYenile() {
        MediaController secilen = null;
        if (bildirimIzniVar) {
            try {
                List<MediaController> liste = oturumYoneticisi.getActiveSessions(dinleyici);
                for (MediaController mc : liste) {
                    PlaybackState s = mc.getPlaybackState();
                    if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                        secilen = mc;
                        break;
                    }
                }
                if (secilen == null && !liste.isEmpty()) secilen = liste.get(0);
            } catch (SecurityException e) {
                bildirimIzniVar = false;
            }
        }
        if (secilen == null) secilen = bildirimdenOturum();
        if (secilen == null) bildirimdenMetin();
        if (aktif != null) aktif.unregisterCallback(medyaGeriCagri);
        aktif = secilen;
        if (aktif != null) aktif.registerCallback(medyaGeriCagri, handler);
        muzikGoster();
    }

    private void muzikGoster() {
        if (!bildirimIzniVar) {
            kaynak.setText("İzin gerekli");
            sarki.setText("Çalan şarkıyı görmek için dokun");
            sanatci.setText("Bildirim erişimi → Araç Ekranı'nı aç");
            kapak.setImageResource(R.drawable.ic_muzik);
            oynat.setText("▶");
            sureleriSifirla();
            return;
        }
        if (aktif == null && harBaslik != null
                && System.currentTimeMillis() - harZaman < HAR_GECERLILIK_MS) {
            kaynak.setText(harKaynak != null ? harKaynak : "Bluetooth müzik");
            sarki.setText(harBaslik);
            sanatci.setText(harKisi != null ? harKisi : "");
            kapak.setImageResource(R.drawable.ic_muzik);
            sureleriSifirla();
            return;
        }
        if (aktif == null) {
            kaynak.setText("Müzik");
            sarki.setText("Şu an çalan yok");
            sanatci.setText("");
            kapak.setImageResource(R.drawable.ic_muzik);
            oynat.setText("▶");
            sureleriSifirla();
            return;
        }

        kaynak.setText(uygulamaAdi(aktif.getPackageName()));
        MediaMetadata m = aktif.getMetadata();
        String baslik = null, kisi = null;
        Bitmap resim = null;
        if (m != null) {
            baslik = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            kisi = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (kisi == null) kisi = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            resim = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (resim == null) resim = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (resim == null) resim = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        sarki.setText(baslik != null ? baslik : "Bilinmeyen parça");
        sanatci.setText(kisi != null ? kisi : "");
        if (resim != null) kapak.setImageBitmap(resim);
        else kapak.setImageResource(R.drawable.ic_muzik);

        PlaybackState s = aktif.getPlaybackState();
        boolean caliyor = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        oynat.setText(caliyor ? "❚❚" : "▶");
        ilerlemeGuncelle();
    }

    private void ilerlemeGuncelle() {
        if (aktif == null) return;
        MediaMetadata m = aktif.getMetadata();
        PlaybackState s = aktif.getPlaybackState();
        long sure = m != null ? m.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        if (s == null || sure <= 0) {
            sureleriSifirla();
            return;
        }
        long konumMs = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            konumMs += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime())
                    * s.getPlaybackSpeed());
        }
        konumMs = Math.max(0, Math.min(konumMs, sure));
        ilerleme.setProgress((int) (konumMs * 1000 / sure));
        gecen.setText(sureYaz(konumMs));
        toplam.setText(sureYaz(sure));
    }

    /** Medya bildirimindeki oturum anahtarından denetleyici oluştur. */
    private MediaController bildirimdenOturum() {
        for (StatusBarNotification sbn : MedyaDinleyici.bildirimler()) {
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) continue;
            Object t = n.extras.get(Notification.EXTRA_MEDIA_SESSION);
            if (t instanceof MediaSession.Token) {
                try {
                    return new MediaController(this, (MediaSession.Token) t);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /** Medya oturumu yoksa: müzik bildiriminin başlık/metninden şarkıyı al. */
    private void bildirimdenMetin() {
        for (StatusBarNotification sbn : MedyaDinleyici.bildirimler()) {
            if (sbn.getPackageName().equals(getPackageName())) continue;
            if (!MedyaDinleyici.medyaBildirimiMi(sbn)) continue;
            Bundle x = sbn.getNotification().extras;
            if (x == null) continue;
            CharSequence b = x.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence t = x.getCharSequence(Notification.EXTRA_TEXT);
            if (b == null || b.length() == 0) continue;
            harBaslik = b.toString();
            harKisi = t != null ? t.toString() : null;
            harKaynak = uygulamaAdi(sbn.getPackageName());
            harZaman = System.currentTimeMillis();
            return;
        }
    }

    /** Ünitelerin yaydığı şarkı bilgisi sinyallerini işle. */
    private void yayinIsle(Intent i) {
        String eylem = String.valueOf(i.getAction());
        StringBuilder ozet = new StringBuilder(eylem);
        Bundle x = i.getExtras();
        String baslik = null, kisi = null;
        if (x != null) {
            for (String k : x.keySet()) {
                Object v = x.get(k);
                ozet.append("\n   ").append(k).append(" = ").append(v);
                if (v instanceof MediaMetadata) {
                    MediaMetadata m = (MediaMetadata) v;
                    baslik = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                    kisi = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                }
            }
            if (baslik == null) baslik = ilkMetin(x, "track", "title", "name", "song", "songname", "id3_title");
            if (kisi == null) kisi = ilkMetin(x, "artist", "singer", "id3_artist");
        }
        synchronized (sonYayinlar) {
            sonYayinlar.addFirst(new SimpleDateFormat("HH:mm:ss", TR).format(new Date()) + "  " + ozet);
            while (sonYayinlar.size() > 6) sonYayinlar.removeLast();
        }
        if (baslik != null && !baslik.isEmpty()) {
            harBaslik = baslik;
            harKisi = kisi;
            harKaynak = eylem.contains("bluetooth") || eylem.contains("bt") ? "Bluetooth müzik" : "Müzik";
            harZaman = System.currentTimeMillis();
            if (aktif == null) muzikGoster();
        }
    }

    private static String ilkMetin(Bundle x, String... anahtarlar) {
        for (String k : anahtarlar) {
            Object v = x.get(k);
            if (v != null && !String.valueOf(v).isEmpty()) return String.valueOf(v);
        }
        return null;
    }

    /** Müzik kartına uzun basınca: neyin görüldüğünü göster (sorun tespiti için). */
    private void teshisGoster() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bildirim erişimi: ").append(bildirimIzniVar ? "VAR" : "YOK").append('\n');
        sb.append("Dinleyici bağlı: ").append(MedyaDinleyici.ornek != null ? "EVET" : "HAYIR").append("\n\n");

        sb.append("Medya oturumları:\n");
        try {
            List<MediaController> liste = oturumYoneticisi.getActiveSessions(dinleyici);
            if (liste.isEmpty()) sb.append("   (yok)\n");
            for (MediaController mc : liste) {
                PlaybackState st = mc.getPlaybackState();
                sb.append("   ").append(mc.getPackageName())
                        .append("  durum=").append(st != null ? st.getState() : -1).append('\n');
            }
        } catch (Exception e) {
            sb.append("   okunamadı: ").append(e.getClass().getSimpleName()).append('\n');
        }

        sb.append("\nBildirimler:\n");
        StatusBarNotification[] b = MedyaDinleyici.bildirimler();
        if (b.length == 0) sb.append("   (yok)\n");
        for (StatusBarNotification sbn : b) {
            Bundle x = sbn.getNotification().extras;
            sb.append("   ").append(sbn.getPackageName());
            if (x != null) {
                sb.append(" | ").append(x.getCharSequence(Notification.EXTRA_TITLE))
                        .append(" | ").append(x.getCharSequence(Notification.EXTRA_TEXT));
            }
            sb.append('\n');
        }

        sb.append("\nSon sinyaller:\n");
        synchronized (sonYayinlar) {
            if (sonYayinlar.isEmpty()) sb.append("   (yok)\n");
            for (String y : sonYayinlar) sb.append("   ").append(y).append('\n');
        }

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextIsSelectable(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setPadding(dp(20), dp(12), dp(20), dp(12));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Müzik teşhis")
                .setView(sv)
                .setPositiveButton("Kapat", null)
                .show();
    }

    private void sureleriSifirla() {
        ilerleme.setProgress(0);
        gecen.setText("");
        toplam.setText("");
    }

    private static String sureYaz(long ms) {
        long sn = ms / 1000;
        return String.format(Locale.US, "%d:%02d", sn / 60, sn % 60);
    }

    private String uygulamaAdi(String paket) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(paket, 0)));
        } catch (Exception e) {
            return "Müzik";
        }
    }

    private void muzikUygulamasiniAc() {
        if (!bildirimIzniVar) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Ayarlar → Bildirim erişimi bölümünden izin ver",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (aktif != null) Kisayollar.baslat(this, aktif.getPackageName());
    }

    private void onceki() {
        if (aktif != null) aktif.getTransportControls().skipToPrevious();
        else medyaTusu(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    private void sonraki() {
        if (aktif != null) aktif.getTransportControls().skipToNext();
        else medyaTusu(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    private void oynatDuraklat() {
        if (aktif != null) {
            PlaybackState s = aktif.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                aktif.getTransportControls().pause();
            } else {
                aktif.getTransportControls().play();
            }
        } else {
            medyaTusu(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    private void medyaTusu(int kod) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, kod));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, kod));
    }

    // ---------------- Konum (hava durumu için) ----------------

    private boolean izinVar() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void konumBaslat() {
        if (!izinVar()) return;
        try {
            if (konum.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                konum.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 500, this);
            }
            if (konum.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                konum.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 500, this);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (sonHavaKonumu == null || sonHavaKonumu.distanceTo(l) > 10000) {
            sonHavaZamani = 0;
        }
        havaGuncelle(false);
    }

    // Eski Android sürümlerinde zorunlu metotlar
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) { konumBaslat(); }
    @Override public void onProviderDisabled(String provider) {}

    private Location enIyiKonum() {
        if (!izinVar()) return null;
        Location en = null;
        try {
            for (String p : konum.getProviders(true)) {
                Location l = konum.getLastKnownLocation(p);
                if (l != null && (en == null || l.getTime() > en.getTime())) en = l;
            }
        } catch (SecurityException ignored) {
        }
        return en;
    }

    // ---------------- Hava durumu (Open-Meteo, anahtar gerekmez) ----------------

    private void havaGuncelle(boolean elle) {
        if (havaYukleniyor) return;
        long simdi = System.currentTimeMillis();
        if (!elle && sonHavaZamani != 0 && simdi - sonHavaZamani < HAVA_ARALIGI_MS) return;

        final Location l = enIyiKonum();
        if (l == null) {
            if (elle) havaDurum.setText("Konum bekleniyor");
            return;
        }
        havaYukleniyor = true;
        if (elle) havaDurum.setText("Yükleniyor…");
        final double enlem = l.getLatitude();
        final double boylam = l.getLongitude();

        new Thread(() -> {
            String sicak = null, durum;
            int kodH = -1;
            try {
                URL url = new URL(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code&timezone=auto",
                        enlem, boylam));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String satir;
                    while ((satir = r.readLine()) != null) sb.append(satir);
                } finally {
                    c.disconnect();
                }
                JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
                sicak = Math.round(cur.getDouble("temperature_2m")) + "°";
                kodH = cur.getInt("weather_code");
                durum = havaMetni(kodH);
                String sehir = sehirBul(enlem, boylam);
                if (sehir != null) durum = sehir + " · " + durum;
            } catch (Exception e) {
                durum = "İnternet yok";
            }
            final String s = sicak, d = durum;
            final int kodSon = kodH;
            handler.post(() -> {
                havaYukleniyor = false;
                if (s != null) {
                    sicaklik.setText(s);
                    sonHavaZamani = System.currentTimeMillis();
                    sonHavaKonumu = l;
                    havaIkon.setImageResource(havaIkonu(kodSon));
                }
                havaDurum.setText(d);
            });
        }).start();
    }

    private String sehirBul(double enlem, double boylam) {
        try {
            if (!Geocoder.isPresent()) return null;
            List<Address> a = new Geocoder(this, TR).getFromLocation(enlem, boylam, 1);
            if (a == null || a.isEmpty()) return null;
            Address ad = a.get(0);
            if (ad.getLocality() != null) return ad.getLocality();
            if (ad.getSubAdminArea() != null) return ad.getSubAdminArea();
            return ad.getAdminArea();
        } catch (Exception e) {
            return null;
        }
    }

    private static int havaIkonu(int kod) {
        if (kod == 0 || kod == 1) return R.drawable.ic_gunes;
        if ((kod >= 51 && kod <= 67) || (kod >= 80 && kod <= 82) || kod >= 95) return R.drawable.ic_yagmur;
        if ((kod >= 71 && kod <= 77) || kod == 85 || kod == 86) return R.drawable.ic_kar;
        return R.drawable.ic_bulut;
    }

    private static String havaMetni(int kod) {
        if (kod == 0) return "Açık";
        if (kod == 1) return "Az bulutlu";
        if (kod == 2) return "Parçalı bulutlu";
        if (kod == 3) return "Kapalı";
        if (kod == 45 || kod == 48) return "Sisli";
        if (kod >= 51 && kod <= 57) return "Çisenti";
        if (kod >= 61 && kod <= 67) return "Yağmurlu";
        if (kod >= 71 && kod <= 77) return "Karlı";
        if (kod >= 80 && kod <= 82) return "Sağanak";
        if (kod == 85 || kod == 86) return "Kar sağanağı";
        if (kod >= 95) return "Gök gürültülü";
        return "—";
    }

    // ---------------- Kısayollar (dok) ----------------

    private static final int SUTUN = 5, SATIR = 1;

    /** Kısayollar: 4 büyük ikon + sonda "Tümü". */
    private void dokuKur() {
        dok.removeAllViews();
        PackageManager pm = getPackageManager();
        List<String> paketler = Kisayollar.oku(this);
        List<View> hucreler = new java.util.ArrayList<>();

        for (String paket : paketler) {
            Drawable ikonTmp;
            CharSequence adTmp;
            if (Kisayollar.NAV.equals(paket)) {
                ikonTmp = navIkonu();
                adTmp = "Navigasyon";
            } else {
                try {
                    ikonTmp = pm.getApplicationIcon(paket);
                    adTmp = pm.getApplicationLabel(pm.getApplicationInfo(paket, 0));
                } catch (PackageManager.NameNotFoundException e) {
                    continue; // kaldırılmış uygulama
                }
            }
            final CharSequence ad = adTmp;
            View h = hucre(ikonTmp, ad);
            h.setOnClickListener(v -> {
                if (!Kisayollar.baslat(this, paket)) {
                    Toast.makeText(this,
                            Kisayollar.NAV.equals(paket) ? "Navigasyon uygulaması bulunamadı" : "Açılamadı",
                            Toast.LENGTH_SHORT).show();
                }
            });
            h.setOnLongClickListener(v -> {
                Kisayollar.degistir(this, paket);
                Toast.makeText(this, ad + " ana ekrandan çıkarıldı", Toast.LENGTH_SHORT).show();
                dokuKur();
                return true;
            });
            hucreler.add(h);
            if (hucreler.size() >= SUTUN * SATIR - 1) break;
        }

        View tumu = hucre(tumuIkonu(), "Tümü");
        tumu.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        tumu.setOnLongClickListener(v -> {
            ayarlarGoster();
            return true;
        });
        hucreler.add(tumu);

        for (int r = 0; r < SATIR; r++) {
            LinearLayout satir = new LinearLayout(this);
            satir.setOrientation(LinearLayout.HORIZONTAL);
            dok.addView(satir, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            for (int c = 0; c < SUTUN; c++) {
                int i = r * SUTUN + c;
                View h;
                if (i < hucreler.size()) {
                    h = hucreler.get(i);
                } else {
                    h = new View(this); // boş hücre
                }
                satir.addView(h, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
        }
    }

    private View hucre(Drawable ikon, CharSequence ad) {
        LinearLayout kutu = new LinearLayout(this);
        kutu.setOrientation(LinearLayout.VERTICAL);
        kutu.setGravity(Gravity.CENTER);
        TypedValue arkaplan = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, arkaplan, true);
        kutu.setBackgroundResource(arkaplan.resourceId);
        kutu.setClickable(true);
        int p = dp(8);
        kutu.setPadding(p, p, p, dp(4));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(ikon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        kutu.addView(iv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f));

        TextView tv = new TextView(this);
        tv.setText(ad);
        tv.setTextColor(yaziRenk);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setShadowLayer(6, 0, 1, 0xA0000000);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(6);
        kutu.addView(tv, tlp);
        return kutu;
    }

    private Drawable tumuIkonu() {
        GradientDrawable kare = new GradientDrawable();
        kare.setColor(tusRenk);
        kare.setCornerRadius(dp(22));
        Drawable izgara = getDrawable(R.drawable.ic_izgara).mutate();
        izgara.setTint(yaziRenk);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{kare, izgara});
        ld.setLayerSize(0, dp(84), dp(84));
        ld.setLayerSize(1, dp(36), dp(36));
        ld.setLayerInset(1, dp(24), dp(24), dp(24), dp(24));
        return ld;
    }

    private GradientDrawable daire(int renk) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(renk);
        return g;
    }

    /** Ünitenin "Telefon" uygulamasını ada göre bulup açar; yoksa arama ekranı. */
    private void telefonAc() {
        PackageManager pm = getPackageManager();
        Intent sorgu = new Intent(Intent.ACTION_MAIN);
        sorgu.addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(sorgu, 0)) {
            String ad = String.valueOf(ri.loadLabel(pm)).trim();
            if (ad.equalsIgnoreCase("Telefon") || ad.equalsIgnoreCase("Phone")
                    || ad.equalsIgnoreCase("Bluetooth")) {
                if (Kisayollar.baslat(this, ri.activityInfo.packageName)) return;
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Telefon uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- Tema ve görünüm ayarları ----------------

    private SharedPreferences ayarlar() {
        return getSharedPreferences("ayarlar", MODE_PRIVATE);
    }

    private static int karistir(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void temaUygula() {
        SharedPreferences sp = ayarlar();
        int secilen = sp.getInt("vurgu", VURGU_RENKLERI[0]);
        String mod = sp.getString("geceModu", "oto");
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        gece = mod.equals("acik") || (mod.equals("oto") && (h >= 19 || h < 7));

        vurgu = gece ? karistir(secilen, 0xFF000000, 0.38f) : secilen;
        int kart = gece ? 0xEB0E1014 : 0xB8141A22;
        int tus = gece ? 0xFF15181D : 0xE626313F;
        int kapakR = gece ? 0xFF15181D : 0xFF26313F;
        int iz = gece ? 0xFF1D2127 : 0xFF364150;
        yaziRenk = gece ? 0xFFAEB6BF : 0xFFF2F5F8;
        int soluk = gece ? 0xFF7A838D : 0xFFAFB9C4;

        kok.setBackgroundColor(gece ? 0xB8000000 : 0x29000000);
        ColorStateList kartT = ColorStateList.valueOf(kart);
        muzikKart.setBackgroundTintList(kartT);
        dok.setBackgroundTintList(kartT);
        tusRenk = tus;
        kapak.setBackgroundTintList(ColorStateList.valueOf(kapakR));


        navBtn.setBackground(daire(vurgu));
        telBtn.setBackground(daire(gece ? 0xFF24551A : 0xFF3B8A1E));
        ayrac.setBackgroundColor(gece ? 0xFF2A2E34 : 0x55FFFFFF);

        ilerleme.setProgressTintList(ColorStateList.valueOf(yaziRenk));
        ilerleme.setProgressBackgroundTintList(ColorStateList.valueOf(iz));
        kaynak.setTextColor(vurgu);
        havaIkon.setColorFilter(yaziRenk);

        for (TextView t : new TextView[]{saat, tarih, sicaklik, havaDurum, sarki, oncekiV, oynat, sonrakiV}) {
            t.setTextColor(yaziRenk);
        }
        for (TextView t : new TextView[]{sanatci, gecen, toplam}) {
            t.setTextColor(soluk);
        }
        dokuKur();
    }

    private Drawable navIkonu() {
        GradientDrawable daire = new GradientDrawable();
        daire.setShape(GradientDrawable.OVAL);
        daire.setColor(vurgu);
        Drawable ok = getDrawable(R.drawable.ic_nav_ok);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{daire, ok});
        ld.setLayerSize(0, dp(84), dp(84));
        ld.setLayerSize(1, dp(40), dp(40));
        ld.setLayerInset(1, dp(22), dp(22), dp(22), dp(22));
        return ld;
    }

    private void ayarlarGoster() {
        String[] secenekler = {
                "Vurgu rengi",
                "Gece modu",
                "Hazır duvar kağıdı: Kırmızı–mavi",
                "Kendi resmimi duvar kağıdı yap"
        };
        new AlertDialog.Builder(this)
                .setTitle("Görünüm")
                .setItems(secenekler, (d, w) -> {
                    if (w == 0) renkSec();
                    else if (w == 1) geceSec();
                    else if (w == 2) hazirDuvarKagidi();
                    else duvarKagidiSec();
                })
                .show();
    }

    private void renkSec() {
        int simdiki = ayarlar().getInt("vurgu", VURGU_RENKLERI[0]);
        int sec = 0;
        for (int i = 0; i < VURGU_RENKLERI.length; i++) if (VURGU_RENKLERI[i] == simdiki) sec = i;
        new AlertDialog.Builder(this)
                .setTitle("Vurgu rengi")
                .setSingleChoiceItems(VURGU_ADLARI, sec, (d, w) -> {
                    ayarlar().edit().putInt("vurgu", VURGU_RENKLERI[w]).apply();
                    d.dismiss();
                    temaUygula();
                })
                .show();
    }

    private void geceSec() {
        String[] adlar = {"Otomatik (19:00–07:00 arası)", "Her zaman açık", "Kapalı"};
        String[] anahtar = {"oto", "acik", "kapali"};
        String simdiki = ayarlar().getString("geceModu", "oto");
        int sec = 0;
        for (int i = 0; i < anahtar.length; i++) if (anahtar[i].equals(simdiki)) sec = i;
        new AlertDialog.Builder(this)
                .setTitle("Gece modu")
                .setSingleChoiceItems(adlar, sec, (d, w) -> {
                    ayarlar().edit().putString("geceModu", anahtar[w]).apply();
                    d.dismiss();
                    temaUygula();
                })
                .show();
    }

    private void hazirDuvarKagidi() {
        Toast.makeText(this, "Duvar kağıdı yükleniyor…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean tamam;
            try {
                WallpaperManager.getInstance(this).setResource(R.drawable.duvar_kirmizi_mavi);
                tamam = true;
            } catch (Exception e) {
                tamam = false;
            }
            final boolean t = tamam;
            handler.post(() -> Toast.makeText(this,
                    t ? "Duvar kağıdı ayarlandı" : "Duvar kağıdı ayarlanamadı",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }

    private int dp(int deger) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, deger, getResources().getDisplayMetrics()));
    }
}
