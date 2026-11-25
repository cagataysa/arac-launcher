package com.cagatay.aracekran;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/** Ana ekrandaki kısayol (dok) listesini saklar. */
final class Kisayollar {

    static final int EN_FAZLA = 6;
    private static final String DOSYA = "ayarlar";
    private static final String ANAHTAR = "dok";

    /** İlk açılışta, cihazda yüklüyse dok'a konacak uygulamalar. */
    /** Dok'ta navigasyonu temsil eden özel anahtar. */
    static final String NAV = "@nav";

    /** Navigasyon: sırayla denenir, ilk yüklü olan açılır. */
    static final String[] NAV_PAKETLERI = {
            "com.google.android.apps.maps",
            "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps",
            "com.waze",
            "com.here.app.maps"
    };

    private static final String[] VARSAYILAN = {
            NAV,
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.whatsapp",
            "com.android.chrome",
            "com.android.settings"
    };

    private Kisayollar() {}

    static List<String> oku(Context c) {
        SharedPreferences sp = c.getSharedPreferences(DOSYA, Context.MODE_PRIVATE);
        String kayit = sp.getString(ANAHTAR, null);
        List<String> liste = new ArrayList<>();
        if (kayit == null) {
            PackageManager pm = c.getPackageManager();
            for (String p : VARSAYILAN) {
                if (liste.size() >= EN_FAZLA) break;
                if (p.equals(NAV) || pm.getLaunchIntentForPackage(p) != null) liste.add(p);
            }
            yaz(c, liste);
            return liste;
        }
        for (String p : kayit.split(",")) {
            if (!p.isEmpty()) liste.add(p);
        }
        return liste;
    }

    static void yaz(Context c, List<String> liste) {
        c.getSharedPreferences(DOSYA, Context.MODE_PRIVATE)
                .edit().putString(ANAHTAR, String.join(",", liste)).apply();
    }

    /** @return true eklendiyse, false çıkarıldıysa; null dok doluysa. */
    static Boolean degistir(Context c, String paket) {
        List<String> liste = oku(c);
        if (liste.remove(paket)) {
            yaz(c, liste);
            return false;
        }
        if (liste.size() >= EN_FAZLA) return null;
        liste.add(paket);
        yaz(c, liste);
        return true;
    }

    static boolean baslat(Context c, String paket) {
        if (NAV.equals(paket)) {
            for (String p : NAV_PAKETLERI) {
                if (baslat(c, p)) return true;
            }
            return false;
        }
        Intent i = c.getPackageManager().getLaunchIntentForPackage(paket);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
