package com.eileenskitchen.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import androidx.browser.customtabs.CustomTabsIntent;

public class MainActivity extends Activity {
    private static final String HOST = "eileenskitchen.xo.je";
    private static final String BASE = "https://" + HOST + "/";
    private WebView web;
    private ValueCallback<Uri[]> filePathCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sc = u.getScheme(), h = u.getHost() == null ? "" : u.getHost();
                if ("http".equals(sc) || "https".equals(sc)) {
                    if (h.equals("accounts.google.com") || (h.endsWith(HOST) && u.getPath() != null && u.getPath().contains("/auth/google_login"))) {
                        // Google يمنع الدخول داخل WebView، فنفتحه في Chrome Custom Tab
                        openTab(Uri.parse(BASE + "auth/google_login.php?app=1"));
                        return true;
                    }
                    if (h.endsWith(HOST)) return false;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try { startActivityForResult(p.createIntent(), 1); }
                catch (Exception e) { filePathCb = null; return false; }
                return true;
            }
        });
        if (b != null) web.restoreState(b);
        else if (!handleIntent(getIntent())) web.loadUrl(BASE);
    }

    private void openTab(Uri u) {
        try { new CustomTabsIntent.Builder().build().launchUrl(this, u); }
        catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
    }

    private boolean handleIntent(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d != null && "eileenskitchen".equals(d.getScheme())) {
            String t = d.getQueryParameter("token");
            if (t != null && t.matches("[a-f0-9]{64}")) {
                web.loadUrl(BASE + "auth/app_exchange.php?token=" + t);
                return true;
            }
        }
        return false;
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        if (rq == 1 && filePathCb != null) {
            filePathCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            filePathCb = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
