package com.eileenskitchen.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;

public class PollWorker extends Worker {
    public PollWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = Notifier.prefs(c);
        try {
            String tk = p.getString("token", "");
            String q = "action=poll&lb=" + p.getString("lb", "") + "&ln=" + p.getString("ln", "") + "&token=" + tk;
            String raw = Notifier.get(c, q).trim();
            if (!raw.startsWith("{")) return Result.retry(); // صفحة فحص الاستضافة وليس JSON
            JSONObject j = new JSONObject(raw);
            if (!j.optBoolean("ok")) return Result.retry();
            if (j.optBoolean("revoked")) { p.edit().remove("token").remove("ln").apply(); }

            JSONArray ns = j.optJSONArray("notifs");
            for (int i = 0; ns != null && i < ns.length(); i++) {
                JSONObject n = ns.getJSONObject(i);
                Notifier.show(c, Notifier.CH_ORDERS, 1000000 + (n.optInt("id") % 1000000),
                        n.optString("title"), n.optString("body"), Notifier.BASE);
            }
            JSONArray bs = j.optJSONArray("broadcasts");
            for (int i = 0; bs != null && i < bs.length(); i++) {
                JSONObject b = bs.getJSONObject(i);
                Notifier.show(c, Notifier.CH_OFFERS, 2000000 + (b.optInt("id") % 1000000),
                        b.optString("title"), b.optString("body"), b.optString("url", Notifier.BASE));
            }
            SharedPreferences.Editor e = p.edit();
            if (j.has("lb") && !j.isNull("lb")) e.putString("lb", j.optString("lb"));
            if (!j.optBoolean("revoked") && j.has("ln") && !j.isNull("ln") && !tk.isEmpty()) e.putString("ln", j.optString("ln"));
            e.apply();
            return Result.success();
        } catch (Throwable t) {
            return Result.retry();
        }
    }
}
