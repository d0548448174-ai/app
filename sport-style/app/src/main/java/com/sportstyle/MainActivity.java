package com.sportstyle;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.webkit.WebViewClientCompat;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int FILE_CHOOSER_REQUEST = 7101;
    private static final int WORKOUT_PERMISSION_REQUEST = 7102;
    private static final int AUDIO_PERMISSION_REQUEST = 7103;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private WebViewAssetLoader assetLoader;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private float lastStepCount = -1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(10, 16, 26));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(10, 16, 26));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(10, 16, 26));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("audio/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestWorkoutPermissions();
                }
            }
        });

        webView.addJavascriptInterface(new AppBridge(), "SportStyle");
        setContentView(webView);
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasStepPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWorkoutPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.ArrayList<String> wanted = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
            if (!wanted.isEmpty()) {
                requestPermissions(wanted.toArray(new String[0]), WORKOUT_PERMISSION_REQUEST);
                return;
            }
        }
        notifyPermissionResult();
    }

    private void notifyPermissionResult() {
        boolean allowed = hasLocationPermission();
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, allowed, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript("window.onNativeLocationPermission && window.onNativeLocationPermission(" + allowed + ")", null));
        }
        registerStepSensorIfAllowed();
    }

    private void registerStepSensorIfAllowed() {
        if (sensorManager != null && stepSensor != null && hasStepPermission()) {
            try {
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } catch (SecurityException ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerStepSensorIfAllowed();
        if (webView != null) {
            webView.onResume();
            webView.postDelayed(() -> webView.evaluateJavascript("window.onNativeAppResume && window.onNativeAppResume()", null), 350);
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WORKOUT_PERMISSION_REQUEST) {
            notifyPermissionResult();
        } else if (requestCode == AUDIO_PERMISSION_REQUEST) {
            final boolean granted = hasAudioPermission();
            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript(
                        "window.onNativeAudioPermission && window.onNativeAudioPermission(" + granted + ")", null));
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER && event.values.length > 0) {
            lastStepCount = event.values[0];
            if (webView != null) {
                String js = "window.onStepUpdate && window.onStepUpdate(" + (long) lastStepCount + ")";
                webView.post(() -> webView.evaluateJavascript(js, null));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.handleAndroidBack && window.handleAndroidBack()", null);
        } else {
            super.onBackPressed();
        }
    }

    public class AppBridge {
        @JavascriptInterface
        public void requestPermissionsForWorkout() {
            runOnUiThread(() -> requestWorkoutPermissions());
        }

        @JavascriptInterface
        public boolean hasLocationPermission() {
            return MainActivity.this.hasLocationPermission();
        }

        @JavascriptInterface
        public boolean hasStepCounter() {
            return stepSensor != null && hasStepPermission();
        }

        @JavascriptInterface
        public boolean hasAudioPermission() {
            return MainActivity.this.hasAudioPermission();
        }

        @JavascriptInterface
        public void requestAudioPermission() {
            runOnUiThread(() -> {
                if (MainActivity.this.hasAudioPermission()) {
                    if (webView != null) webView.evaluateJavascript(
                            "window.onNativeAudioPermission && window.onNativeAudioPermission(true)", null);
                    return;
                }
                String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? Manifest.permission.READ_MEDIA_AUDIO
                        : Manifest.permission.READ_EXTERNAL_STORAGE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{permission}, AUDIO_PERMISSION_REQUEST);
                }
            });
        }

        @JavascriptInterface
        public String scanDeviceMusic() {
            if (!MainActivity.this.hasAudioPermission()) return "[]";
            JSONArray songs = new JSONArray();
            Cursor cursor = null;
            try {
                String[] projection = new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.DATE_ADDED
                };
                String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                        + MediaStore.Audio.Media.DURATION + " > 0";
                cursor = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
                if (cursor == null) return "[]";
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
                int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
                int addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    if (title == null || title.trim().isEmpty()) title = "שיר ללא שם";
                    String artist = cursor.getString(artistCol);
                    if (artist == null || artist.equals(MediaStore.UNKNOWN_STRING)) artist = "";
                    String album = cursor.getString(albumCol);
                    if (album == null || album.equals(MediaStore.UNKNOWN_STRING)) album = "";
                    JSONObject song = new JSONObject();
                    song.put("id", String.valueOf(id));
                    song.put("title", title);
                    song.put("artist", artist);
                    song.put("album", album);
                    song.put("duration", Math.max(0L, cursor.getLong(durationCol)));
                    song.put("size", Math.max(0L, cursor.getLong(sizeCol)));
                    song.put("type", cursor.getString(mimeCol) == null ? "audio/*" : cursor.getString(mimeCol));
                    song.put("addedAt", cursor.getLong(addedCol));
                    song.put("uri", android.content.ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString());
                    songs.put(song);
                }
            } catch (Exception e) {
                android.util.Log.e("SportStyle", "Could not scan device music", e);
                return "[]";
            } finally {
                if (cursor != null) cursor.close();
            }
            return songs.toString();
        }

        @JavascriptInterface
        public void keepScreenOn(boolean enabled) {
            runOnUiThread(() -> {
                if (enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void vibrate() {
            runOnUiThread(() -> {
                try {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(35);
                        }
                    }
                } catch (Exception ignored) { }
            });
        }
    }
}
