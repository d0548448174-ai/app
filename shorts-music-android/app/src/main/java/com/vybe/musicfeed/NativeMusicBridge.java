package com.vybe.musicfeed;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class NativeMusicBridge {
    private static final String PREFS = "vybe_musicfeed_v2";
    private static final long MAX_AUDIO_BYTES = 90L * 1024L * 1024L;
    private final Context context;
    private final SharedPreferences preferences;

    public NativeMusicBridge(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String getTracksJson() {
        JSONArray rows = new JSONArray();
        if (!hasAudioPermission()) return rows.toString();
        Cursor cursor = null;
        try {
            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                    MediaStore.Audio.Media.DURATION + " >= ?";
            String[] args = new String[] { "8000" };
            String sort = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
            cursor = context.getContentResolver().query(collection, projection, selection, args, sort);
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                while (cursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    long id = cursor.getLong(idCol);
                    item.put("id", String.valueOf(id));
                    item.put("title", clean(cursor.getString(titleCol), "ללא שם"));
                    item.put("artist", clean(cursor.getString(artistCol), "אמן לא ידוע"));
                    item.put("album", clean(cursor.getString(albumCol), "אלבום לא ידוע"));
                    item.put("duration", Math.max(0L, cursor.getLong(durationCol)));
                    item.put("albumId", String.valueOf(cursor.getLong(albumIdCol)));
                    item.put("favorite", preferences.getBoolean("favorite_" + id, false));
                    item.put("analysis", preferences.getString("analysis_" + id, ""));
                    rows.put(item);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VYBE", "Could not scan MediaStore", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return rows.toString();
    }

    @JavascriptInterface
    public String readAudioBase64(String idText) {
        if (!hasAudioPermission()) return "ERR:PERMISSION";
        try {
            long id = Long.parseLong(idText);
            Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) return "ERR:NOT_FOUND";
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_AUDIO_BYTES) return "ERR:FILE_TOO_LARGE";
                    output.write(buffer, 0, count);
                }
                return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            }
        } catch (Exception e) {
            android.util.Log.e("VYBE", "Audio read failed for " + idText, e);
            return "ERR:" + e.getClass().getSimpleName();
        }
    }

    @JavascriptInterface
    public String getCoverBase64(String albumIdText) {
        try {
            long albumId = Long.parseLong(albumIdText);
            Uri artUri = Uri.parse("content://media/external/audio/albumart/" + albumId);
            try (InputStream input = context.getContentResolver().openInputStream(artUri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) return "";
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 8L * 1024L * 1024L) return "";
                    output.write(buffer, 0, count);
                }
                return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public String getAnalysis(String id) {
        return preferences.getString("analysis_" + id, "");
    }

    @JavascriptInterface
    public void saveAnalysis(String id, String json) {
        preferences.edit().putString("analysis_" + id, json).apply();
    }

    @JavascriptInterface
    public boolean isFavorite(String id) {
        return preferences.getBoolean("favorite_" + id, false);
    }

    @JavascriptInterface
    public void setFavorite(String id, boolean favorite) {
        preferences.edit().putBoolean("favorite_" + id, favorite).apply();
    }

    @JavascriptInterface
    public String getBackgroundState() {
        try {
            JSONObject state = new JSONObject();
            state.put("running", preferences.getBoolean("background_running", false));
            state.put("message", preferences.getString("background_message", ""));
            state.put("current", preferences.getInt("background_current", 0));
            state.put("total", preferences.getInt("background_total", 0));
            return state.toString();
        } catch (Exception e) {
            return "{\"running\":false}";
        }
    }

    @JavascriptInterface
    public void startBackgroundAnalysis() {
        if (!hasAudioPermission()) return;
        Intent intent = new Intent(context, MusicAnalysisService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @JavascriptInterface
    public void updateBackgroundProgress(String message, int current, int total) {
        preferences.edit()
                .putBoolean("background_running", true)
                .putString("background_message", message == null ? "מנתח מוזיקה" : message)
                .putInt("background_current", Math.max(0, current))
                .putInt("background_total", Math.max(0, total))
                .apply();
        MusicAnalysisService.updateNotification(context, message, current, total);
    }

    @JavascriptInterface
    public void finishBackground(String message) {
        preferences.edit()
                .putBoolean("background_running", false)
                .putString("background_message", message == null ? "הסריקה הסתיימה" : message)
                .apply();
        MusicAnalysisService.finish(context, message);
    }

    @JavascriptInterface
    public String saveMp3Base64(String requestedName, String encoded) {
        if (encoded == null || encoded.isEmpty()) return "ERR:EMPTY";
        if (Build.VERSION.SDK_INT <= 28 &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(() ->
                        ((Activity) context).requestPermissions(
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 901));
            }
            return "ERR:PERMISSION";
        }
        Uri savedUri = null;
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, safeFileName(requestedName));
                values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VYBE");
                values.put(MediaStore.Audio.Media.IS_PENDING, 1);
                savedUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) return "ERR:CREATE";
                try (OutputStream stream = context.getContentResolver().openOutputStream(savedUri)) {
                    if (stream == null) throw new IllegalStateException("Cannot open output stream");
                    stream.write(bytes);
                    stream.flush();
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
                context.getContentResolver().update(savedUri, ready, null, null);
            } else {
                File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "VYBE");
                if (!folder.exists() && !folder.mkdirs()) return "ERR:FOLDER";
                File file = new File(folder, safeFileName(requestedName));
                try (FileOutputStream stream = new FileOutputStream(file)) {
                    stream.write(bytes);
                    stream.flush();
                }
                android.media.MediaScannerConnection.scanFile(context,
                        new String[]{file.getAbsolutePath()}, new String[]{"audio/mpeg"}, null);
            }
            return "OK";
        } catch (Exception e) {
            android.util.Log.e("VYBE", "MP3 save failed", e);
            if (savedUri != null) {
                try { context.getContentResolver().delete(savedUri, null, null); } catch (Exception ignored) { }
            }
            return "ERR:" + e.getClass().getSimpleName();
        }
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value.trim())) return fallback;
        return value.trim();
    }

    private static String safeFileName(String name) {
        String cleaned = name == null ? "VYBE_chorus" : name.trim();
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        if (!cleaned.toLowerCase(Locale.ROOT).endsWith(".mp3")) cleaned += ".mp3";
        return cleaned;
    }
}
