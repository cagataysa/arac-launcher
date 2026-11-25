package com.cagatay.aracekran;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppsActivity extends Activity {

    static final class Uygulama {
        String paket;
        String ad;
        Drawable ikon;
    }

    private final List<Uygulama> uygulamalar = new ArrayList<>();
    private Adaptor adaptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        GridView izgara = findViewById(R.id.izgara);
        adaptor = new Adaptor();
        izgara.setAdapter(adaptor);

        izgara.setOnItemClickListener((parent, view, pos, id) -> {
            Uygulama u = uygulamalar.get(pos);
            if (!Kisayollar.baslat(this, u.paket)) {
                Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
            }
        });

        izgara.setOnItemLongClickListener((parent, view, pos, id) -> {
            Uygulama u = uygulamalar.get(pos);
            Boolean sonuc = Kisayollar.degistir(this, u.paket);
            String mesaj;
            if (sonuc == null) {
                mesaj = "Kısayollar dolu (en fazla " + Kisayollar.EN_FAZLA + "). Önce birini çıkar.";
            } else if (sonuc) {
                mesaj = u.ad + " ana ekrana eklendi";
            } else {
                mesaj = u.ad + " ana ekrandan çıkarıldı";
            }
            Toast.makeText(this, mesaj, Toast.LENGTH_SHORT).show();
            return true;
        });

        findViewById(R.id.geri).setOnClickListener(v -> finish());

        yukle();
    }

    private void yukle() {
        Handler ana = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent sorgu = new Intent(Intent.ACTION_MAIN);
            sorgu.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> sonuclar = pm.queryIntentActivities(sorgu, 0);
            List<Uygulama> liste = new ArrayList<>();
            for (ResolveInfo ri : sonuclar) {
                String paket = ri.activityInfo.packageName;
                if (paket.equals(getPackageName())) continue;
                boolean varMi = false;
                for (Uygulama x : liste) {
                    if (x.paket.equals(paket)) { varMi = true; break; }
                }
                if (varMi) continue;
                Uygulama u = new Uygulama();
                u.paket = paket;
                u.ad = String.valueOf(ri.loadLabel(pm));
                u.ikon = ri.loadIcon(pm);
                liste.add(u);
            }
            Collator tr = Collator.getInstance(new Locale("tr", "TR"));
            liste.sort((a, b) -> tr.compare(a.ad, b.ad));
            Uygulama nav = new Uygulama();
            nav.paket = Kisayollar.NAV;
            nav.ad = "Navigasyon";
            nav.ikon = getDrawable(R.drawable.ic_nav);
            liste.add(0, nav);
            ana.post(() -> {
                uygulamalar.clear();
                uygulamalar.addAll(liste);
                adaptor.notifyDataSetChanged();
            });
        }).start();
    }

    private class Adaptor extends BaseAdapter {
        @Override public int getCount() { return uygulamalar.size(); }
        @Override public Object getItem(int i) { return uygulamalar.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) {
                v = LayoutInflater.from(AppsActivity.this).inflate(R.layout.item_app, parent, false);
            }
            Uygulama u = uygulamalar.get(i);
            ((ImageView) v.findViewById(R.id.ikon)).setImageDrawable(u.ikon);
            ((TextView) v.findViewById(R.id.ad)).setText(u.ad);
            return v;
        }
    }
}
