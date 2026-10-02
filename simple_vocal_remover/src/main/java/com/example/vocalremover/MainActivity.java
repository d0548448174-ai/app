package com.example.vocalremover;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new PickerChrome());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    private class PickerChrome extends WebChromeClient {
        @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), 9001);
                return true;
            } catch (Exception e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "לא ניתן לפתוח בחירת קובץ", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9001 && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    public class Bridge {
        @JavascriptInterface public void saveBase64(String name, String b64) {
            try {
                byte[] data = android.os.Build.VERSION.SDK_INT >= 26
                        ? Base64.getDecoder().decode(b64)
                        : android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                if (dir == null) throw new Exception();
                if (!dir.exists() && !dir.mkdirs()) throw new Exception();
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "נשמר: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "שגיאה בשמירה", Toast.LENGTH_SHORT).show());
            }
        }
    }
}
