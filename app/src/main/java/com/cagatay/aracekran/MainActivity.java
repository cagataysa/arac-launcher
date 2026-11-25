package com.cagatay.aracekran;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {

    private static final int IZIN_KODU = 42;
    private static final long HAVA_ARALIGI_MS = 30 * 60 * 1000L; // 30 dk
    private static final long HIZ_ZAMAN_ASIMI_MS = 5000L;

    /** Navigasyon butonu: sırayla denenir, ilk yüklü olan açılır. */
    private static final String[] NAV_PAKETLERI = {
            "com.google.android.apps.maps",
            "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps",
            "com.waze",
            "com.here.app.maps"
    };

    private static final Locale TR = new Locale("tr", "TR");

    private TextView saat, tarih, hiz, sicaklik, havaDurum;
    private LinearLayout dok;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat saatFmt = new SimpleDateFormat("HH:mm", TR);
    private final SimpleDateFormat tarihFmt = new SimpleDateFormat("d MMMM EEEE", TR);

    private LocationManager konum;
    private long sonKonumZamani = 0;
    private Location sonHavaKonumu;
    private long sonHavaZamani = 0;
    private boolean havaYukleniyor = false;

    private final Runnable saniyelik = new Runnable() {
        @Override
        public void run() {
            Date simdi = new Date();
            saat.setText(saatFmt.format(simdi));
            tarih.setText(tarihFmt.format(simdi));
            if (sonKonumZamani == 0
                    || SystemClock.elapsedRealtime() - sonKonumZamani > HIZ_ZAMAN_ASIMI_MS) {
                hiz.setText("--");
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        saat = findViewById(R.id.saat);
        tarih = findViewById(R.id.tarih);
        hiz = findViewById(R.id.hiz);
        sicaklik = findViewById(R.id.sicaklik);
        havaDurum = findViewById(R.id.havaDurum);
        dok = findViewById(R.id.dok);

        konum = (LocationManager) getSystemService(LOCATION_SERVICE);

        findViewById(R.id.navigasyon).setOnClickListener(v -> navigasyonAc());
        findViewById(R.id.onceki).setOnClickListener(v -> medyaTusu(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        findViewById(R.id.oynat).setOnClickListener(v -> medyaTusu(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        findViewById(R.id.sonraki).setOnClickListener(v -> medyaTusu(KeyEvent.KEYCODE_MEDIA_NEXT));
        findViewById(R.id.uygulamalar).setOnClickListener(v ->
                startActivity(new Intent(this, AppsActivity.class)));
        findViewById(R.id.havaKart).setOnClickListener(v -> {
            sonHavaZamani = 0;
            havaGuncelle(true);
        });

        if (!izinVar()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, IZIN_KODU);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(saniyelik);
        handler.post(saniyelik);
        dokuKur();
        konumBaslat();
        havaGuncelle(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(saniyelik);
        try {
            konum.removeUpdates(this);
        } catch (Exception ignored) {
        }
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

    // ---------------- Konum / hız ----------------

    private boolean izinVar() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void konumBaslat() {
        if (!izinVar()) return;
        try {
            if (konum.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                konum.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            }
            if (konum.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                konum.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 500, this);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (LocationManager.GPS_PROVIDER.equals(l.getProvider())) {
            sonKonumZamani = SystemClock.elapsedRealtime();
            float kmsa = l.hasSpeed() ? l.getSpeed() * 3.6f : 0f;
            if (kmsa < 2f) kmsa = 0f;
            hiz.setText(String.valueOf(Math.round(kmsa)));
        }
        // Hava durumunu 10 km'den fazla yer değiştirince de yenile
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

        Location l = enIyiKonum();
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
                durum = havaMetni(cur.getInt("weather_code"));
            } catch (Exception e) {
                durum = "İnternet yok";
            }
            final String s = sicak, d = durum;
            handler.post(() -> {
                havaYukleniyor = false;
                if (s != null) {
                    sicaklik.setText(s);
                    sonHavaZamani = System.currentTimeMillis();
                    sonHavaKonumu = l;
                }
                havaDurum.setText(d);
            });
        }).start();
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

    // ---------------- Navigasyon & müzik ----------------

    private void navigasyonAc() {
        for (String p : NAV_PAKETLERI) {
            if (Kisayollar.baslat(this, p)) return;
        }
        Toast.makeText(this, "Navigasyon uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
    }

    private void medyaTusu(int kod) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, kod));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, kod));
    }

    // ---------------- Kısayollar (dok) ----------------

    private void dokuKur() {
        dok.removeAllViews();
        PackageManager pm = getPackageManager();
        List<String> paketler = Kisayollar.oku(this);
        int eklenen = 0;

        for (String paket : paketler) {
            Drawable ikonTmp;
            CharSequence adTmp;
            try {
                ikonTmp = pm.getApplicationIcon(paket);
                adTmp = pm.getApplicationLabel(pm.getApplicationInfo(paket, 0));
            } catch (PackageManager.NameNotFoundException e) {
                continue; // kaldırılmış uygulama
            }
            final Drawable ikon = ikonTmp;
            final CharSequence ad = adTmp;

            LinearLayout kutu = new LinearLayout(this);
            kutu.setOrientation(LinearLayout.VERTICAL);
            kutu.setGravity(Gravity.CENTER);
            kutu.setBackgroundResource(R.drawable.buton_bg);
            kutu.setClickable(true);
            int p = dp(4);
            kutu.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            kutu.setLayoutParams(lp);

            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ikon);
            iv.setAdjustViewBounds(true);
            iv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f));

            TextView tv = new TextView(this);
            tv.setText(ad);
            tv.setTextColor(getColor(R.color.yazi));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            kutu.addView(iv);
            kutu.addView(tv);

            kutu.setOnClickListener(v -> {
                if (!Kisayollar.baslat(this, paket)) {
                    Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
                }
            });
            kutu.setOnLongClickListener(v -> {
                Kisayollar.degistir(this, paket);
                Toast.makeText(this, ad + " kısayollardan çıkarıldı", Toast.LENGTH_SHORT).show();
                dokuKur();
                return true;
            });

            dok.addView(kutu);
            eklenen++;
        }

        if (eklenen == 0) {
            TextView ipucu = new TextView(this);
            ipucu.setText("Kısayol eklemek için: Tüm uygulamalar → uygulamaya uzun bas");
            ipucu.setTextColor(getColor(R.color.soluk));
            ipucu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            ipucu.setGravity(Gravity.CENTER);
            dok.addView(ipucu, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private int dp(int deger) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, deger, getResources().getDisplayMetrics()));
    }
}
