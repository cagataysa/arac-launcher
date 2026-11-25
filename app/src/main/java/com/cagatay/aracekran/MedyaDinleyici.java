package com.cagatay.aracekran;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Bildirim dinleyicisi. İki işi var:
 * 1) Android, çalan şarkının medya oturumlarına erişim için bu servisin izinli olmasını ister.
 * 2) Bazı ünitelerin Bluetooth müzik uygulaması medya oturumu açmaz ama bildirim gösterir;
 *    şarkı adını o bildirimden okuruz.
 */
public class MedyaDinleyici extends NotificationListenerService {

    interface Dinleyen { void bildirimlerDegisti(); }

    static volatile MedyaDinleyici ornek;
    static volatile Dinleyen dinleyen;

    @Override
    public void onListenerConnected() {
        ornek = this;
        haberVer();
    }

    @Override
    public void onListenerDisconnected() {
        ornek = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) { haberVer(); }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) { haberVer(); }

    private static void haberVer() {
        Dinleyen d = dinleyen;
        if (d != null) d.bildirimlerDegisti();
    }

    static StatusBarNotification[] bildirimler() {
        MedyaDinleyici m = ornek;
        if (m == null) return new StatusBarNotification[0];
        try {
            StatusBarNotification[] b = m.getActiveNotifications();
            return b != null ? b : new StatusBarNotification[0];
        } catch (Exception e) {
            return new StatusBarNotification[0];
        }
    }

    /** Bildirim medya/müzik bildirimine benziyor mu? */
    static boolean medyaBildirimiMi(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return false;
        Bundle x = n.extras;
        if (x != null && x.get(Notification.EXTRA_MEDIA_SESSION) != null) return true;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return true;
        String sablon = x != null ? x.getString(Notification.EXTRA_TEMPLATE) : null;
        if (sablon != null && sablon.contains("MediaStyle")) return true;
        String p = sbn.getPackageName().toLowerCase();
        return p.contains("music") || p.contains("bt") || p.contains("bluetooth")
                || p.contains("a2dp") || p.contains("player") || p.contains("media");
    }
}
