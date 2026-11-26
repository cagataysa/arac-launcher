package com.cagatay.aracekran;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;

/**
 * FYT (Syu) ünitelerinde Bluetooth müzik bilgisi Android'in standart medya sistemine gelmez;
 * ünitenin kendi servisinde (com.syu.ms, "com.syu.ms.toolkit") durur. Bu sınıf o servise bağlanıp
 * Bluetooth modülünün (2) şarkı adı / sanatçı / süre / çalma durumu güncellemelerini dinler
 * ve önceki / sonraki / oynat-duraklat komutlarını gönderir.
 *
 * Arayüz (ünite yazılımından): IRemoteToolkit.getRemoteModule(int) = işlem 1;
 * IRemoteModule: 1 cmd, 2 get, 3 register(callback, kod, 1), 4 unregister;
 * IModuleCallback.update(int kod, int[], float[], String[]) = işlem 1.
 */
final class FytBt {

    interface Dinleyici { void degisti(); }

    private static final String TK_DESC = "com.syu.ipc.IRemoteToolkit";
    private static final String MOD_DESC = "com.syu.ipc.IRemoteModule";
    private static final String CB_DESC = "com.syu.ipc.IModuleCallback";

    private static final int MODUL_ANA = 0, MODUL_BT = 2;
    // Bluetooth modülü güncelleme kodları
    private static final int U_BASLIK = 0, U_SANATCI = 1, U_SURE = 2, U_DURUM = 13, U_ALBUM = 26, U_KONUM = 28;
    private static final int[] BT_KODLARI = {U_BASLIK, U_SANATCI, U_SURE, U_DURUM, U_ALBUM, U_KONUM};
    // Bluetooth modülü komutları
    private static final int C_ONCEKI = 0, C_SONRAKI = 1, C_OYNAT_DURAKLAT = 2;

    private final Context ctx;
    private final Dinleyici dinleyici;
    private final Handler ana = new Handler(Looper.getMainLooper());

    private ServiceConnection baglanti;
    private IBinder btModul, anaModul;

    volatile String baslik = "", sanatci = "", album = "";
    volatile boolean caliyor = false;
    volatile long sureMs = 0, konumMs = 0, konumZamani = 0, sonGuncelleme = 0;
    volatile int kaynakId = -1;
    volatile String durumNotu = "bağlanmadı";

    FytBt(Context ctx, Dinleyici dinleyici) {
        this.ctx = ctx.getApplicationContext();
        this.dinleyici = dinleyici;
    }

    boolean bagli() { return btModul != null; }

    /** Bluetooth'tan gelmiş bir şarkı bilgisi var mı? */
    boolean bilgiVar() { return bagli() && baslik != null && !baslik.isEmpty(); }

    void baslat() {
        if (baglanti != null) return;
        baglanti = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder servis) {
                btModul = modulAl(servis, MODUL_BT);
                anaModul = modulAl(servis, MODUL_ANA);
                if (btModul != null) {
                    for (int k : BT_KODLARI) kaydet(btModul, geriCagri(MODUL_BT), k);
                    durumNotu = "bağlı";
                } else {
                    durumNotu = "servis var, BT modülü yok";
                }
                if (anaModul != null) kaydet(anaModul, geriCagri(MODUL_ANA), 0);
                haberVer();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                btModul = null;
                anaModul = null;
                durumNotu = "bağlantı koptu";
                haberVer();
            }
        };
        try {
            Intent i = new Intent("com.syu.ms.toolkit").setPackage("com.syu.ms");
            boolean ok = ctx.bindService(i, baglanti, Context.BIND_AUTO_CREATE);
            if (!ok) {
                Intent i2 = new Intent("com.syu.ms.toolkit");
                i2.setComponent(new ComponentName("com.syu.ms", "app.ToolkitService"));
                ok = ctx.bindService(i2, baglanti, Context.BIND_AUTO_CREATE);
            }
            durumNotu = ok ? "bağlanıyor" : "servis bulunamadı (FYT değil?)";
        } catch (Exception e) {
            durumNotu = "bağlanamadı: " + e.getClass().getSimpleName();
        }
    }

    void durdur() {
        if (baglanti == null) return;
        try {
            ctx.unbindService(baglanti);
        } catch (Exception ignored) {
        }
        baglanti = null;
        btModul = null;
        anaModul = null;
    }

    /** Şu anki çalma konumu (ms), çalıyorsa geçen süre eklenir. */
    long konum() {
        long k = konumMs;
        if (caliyor && konumZamani > 0) k += SystemClock.elapsedRealtime() - konumZamani;
        if (sureMs > 0) k = Math.min(k, sureMs);
        return Math.max(0, k);
    }

    void onceki() { btKomut(C_ONCEKI); }
    void sonraki() { btKomut(C_SONRAKI); }
    void oynatDuraklat() { btKomut(C_OYNAT_DURAKLAT); }

    private void btKomut(int kod) {
        IBinder m = btModul;
        if (m == null) return;
        sesKaynaginiBtYap();
        islem(m, 1, d -> {
            d.writeInt(kod);
            d.writeIntArray(null);
            d.writeFloatArray(null);
            d.writeStringArray(null);
        });
    }

    /** Ünite başka kaynaktaysa (radyo vb.) sesi Bluetooth müziğe al: ANA modül cmd(0, {3}). */
    private void sesKaynaginiBtYap() {
        IBinder m = anaModul;
        if (m == null || kaynakId == 3) return;
        islem(m, 1, d -> {
            d.writeInt(0);
            d.writeIntArray(new int[]{3});
            d.writeFloatArray(null);
            d.writeStringArray(null);
        });
    }

    // ---------------- Binder ayrıntıları ----------------

    private final Binder[] geriCagrilar = new Binder[3];

    private Binder geriCagri(int modul) {
        int idx = modul == MODUL_BT ? 1 : 0;
        if (geriCagrilar[idx] == null) {
            geriCagrilar[idx] = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                    if (code == INTERFACE_TRANSACTION) {
                        if (reply != null) reply.writeString(CB_DESC);
                        return true;
                    }
                    if (code != 1) {
                        try {
                            return super.onTransact(code, data, reply, flags);
                        } catch (Exception e) {
                            return false;
                        }
                    }
                    try {
                        data.enforceInterface(CB_DESC);
                        int k = data.readInt();
                        int[] ints = data.createIntArray();
                        data.createFloatArray();
                        String[] strs = data.createStringArray();
                        guncelle(modul, k, ints, strs);
                        if (reply != null) reply.writeNoException();
                    } catch (Exception ignored) {
                    }
                    return true;
                }
            };
        }
        return geriCagrilar[idx];
    }

    private void guncelle(int modul, int kod, int[] ints, String[] strs) {
        int sayi = ints != null && ints.length > 0 ? ints[0] : 0;
        String yazi = strs != null && strs.length > 0 && strs[0] != null ? strs[0] : "";
        if (modul == MODUL_ANA) {
            if (kod == 0) kaynakId = sayi;
            return;
        }
        switch (kod) {
            case U_BASLIK: baslik = yazi; break;
            case U_SANATCI: sanatci = yazi; break;
            case U_ALBUM: album = yazi; break;
            case U_SURE: sureMs = sayi; break;
            case U_KONUM:
                konumMs = sayi;
                konumZamani = SystemClock.elapsedRealtime();
                break;
            case U_DURUM:
                caliyor = sayi == 1;
                konumMs = konum();
                konumZamani = SystemClock.elapsedRealtime();
                break;
            default: return;
        }
        sonGuncelleme = System.currentTimeMillis();
        haberVer();
    }

    private void haberVer() {
        ana.post(dinleyici::degisti);
    }

    private static IBinder modulAl(IBinder toolkit, int modul) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(TK_DESC);
            d.writeInt(modul);
            toolkit.transact(1, d, r, 0);
            r.readException();
            return r.readStrongBinder();
        } catch (Exception e) {
            return null;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    private interface Doldur { void doldur(Parcel d); }

    private static void kaydet(IBinder modul, Binder cb, int kod) {
        islem(modul, 3, d -> {
            d.writeStrongBinder(cb);
            d.writeInt(kod);
            d.writeInt(1);
        });
    }

    private static void islem(IBinder modul, int tx, Doldur doldur) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(MOD_DESC);
            doldur.doldur(d);
            modul.transact(tx, d, r, 0);
            r.readException();
        } catch (Exception ignored) {
        } finally {
            d.recycle();
            r.recycle();
        }
    }
}
