package com.adnani.nawawi42;

import android.annotation.TargetApi;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

@TargetApi(21)
public class ModernChromeClient extends WebChromeClient {
    private final MainActivity activity;
    public ModernChromeClient(MainActivity activity) { this.activity = activity; }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
        activity.launchModernFileChooser(callback);
        return true;
    }
}
