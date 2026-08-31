package com.adnani.nawawi42;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    static final int FILE_CHOOSER_REQUEST = 1001;
    static final int SAVE_REQUEST = 1002;

    private WebView webView;
    ValueCallback<Uri[]> modernFileCallback;
    ValueCallback<Uri> legacyFileCallback;
    private byte[] pendingSaveBytes;
    private String pendingSaveName = "nawawi-backup.json";
    private String pendingSaveMime = "application/json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);
        configureWebView();
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(savedInstanceState);
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
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidShare");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNativeBridge();
            }
        });

        if (Build.VERSION.SDK_INT >= 21) webView.setWebChromeClient(new ModernChromeClient(this));
        else webView.setWebChromeClient(new LegacyChromeClient(this));
    }

    private void injectNativeBridge() {
        String js = "(function(){try{" +
                "if(window.__nawawiAndroidBridgeReady)return;window.__nawawiAndroidBridgeReady=true;" +
                "var shareFn=function(data){data=data||{};return new Promise(function(resolve,reject){try{" +
                "var files=data.files||[];if(files.length){var f=files[0],r=new FileReader();" +
                "r.onload=function(){AndroidShare.shareDataUrl(data.title||'',data.text||'',f.name||'nawawi-card.png',String(r.result));resolve();};" +
                "r.onerror=function(){reject(new Error('read failed'));};r.readAsDataURL(f);}" +
                "else{AndroidShare.shareText(data.title||'',data.text||'');resolve();}}catch(e){reject(e);}});};" +
                "try{Object.defineProperty(navigator,'share',{configurable:true,value:shareFn});}catch(e){navigator.share=shareFn;}" +
                "var canFn=function(){return true;};try{Object.defineProperty(navigator,'canShare',{configurable:true,value:canFn});}catch(e){navigator.canShare=canFn;}" +
                "var oldCreate=URL.createObjectURL.bind(URL);URL.createObjectURL=function(blob){window.__nawawiLastDownloadBlob=blob;return oldCreate(blob);};" +
                "var oldClick=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){" +
                "if(this.download&&window.__nawawiLastDownloadBlob){var a=this,b=window.__nawawiLastDownloadBlob,r=new FileReader();" +
                "r.onload=function(){AndroidShare.saveDataUrl(a.download||'nawawi-file',String(r.result));window.__nawawiLastDownloadBlob=null;};" +
                "r.readAsDataURL(b);return;}return oldClick.call(this);};" +
                "}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    void launchModernFileChooser(ValueCallback<Uri[]> callback) {
        if (modernFileCallback != null) modernFileCallback.onReceiveValue(null);
        modernFileCallback = callback;
        launchFileChooser();
    }

    void launchLegacyFileChooser(ValueCallback<Uri> callback) {
        if (legacyFileCallback != null) legacyFileCallback.onReceiveValue(null);
        legacyFileCallback = callback;
        launchFileChooser();
    }

    private void launchFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try { startActivityForResult(intent, FILE_CHOOSER_REQUEST); }
        catch (ActivityNotFoundException e) {
            if (modernFileCallback != null) { modernFileCallback.onReceiveValue(null); modernFileCallback = null; }
            if (legacyFileCallback != null) { legacyFileCallback.onReceiveValue(null); legacyFileCallback = null; }
            Toast.makeText(this, "تعذر فتح منتقي الملفات", Toast.LENGTH_SHORT).show();
        }
    }

    private static String mimeFromName(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            if (modernFileCallback != null) {
                modernFileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                modernFileCallback = null;
            }
            if (legacyFileCallback != null) {
                legacyFileCallback.onReceiveValue(uri);
                legacyFileCallback = null;
            }
            return;
        }
        if (requestCode == SAVE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(data.getData());
                    if (os != null) { os.write(pendingSaveBytes); os.flush(); os.close(); }
                    Toast.makeText(this, "تم حفظ الملف", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show();
                }
            }
            pendingSaveBytes = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        String js = "(function(){try{var p=(typeof currentPage!=='undefined')?currentPage:'';if(p&&p!=='home'&&typeof showPage==='function'){showPage('home');return 'handled';}return 'finish';}catch(e){return 'finish';}})();";
        webView.evaluateJavascript(js, value -> {
            if (value == null || !value.contains("handled")) MainActivity.super.onBackPressed();
        });
    }

    public class AndroidBridge {
        @JavascriptInterface public void shareText(final String title, final String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "مشاركة الحديث"));
            });
        }

        @JavascriptInterface public void shareDataUrl(final String title, final String text, String filename, String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) throw new IllegalArgumentException("Bad data URL");
                byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists()) dir.mkdirs();
                String safe = (filename == null || filename.trim().isEmpty()) ? "nawawi-card.png" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                File file = new File(dir, safe);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bytes); fos.flush(); fos.close();
                final Uri uri = Uri.parse("content://com.adnani.nawawi42.share/" + Uri.encode(safe));
                final String mime = mimeFromName(safe);
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(mime);
                    send.putExtra(Intent.EXTRA_SUBJECT, title);
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "مشاركة بطاقة الحديث"));
                });
            } catch (Exception e) { shareText(title, text); }
        }

        @JavascriptInterface public void saveDataUrl(String filename, String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) return;
                pendingSaveBytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                pendingSaveName = (filename == null || filename.trim().isEmpty()) ? "nawawi-file" : filename;
                pendingSaveMime = mimeFromName(pendingSaveName);
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(pendingSaveMime);
                    intent.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
                    startActivityForResult(intent, SAVE_REQUEST);
                });
            } catch (Exception ignored) { }
        }
    }
}
