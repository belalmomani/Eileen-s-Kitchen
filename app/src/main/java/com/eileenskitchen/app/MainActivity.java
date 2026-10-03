package com.eileenskitchen.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;

public class MainActivity extends Activity {
    private static final String URL = "https://eileenskitchen.xo.je/";
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
                String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) {
                    if (u.getHost() != null && u.getHost().endsWith("eileenskitchen.xo.je")) return false;
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
        if (b == null) web.loadUrl(URL); else web.restoreState(b);
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        if (rq == 1 && filePathCb != null) {
            filePathCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            filePathCb = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
