package com.cagatay.aracekran;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

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
            "com.nng.igo.primong.igoworld",
            "com.nng.igoprimoisrael.javaclient",
            "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps",
            "com.waze",
            "com.here.app.maps"
    };

    /**
     * İlk açılışta dok'a konacaklar. Ünitenin kendi uygulamalarının paket adları
     * üreticiye göre değiştiği için ekranda görünen adlarıyla eşleştirilir.
     */
    private static final String[] VARSAYILAN_ADLAR = {
            "Spotify",
            "YT Music",
            "Telefon",
            "@carplay",
            "Radyo",
            "Ayarlar"
    };

    private Kisayollar() {}

    static List<String> oku(Context c) {
        SharedPreferences sp = c.getSharedPreferences(DOSYA, Context.MODE_PRIVATE);
        String kayit = sp.getString(ANAHTAR, null);
        List<String> liste = new ArrayList<>();
        if (kayit == null) {
            liste.add(NAV);
            PackageManager pm = c.getPackageManager();
            Intent sorgu = new Intent(Intent.ACTION_MAIN);
            sorgu.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> uygulamalar = pm.queryIntentActivities(sorgu, 0);
            for (String hedef : VARSAYILAN_ADLAR) {
                if (liste.size() >= EN_FAZLA) break;
                for (ResolveInfo ri : uygulamalar) {
                    String ad = String.valueOf(ri.loadLabel(pm)).trim();
                    String paket = ri.activityInfo.packageName;
                    boolean uyar = hedef.equals("@carplay")
                            ? carplayMi(ad, paket)
                            : ad.equalsIgnoreCase(hedef);
                    if (uyar && !liste.contains(paket)
                            && !paket.equals(c.getPackageName())) {
                        liste.add(paket);
                        break;
                    }
                }
            }
            yaz(c, liste);
            return liste;
        }
        for (String p : kayit.split(",")) {
            if (!p.isEmpty()) liste.add(p);
        }
        return liste;
    }

    /** CarPlay / telefon bağlantı uygulamaları ünitelerde farklı adlarla gelir. */
    private static boolean carplayMi(String ad, String paket) {
        String a = ad.toLowerCase(new java.util.Locale("tr", "TR"));
        String p = paket.toLowerCase(java.util.Locale.ROOT);
        return a.contains("kolay") || a.contains("carplay") || a.contains("zlink")
                || a.contains("autokit") || a.contains("easyconn") || a.contains("tlink")
                || p.contains("zlink") || p.contains("carplay") || p.contains("easyconn")
                || p.contains("autokit") || p.contains("tlink");
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
