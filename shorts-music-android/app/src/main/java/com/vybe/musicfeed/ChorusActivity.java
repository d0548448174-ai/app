package com.vybe.musicfeed;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

public final class ChorusActivity extends Activity {
    private static final int REQUEST_AUDIO = 804;
    private static final int REQUEST_NOTIFICATIONS = 805;
    private WebView webView;
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 11, 18));
        getWindow().setNavigationBarColor(Color.rgb(9, 11, 18));
        if (!hasAudioPermission()) {
            showPermissionScreen();
            requestAudioPermission();
        } else {
            requestNotificationPermissionIfNeeded();
            showApp();
        }
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_AUDIO);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_AUDIO);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void showPermissionScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(42, 32, 42, 32);
        layout.setBackgroundColor(Color.rgb(9, 11, 18));
        TextView title = new TextView(this);
        title.setText("VYBE · פזמונים חכמים");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        TextView description = new TextView(this);
        description.setText("כדי למצוא את השירים והפזמונים שבמכשיר, צריך לאשר גישה לקובצי המוזיקה. הקבצים יישארו במכשיר ולא יועלו לשרת.");
        description.setTextColor(Color.LTGRAY);
        description.setTextSize(15);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 18, 0, 22);
        Button button = new Button(this);
        button.setText("אישור גישה למוזיקה");
        button.setOnClickListener(v -> requestAudioPermission());
        layout.addView(title);
        layout.addView(description);
        layout.addView(button);
        setContentView(layout);
    }

    private void showApp() {
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(9, 11, 18));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.addJavascriptInterface(new NativeMusicBridge(this), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(android.net.Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.evaluateJavascript("window.vybeNativeReady && window.vybeNativeReady()", null);
            }
        });
        setContentView(webView);
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            if (hasAudioPermission()) {
                requestNotificationPermissionIfNeeded();
                showApp();
            }
            else Toast.makeText(this, "צריך לאשר גישה למוזיקה כדי להשתמש בספרייה", Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_NOTIFICATIONS && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "התראות הניתוח הופעלו", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.evaluateJavascript("window.refreshFromNative && window.refreshFromNative()", null);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
