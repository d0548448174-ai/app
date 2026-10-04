package com.d0548448174ai.covermaker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 101;
    private static final int PICK_MP3 = 102;
    private static final int CREATE_OUTPUT = 103;

    private Uri imageUri;
    private Uri audioUri;
    private Uri savedUri;
    private File pendingOutput;
    private String audioName = "";
    private ImageView coverPreview;
    private TextView imageName;
    private TextView audioNameView;
    private TextView statusView;
    private TextView successView;
    private Button makeButton;
    private Button shareButton;
    private ProgressBar progress;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView badge = text("MP3  •  COVER", 12, Color.rgb(109, 93, 253), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(14), dp(8), dp(14), dp(8));
        badge.setBackground(round(Color.rgb(238, 234, 254), 50));
        root.addView(badge, lpWrap());

        TextView title = text("עטיפה חדשה לשיר", 30, Color.rgb(23, 23, 27), true);
        title.setPadding(0, dp(18), 0, 0);
        root.addView(title);

        TextView subtitle = text("בחר תמונה ו‑MP3 — והאפליקציה תטמיע את התמונה בתוך השיר כעטיפת אלבום.", 15, Color.rgb(119, 119, 131), false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        LinearLayout previewCard = card();
        previewCard.setPadding(dp(12), dp(12), dp(12), dp(12));

        coverPreview = new ImageView(this);
        coverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverPreview.setImageDrawable(roundDrawable(Color.rgb(247,247,250), 28));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(126), dp(126));
        previewLp.gravity = Gravity.CENTER;
        previewCard.addView(coverPreview, previewLp);

        TextView hint = text("התמונה שנבחרה", 12, Color.rgb(119,119,131), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, 0);
        previewCard.addView(hint);
        root.addView(previewCard, lpMatchWithMargins(0, 0, 0, 14));

        LinearLayout imageCard = card();
        TextView imageTitle = text("1  •  בחר תמונה", 18, Color.rgb(23,23,27), true);
        imageCard.addView(imageTitle);
        imageName = text("עדיין לא נבחרה תמונה", 13, Color.rgb(119,119,131), false);
        imageName.setPadding(0, dp(5), 0, dp(12));
        imageCard.addView(imageName);
        Button imageButton = actionButton("📷  בחירת תמונה");
        imageButton.setOnClickListener(v -> pickImage());
        imageCard.addView(imageButton);
        root.addView(imageCard, lpMatchWithMargins(0, 0, 0, 12));

        LinearLayout audioCard = card();
        TextView audioTitle = text("2  •  בחר שיר MP3", 18, Color.rgb(23,23,27), true);
        audioCard.addView(audioTitle);
        audioNameView = text("עדיין לא נבחר שיר", 13, Color.rgb(119,119,131), false);
        audioNameView.setPadding(0, dp(5), 0, dp(12));
        audioCard.addView(audioNameView);
        Button audioButton = actionButton("🎵  בחירת MP3");
        audioButton.setOnClickListener(v -> pickAudio());
        audioCard.addView(audioButton);
        root.addView(audioCard, lpMatchWithMargins(0, 0, 0, 12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setPadding(dp(14), dp(12), dp(14), dp(12));
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setBackground(round(Color.rgb(247,247,250), 18));
        TextView infoIcon = text("✓", 17, Color.rgb(109,93,253), true);
        infoIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconLp.setMargins(0, 0, dp(10), 0);
        info.addView(infoIcon, iconLp);
        TextView infoText = text("ה‑MP3 עצמו לא מנוגן מחדש ולא מאבד את השמע. רק תגית העטיפה מתווספת לקובץ.", 13, Color.rgb(84,84,94), false);
        info.addView(infoText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(info, lpMatchWithMargins(0, 0, 0, 16));

        makeButton = actionButton("✨  צור MP3 עם עטיפה");
        makeButton.setEnabled(false);
        makeButton.setAlpha(0.45f);
        makeButton.setTextSize(17);
        makeButton.setPadding(dp(18), dp(14), dp(18), dp(14));
        makeButton.setOnClickListener(v -> createCoveredMp3());
        root.addView(makeButton, lpMatchWithMargins(0, 0, 0, 12));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        progressLp.gravity = Gravity.CENTER;
        root.addView(progress, progressLp);

        statusView = text("", 13, Color.rgb(109,93,253), true);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(8), 0, 0);
        root.addView(statusView, lpWrap());

        successView = text("", 15, Color.rgb(28, 132, 77), true);
        successView.setGravity(Gravity.CENTER);
        successView.setPadding(dp(16), dp(14), dp(16), 0);
        root.addView(successView, lpMatchWithMargins(0, dp(10), 0, 0));

        shareButton = actionButton("↗  שתף את ה‑MP3 החדש");
        shareButton.setVisibility(View.GONE);
        shareButton.setOnClickListener(v -> shareSavedFile());
        root.addView(shareButton, lpMatchWithMargins(0, dp(10), 0, 0));

        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/mpeg");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_MP3);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }

        if (requestCode == PICK_IMAGE) {
            imageUri = uri;
            imageName.setText(fileName(uri));
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (in != null) in.close();
                if (bitmap != null) coverPreview.setImageBitmap(bitmap);
            } catch (Exception e) {
                showError("לא הצלחתי לפתוח את התמונה הזאת.");
            }
        } else if (requestCode == PICK_MP3) {
            audioUri = uri;
            audioName = fileName(uri);
            audioNameView.setText(audioName);
        } else if (requestCode == CREATE_OUTPUT) {
            savePendingOutput(uri);
        }
        refreshButton();
    }

    private void refreshButton() {
        boolean ready = imageUri != null && audioUri != null && pendingOutput == null;
        makeButton.setEnabled(ready);
        makeButton.setAlpha(ready ? 1f : 0.45f);
    }

    private void createCoveredMp3() {
        if (imageUri == null || audioUri == null) return;
        setBusy(true, "מעבד את הקובץ…");
        executor.execute(() -> {
            File input = null;
            File output = null;
            try {
                input = new File(getCacheDir(), "source_" + System.currentTimeMillis() + ".mp3");
                copyUriToFile(audioUri, input);

                byte[] cover = buildCoverJpeg(imageUri);
                if (cover == null || cover.length == 0) throw new Exception("image");

                Mp3File mp3 = new Mp3File(input.getAbsolutePath());
                ID3v2 oldTag = mp3.hasId3v2Tag() ? mp3.getId3v2Tag() : null;

                String title = oldTag != null ? oldTag.getTitle() : null;
                String artist = oldTag != null ? oldTag.getArtist() : null;
                String album = oldTag != null ? oldTag.getAlbum() : null;
                String year = oldTag != null ? oldTag.getYear() : null;
                String comment = oldTag != null ? oldTag.getComment() : null;
                String track = oldTag != null ? oldTag.getTrack() : null;
                String albumArtist = oldTag != null ? oldTag.getAlbumArtist() : null;

                if (mp3.hasId3v2Tag()) mp3.removeId3v2Tag();

                ID3v24Tag tag = new ID3v24Tag();
                if (title != null) tag.setTitle(title);
                if (artist != null) tag.setArtist(artist);
                if (album != null) tag.setAlbum(album);
                if (year != null) tag.setYear(year);
                if (comment != null) tag.setComment(comment);
                if (track != null) tag.setTrack(track);
                if (albumArtist != null) tag.setAlbumArtist(albumArtist);
                tag.setAlbumImage(cover, "image/jpeg");
                tag.setEncoder("MP3 Cover Maker");
                mp3.setId3v2Tag(tag);

                output = new File(getCacheDir(), safeBaseName(audioName) + " - עטיפה.mp3");
                if (output.exists()) output.delete();
                mp3.save(output.getAbsolutePath());

                pendingOutput = output;
                runOnUiThread(() -> {
                    setBusy(false, "ה‑MP3 מוכן לשמירה.");
                    successView.setText("✅ נוספה עטיפה לשיר. עכשיו בחר איפה לשמור את הקובץ.");
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.setType("audio/mpeg");
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.putExtra(Intent.EXTRA_TITLE, output.getName());
                    startActivityForResult(save, CREATE_OUTPUT);
                });
            } catch (Exception e) {
                if (output != null) output.delete();
                runOnUiThread(() -> setBusy(false, "נכשל. בדוק שהקובץ הוא MP3 תקין ונסה שוב."));
            } finally {
                if (input != null) input.delete();
            }
        });
    }

    private void savePendingOutput(Uri destination) {
        if (pendingOutput == null) return;
        final File source = pendingOutput;
        setBusy(true, "שומר את הקובץ…");
        executor.execute(() -> {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(destination)) {
                if (out == null) throw new Exception("no output");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
                savedUri = destination;
                source.delete();
                pendingOutput = null;
                runOnUiThread(() -> {
                    setBusy(false, "נשמר בהצלחה.");
                    successView.setText("✅ הקובץ נשמר. התמונה משולבת בתוך ה‑MP3 כעטיפת אלבום.");
                    shareButton.setVisibility(View.VISIBLE);
                    refreshButton();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false, "לא הצלחתי לשמור את הקובץ."));
            }
        });
    }

    private void shareSavedFile() {
        if (savedUri == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("audio/mpeg");
        share.putExtra(Intent.EXTRA_STREAM, savedUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "שתף MP3"));
    }

    private byte[] buildCoverJpeg(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("image");
        Bitmap bitmap = BitmapFactory.decodeStream(in);
        in.close();
        if (bitmap == null) throw new Exception("image");

        int max = 1200;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w > max || h > max) {
            float scale = Math.min((float) max / w, (float) max / h);
            bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(w * scale), Math.round(h * scale), true);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private void copyUriToFile(Uri uri, File destination) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) throw new Exception("source");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private String fileName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) { }
        if (name == null) name = uri.getLastPathSegment();
        return name == null ? "קובץ" : name;
    }

    private String safeBaseName(String name) {
        if (name == null || name.trim().isEmpty()) return "song";
        String n = name;
        int dot = n.toLowerCase().lastIndexOf(".mp3");
        if (dot > 0) n = n.substring(0, dot);
        return n.replaceAll("[\\/:*?"<>|]", "_").trim();
    }

    private void setBusy(boolean busy, String status) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        makeButton.setEnabled(!busy && imageUri != null && audioUri != null && pendingOutput == null);
        makeButton.setAlpha(!busy && imageUri != null && audioUri != null && pendingOutput == null ? 1f : 0.45f);
        statusView.setText(status);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(round(Color.WHITE, 22));
        l.setElevation(dp(2));
        return l;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(42, 42, 48));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(round(Color.rgb(247,247,250), 16));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.rgb(232,232,238));
        return d;
    }

    private GradientDrawable roundDrawable(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpMatchWithMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
