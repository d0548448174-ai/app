package com.vybe.musicfeed;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;

public final class MusicAnalysisService extends Service {
    private static final String CHANNEL_ID = "vybe_music_analysis";
    private static final int NOTIFICATION_ID = 7301;
    private WebView analysisWebView;
    private WebViewAssetLoader assetLoader;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForegroundCompat(buildNotification("מכין את מנוע ניתוח המוזיקה", 0, 0));
        getSharedPreferences("vybe_musicfeed_v2", MODE_PRIVATE).edit()
                .putBoolean("background_running", true)
                .putString("background_message", "מתחיל ניתוח AI")
                .apply();
        createAnalysisWebView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createAnalysisWebView() {
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        analysisWebView = new WebView(this);
        WebSettings settings = analysisWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        analysisWebView.addJavascriptInterface(new NativeMusicBridge(this), "Android");
        analysisWebView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(android.net.Uri.parse(url));
            }
        });
        analysisWebView.loadUrl("https://appassets.androidplatform.net/assets/index.html?background=1");
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "ניתוח מוזיקה של VYBE", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("התקדמות ניתוח הפזמונים בספריית המוזיקה");
            channel.enableLights(false);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message, int current, int total) {
        String line = message == null || message.isEmpty() ? "מנתח שירים במכשיר" : message;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("VYBE · ניתוח פזמונים")
                .setContentText(line)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setColor(Color.rgb(215, 255, 79));
        if (total > 0) builder.setProgress(total, Math.min(current, total), false);
        else builder.setProgress(0, 0, true);
        return builder.build();
    }

    public static void updateNotification(Context context, String message, int current, int total) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channel = CHANNEL_ID;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("VYBE · ניתוח פזמונים")
                .setContentText(message == null ? "מנתח שירים במכשיר" : message)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setColor(Color.rgb(215, 255, 79));
        if (total > 0) builder.setProgress(total, Math.min(current, total), false);
        else builder.setProgress(0, 0, true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void finish(Context context, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("VYBE · ניתוח הפזמונים")
                    .setContentText(message == null ? "ניתוח הספרייה הסתיים" : message)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setColor(Color.rgb(215, 255, 79));
            manager.notify(NOTIFICATION_ID, builder.build());
        }
        context.stopService(new Intent(context, MusicAnalysisService.class));
    }

    @Override
    public void onDestroy() {
        if (analysisWebView != null) {
            try {
                analysisWebView.stopLoading();
                analysisWebView.removeJavascriptInterface("Android");
                analysisWebView.destroy();
            } catch (Exception ignored) { }
            analysisWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
