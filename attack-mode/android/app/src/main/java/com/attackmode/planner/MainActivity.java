package com.attackmode.planner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RemindersPlugin.class);
        registerPlugin(BackupPlugin.class);
        registerPlugin(WidgetsPlugin.class);
        registerPlugin(NudgesPlugin.class);
        super.onCreate(savedInstanceState);
        tuneWebView();
        captureUrl(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureUrl(intent);
    }

    private void tuneWebView() {
        WebView webView = getBridge().getWebView();
        WebSettings settings = webView.getSettings();
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setOnLongClickListener(view -> {
            WebView.HitTestResult hit = ((WebView) view).getHitTestResult();
            int type = hit.getType();
            return type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                || type == WebView.HitTestResult.IMAGE_TYPE;
        });
    }

    private void captureUrl(Intent intent) {
        if (intent == null) {
            return;
        }
        String url = intent.getStringExtra(ReminderNotifier.EXTRA_URL);
        if (url != null && !url.isEmpty()) {
            RemindersPlugin.pendingUrl = url;
            intent.removeExtra(ReminderNotifier.EXTRA_URL);
        }
    }
}
