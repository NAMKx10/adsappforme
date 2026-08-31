package com.adnani.nawawi42;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    static final int FILE_CHOOSER_REQUEST = 1001;
    private WebView webView;
    ValueCallback<Uri[]> modernFileCallback;
    ValueCallback<Uri> legacyFileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);
        configureWebView();
        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDefaultTextEncodingName("utf-8");
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(new AndroidShareBridge(), "AndroidShare");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNativeBridge();
            }
        });

        if (Build.VERSION.SDK_INT >= 21) {
            webView.setWebChromeClient(new ModernChromeClient(this));
        } else {
            webView.setWebChromeClient(new LegacyChromeClient(this));
        }
    }

    void launchModernFileChooser(ValueCallback<Uri[]> callback) {
        if (modernFileCallback != null) modernFileCallback.onReceiveValue(null);
        modernFileCallback = callback;
        launchJsonPicker();
    }

    void launchLegacyFileChooser(ValueCallback<Uri> callback) {
        if (legacyFileCallback != null) legacyFileCallback.onReceiveValue(null);
        legacyFileCallback = callback;
        launchJsonPicker();
    }

    private void launchJsonPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {
            startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } catch (ActivityNotFoundException e) {
            if (modernFileCallback != null) { modernFileCallback.onReceiveValue(null); modernFileCallback = null; }
            if (legacyFileCallback != null) { legacyFileCallback.onReceiveValue(null); legacyFileCallback = null; }
            Toast.makeText(this, "تعذر فتح منتقي الملفات", Toast.LENGTH_SHORT).show();
        }
    }

    private void injectNativeBridge() {
        String js = "(function(){" +
                "if(!window.AndroidShare||window.__nawawiNativeShareReady)return;" +
                "window.__nawawiNativeShareReady=true;" +
                "var shareFn=function(data){data=data||{};return new Promise(function(resolve,reject){try{" +
                "var files=data.files||[];" +
                "if(files.length){var f=files[0];var r=new FileReader();r.onload=function(){AndroidShare.shareDataUrl(data.title||'',data.text||'',f.name||'nawawi.png',r.result);resolve();};r.onerror=reject;r.readAsDataURL(f);}" +
                "else{AndroidShare.shareText(data.title||'',data.text||'');resolve();}" +
                "}catch(e){reject(e);}});};" +
                "try{Object.defineProperty(navigator,'share',{configurable:true,value:shareFn});}catch(e){navigator.share=shareFn;}" +
                "var can=function(){return true;};try{Object.defineProperty(navigator,'canShare',{configurable:true,value:can});}catch(e){navigator.canShare=can;}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST) return;
        Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
        if (modernFileCallback != null) {
            modernFileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            modernFileCallback = null;
        }
        if (legacyFileCallback != null) {
            legacyFileCallback.onReceiveValue(uri);
            legacyFileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        String js = "(function(){try{if(typeof currentPage!=='undefined'&&currentPage&&currentPage!=='home'){showPage('home');return 'handled';}return 'finish';}catch(e){return 'finish';}})();";
        webView.evaluateJavascript(js, value -> {
            if (value == null || !value.contains("handled")) MainActivity.super.onBackPressed();
        });
    }

    public class AndroidShareBridge {
        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "مشاركة الحديث"));
            });
        }

        @JavascriptInterface
        public void shareDataUrl(String title, String text, String filename, String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) throw new IllegalArgumentException("bad data url");
                String meta = dataUrl.substring(0, comma);
                byte[] bytes = android.util.Base64.decode(dataUrl.substring(comma + 1), android.util.Base64.DEFAULT);
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists()) dir.mkdirs();
                String safe = (filename == null || filename.trim().isEmpty()) ? "nawawi-card.png" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                File file = new File(dir, safe);
                try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(bytes); }
                Uri uri = Uri.parse("content://com.adnani.nawawi42.share/" + Uri.encode(safe));
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(meta.contains("image/png") ? "image/png" : "application/octet-stream");
                    send.putExtra(Intent.EXTRA_SUBJECT, title);
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "مشاركة بطاقة الحديث"));
                });
            } catch (Exception e) {
                shareText(title, text);
            }
        }
    }
}
