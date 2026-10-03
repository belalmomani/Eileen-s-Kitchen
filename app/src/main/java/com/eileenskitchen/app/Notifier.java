package com.eileenskitchen.app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.core.app.NotificationManagerCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Notifier {
    static final String HOST = "eileenskitchen.xo.je";
    static final String BASE = "https://" + HOST + "/";
    static final String API = BASE + "api/app_poll.php";
    static final String CH_ORDERS = "orders", CH_OFFERS = "offers";

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("notif", Context.MODE_PRIVATE); }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_ORDERS, "حالة الطلبات", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_OFFERS, "العروض اليومية", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @SuppressLint("MissingPermission")
    static void show(Context c, String channel, int id, String title, String body, String url) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;
        Intent i = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (url != null && url.startsWith(BASE)) i.putExtra("open_url", url);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(id, new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(CH_ORDERS.equals(channel) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .build());
    }

    /** طلب HTTP يستخدم كوكيز الـWebView (مهم لتجاوز فحص InfinityFree). */
    static String get(Context c, String query) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(API + "?" + query + "&i=1").openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(15000);
        String ck = null;
        try { ck = CookieManager.getInstance().getCookie(BASE); } catch (Throwable t) {}
        if (ck != null) h.setRequestProperty("Cookie", ck);
        try { h.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(c)); } catch (Throwable t) {}
        InputStream in = h.getResponseCode() < 400 ? h.getInputStream() : h.getErrorStream();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while (in != null && (n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toString("UTF-8");
    }

    /** تسجيل الجهاز بعد الدخول: يمنح رمزاً طويل الأمد لجلب إشعارات الطلبات. */
    static void registerAsync(final Context c) {
        final SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (p.getString("token", null) != null || now - p.getLong("reg_try", 0) < 60000) return;
        p.edit().putLong("reg_try", now).apply();
        new Thread(new Runnable() { public void run() {
            try {
                String raw = get(c, "action=register").trim();
                if (!raw.startsWith("{")) { if (!toasted) { toasted = true; toast(c, "تعذّر تفعيل الإشعارات: ملف api/app_poll.php غير موجود أو محجوب على الخادم"); } return; }
                JSONObject j = new JSONObject(raw);
                if (j.optBoolean("ok")) {
                    p.edit().putString("token", j.getString("token")).putString("ln", j.optString("ln", "")).apply();
                    toast(c, "تم تفعيل إشعارات الطلبات ✅");
                }
            } catch (Throwable t) { if (!toasted) { toasted = true; toast(c, "تعذّر تفعيل الإشعارات (اتصال)"); } }
        }}).start();
    }

    /** عند تسجيل الخروج: إلغاء الرمز حتى لا تصل إشعارات الحساب السابق. */
    static void revokeAsync(final Context c) {
        final SharedPreferences p = prefs(c);
        final String tk = p.getString("token", null);
        if (tk == null) return;
        p.edit().remove("token").remove("ln").apply();
        new Thread(new Runnable() { public void run() {
            try { get(c, "action=revoke&token=" + tk); } catch (Throwable t) {}
        }}).start();
    }

    static boolean toasted = false;
    static void toast(final Context c, final String m) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() { Toast.makeText(c, m, Toast.LENGTH_LONG).show(); } });
    }

    /** فحص واحد للخادم وعرض الجديد. يعيد false عند فشل الاتصال أو رد غير متوقع. */
    static synchronized boolean pollOnce(Context c) {
        SharedPreferences p = prefs(c);
        try {
            String tk = p.getString("token", "");
            String raw = get(c, "action=poll&lb=" + p.getString("lb", "") + "&ln=" + p.getString("ln", "") + "&token=" + tk).trim();
            if (!raw.startsWith("{")) return false;
            JSONObject j = new JSONObject(raw);
            if (!j.optBoolean("ok")) return false;
            if (j.optBoolean("revoked")) p.edit().remove("token").remove("ln").apply();
            JSONArray ns = j.optJSONArray("notifs");
            for (int i = 0; ns != null && i < ns.length(); i++) {
                JSONObject n = ns.getJSONObject(i);
                show(c, CH_ORDERS, 1000000 + (n.optInt("id") % 1000000), n.optString("title"), n.optString("body"), BASE);
            }
            JSONArray bs = j.optJSONArray("broadcasts");
            for (int i = 0; bs != null && i < bs.length(); i++) {
                JSONObject b = bs.getJSONObject(i);
                String u = b.optString("url", ""); 
                show(c, CH_OFFERS, 2000000 + (b.optInt("id") % 1000000), b.optString("title"), b.optString("body"), u.isEmpty() || "null".equals(u) ? BASE : u);
            }
            SharedPreferences.Editor e = p.edit();
            if (j.has("lb") && !j.isNull("lb")) e.putString("lb", j.optString("lb"));
            if (!j.optBoolean("revoked") && j.has("ln") && !j.isNull("ln") && !tk.isEmpty()) e.putString("ln", j.optString("ln"));
            e.apply();
            return true;
        } catch (Throwable t) { return false; }
    }
}
