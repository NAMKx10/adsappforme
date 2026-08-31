package com.adnani.nawawi42;

import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

public class LegacyChromeClient extends WebChromeClient {
    private final MainActivity activity;
    public LegacyChromeClient(MainActivity activity) { this.activity = activity; }

    public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        activity.launchLegacyFileChooser(uploadMsg);
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        activity.launchLegacyFileChooser(uploadMsg);
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        activity.launchLegacyFileChooser(uploadMsg);
    }
}
