package com.d0548448174ai.chipmunk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 10;
    private static final int SAVE_MP3 = 11;

    private Uri inputUri;
    private File resultMp3;

    private TextView fileLabel;
    private TextView effectLabel;
    private TextView status;
    private ProgressBar progress;
    private Button makeButton;
    private Button previewButton;
    private Button saveButton;
    private SeekBar effectBar;
    private MediaPlayer mediaPlayer;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(255, 248, 240));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(24));
        root.setBackgroundColor(Color.rgb(255, 248, 240));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        LinearLayout hero = card(Color.rgb(255, 247, 228), 26);
        hero.setPadding(dp(10), dp(10), dp(10), dp(14));

        TextView badge = text("🐿️  CHIPMUNK VOICE", 12, Color.rgb(122, 72, 35), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        badge.setBackground(bg(Color.rgb(255, 229, 177), 40));
        hero.addView(badge, wrap());

        ImageView scene1 = image(loadSceneImage("chipmunk_scene_1a.b64", "chipmunk_scene_1b.b64"));
        hero.addView(scene1, imageParams(dp(175), dp(4)));

        ImageView scene2 = image(loadSingleImage("chipmunk_scene_2small.b64"));
        hero.addView(scene2, imageParams(dp(150), 0));

        TextView title = text("קול צ'יפמאנק", 29, Color.rgb(88, 49, 25), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, 0);
        hero.addView(title, match(0, 0, 0, 0));

        TextView subtitle = text(
                "מעלה גובה 🐿️  •  משמיע קצת יותר מהר  •  אפשר לשמור או רק לשמוע",
                14, Color.rgb(116, 83, 62), false);
        subtitle.setGravity(Gravity.CENTER);
        hero.addView(subtitle, match(0, dp(2), 0, 0));

        root.addView(hero, match(0, 0, 0, 12));

        LinearLayout song = card(Color.WHITE, 22);
        song.addView(text("1  •  בחר את השיר", 18, Color.rgb(73, 48, 31), true));

        fileLabel = text("עדיין לא נבחר שיר", 13, Color.rgb(130, 112, 96), false);
        fileLabel.setPadding(0, dp(6), 0, dp(10));
        song.addView(fileLabel);

        Button choose = button("🎵  בחירת שיר");
        choose.setOnClickListener(v -> pickAudio());
        song.addView(choose);

        root.addView(song, match(0, 0, 0, 10));

        LinearLayout effect = card(Color.WHITE, 22);
        effect.addView(text("2  •  גובה צ'יפמאנק", 18, Color.rgb(73, 48, 31), true));

        effectLabel = text("", 17, Color.rgb(161, 86, 36), true);
        effectLabel.setGravity(Gravity.CENTER);
        effectLabel.setPadding(0, dp(8), 0, dp(4));
        effect.addView(effectLabel);

        effectBar = new SeekBar(this);
        effectBar.setMax(2);
        effectBar.setProgress(1);
        effectBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                updateEffectLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        effect.addView(effectBar, match(0, 3, 0, 0));

        LinearLayout marks = new LinearLayout(this);
        marks.setGravity(Gravity.CENTER);
        TextView low = text("נמוך", 11, Color.GRAY, false);
        TextView chip = text("🐿️ צ'יפמאנק", 12, Color.rgb(161, 86, 36), true);
        TextView high = text("גבוה", 11, Color.GRAY, false);
        marks.addView(low, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        marks.addView(chip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        marks.addView(high, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView hint = text(
                "הקובץ נשאר באותה מהירות נגינה — רק הגובה עולה",
                12, Color.rgb(145, 112, 82), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        effect.addView(hint, match(0, 2, 0, 0));

        effect.addView(marks, match(0, 2, 0, 0));

        root.addView(effect, match(0, 0, 0, 10));
        updateEffectLabel();

        LinearLayout recipe = card(Color.rgb(255, 241, 220), 22);
        recipe.addView(text("האפקט עובד כך:", 15, Color.rgb(105, 66, 36), true));
        recipe.addView(text(
                "① מאטים את השיר ל־½ מהירות בלי להוריד את הגובה\n" +
                "② מחזירים למהירות רגילה דרך קצב הדגימה\n" +
                "③ מתקבל קול גבוה יותר, אבל השיר לא נשאר איטי",
                13, Color.rgb(111, 83, 62), false));
        root.addView(recipe, match(0, 0, 0, 12));

        makeButton = button("🐿️  המר ל‑MP3");
        makeButton.setTextSize(17);
        makeButton.setEnabled(false);
        makeButton.setAlpha(.45f);
        makeButton.setTextColor(Color.rgb(85, 48, 26));
        makeButton.setBackground(bg(Color.rgb(255, 204, 112), 20));
        makeButton.setOnClickListener(v -> convertToMp3());
        root.addView(makeButton, match(0, 0, 0, 8));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(34), dp(34));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        status = text("", 13, Color.rgb(116, 76, 49), true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, wrap());

        LinearLayout playerCard = card(Color.rgb(255, 248, 236), 22);
        playerCard.addView(text("3  •  השמע בתוך האפליקציה", 18, Color.rgb(73, 48, 31), true));
        playerCard.addView(text(
                "אין צורך להוריד קובץ כדי לשמוע את התוצאה",
                12, Color.rgb(145, 112, 82), false));

        previewButton = button("▶️  השמע את התוצאה");
        previewButton.setVisibility(View.GONE);
        previewButton.setTextSize(16);
        previewButton.setTextColor(Color.WHITE);
        previewButton.setBackground(bg(Color.rgb(122, 72, 35), 18));
        previewButton.setOnClickListener(v -> togglePreview());
        playerCard.addView(previewButton, match(0, dp(8), 0, 0));
        root.addView(playerCard, match(0, dp(4), 0, 10));

        saveButton = button("💾  שמור את קובץ ה‑MP3");
        saveButton.setVisibility(View.GONE);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackground(bg(Color.rgb(122, 72, 35), 18));
        saveButton.setOnClickListener(v -> saveMp3());
        root.addView(saveButton, match(0, dp(8), 0, 0));

        setContentView(scroll);
    }

    private ImageView image(Bitmap bitmap) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setAdjustViewBounds(true);
        if (bitmap != null) {
            v.setImageBitmap(bitmap);
        } else {
            v.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        return v;
    }

    private LinearLayout.LayoutParams imageParams(int heightDp, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        p.setMargins(0, dp(topDp), 0, 0);
        return p;
    }

    private Bitmap loadSceneImage(String part1, String part2) {
        try {
            String base64 = readAsset(part1) + readAsset(part2);
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap loadSingleImage(String name) {
        try {
            byte[] bytes = Base64.decode(readAsset(name), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == PICK_AUDIO) {
            inputUri = data.getData();
            fileLabel.setText(fileName(inputUri));
            stopPreview();
            resultMp3 = null;
            previewButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            status.setText("");
            makeButton.setEnabled(true);
            makeButton.setAlpha(1f);
        } else if (requestCode == SAVE_MP3) {
            copyToUri(resultMp3, data.getData());
        }
    }

    private double selectedSlowdown() {
        if (effectBar == null) return 0.5;
        int p = effectBar.getProgress();
        if (p == 0) return 0.67;
        if (p == 2) return 0.40;
        return 0.50;
    }

    private double pitchSemitones() {
        double slow = selectedSlowdown();
        return -12.0 * (Math.log(slow) / Math.log(2.0));
    }

    private void updateEffectLabel() {
        double slow = selectedSlowdown();
        double semitones = pitchSemitones();
        String s = String.format(Locale.US,
                "האטה ×%.2f  →  גובה +%.1f חצאי־טונים", slow, semitones);
        if (Math.abs(slow - 0.5) < 0.001) {
            s = "🐿️ מצב צ'יפמאנק  •  אוקטבה מעל";
        }
        if (effectLabel != null) effectLabel.setText(s);
    }

    private void convertToMp3() {
        if (inputUri == null) return;

        busy(true, "מכין את הצ'יפמאנק…");

        executor.execute(() -> {
            File input = null;
            try {
                long stamp = System.currentTimeMillis();
                input = new File(getCacheDir(), "chipmunk_input_" + stamp);
                copyUriToFile(inputUri, input);

                File output = new File(getCacheDir(), "chipmunk_" + stamp + ".mp3");
                double slow = selectedSlowdown();

                String tempoFilter;
                if (slow < 0.5) {
                    // FFmpeg's atempo filter should stay within its supported range.
                    // Two stages give us 0.40x without leaving the final song speed changed.
                    tempoFilter = "atempo=0.5,atempo=0.8";
                } else {
                    tempoFilter = String.format(Locale.US, "atempo=%.6f", slow);
                }

                String filter = String.format(
                        Locale.US,
                        "aresample=44100,%s,asetrate=%.2f,aresample=44100,atempo=1.03",
                        tempoFilter, 44100.0 / slow);

                String command =
                        "-y " +
                        "-i " + quote(input.getAbsolutePath()) + " " +
                        "-vn " +
                        "-af " + quote(filter) + " " +
                        "-c:a libmp3lame " +
                        "-b:a 192k " +
                        "-ar 44100 " +
                        "-threads 1 " +
                        "-loglevel error " +
                        "-nostdin " +
                        quote(output.getAbsolutePath());

                String safeCommand = command;
                com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.execute(safeCommand);

                if (!ReturnCode.isSuccess(session.getReturnCode()) || !output.exists() || output.length() < 5000) {
                    String details = session.getFailStackTrace();
                    if (details == null || details.length() == 0) {
                        details = String.valueOf(session.getReturnCode());
                    }
                    throw new IllegalStateException("FFmpeg failed: " + details);
                }

                resultMp3 = output;
                deleteQuiet(input);

                runOnUiThread(() -> {
                    busy(false, "✅ מוכן! אפשר להשמיע כאן או לשמור ל‑MP3.");
                    previewButton.setVisibility(View.VISIBLE);
                    previewButton.setText("▶️  השמע את התוצאה");
                    saveButton.setVisibility(View.VISIBLE);
                });
            } catch (Throwable e) {
                e.printStackTrace();
                deleteQuiet(input);
                String message = e.getMessage();
                if (message == null || message.length() == 0) message = "לא ניתן להמיר את הקובץ";
                final String finalMessage = "❌ " + message;
                runOnUiThread(() -> busy(false, finalMessage));
            }
        });
    }

    private void togglePreview() {
        if (resultMp3 == null) return;

        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                previewButton.setText("▶️  המשך השמעה");
                return;
            }

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    previewButton.setText("⏸️  עצור השמעה");
                    status.setText("🔊 משמיע את תוצאת הצ'יפמאנק…");
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    previewButton.setText("▶️  השמע שוב");
                    status.setText("✅ ההשמעה הסתיימה.");
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    status.setText("❌ לא ניתן להשמיע את התוצאה.");
                    previewButton.setText("▶️  השמע את התוצאה");
                    stopPreview();
                    return true;
                });
                mediaPlayer.setDataSource(resultMp3.getAbsolutePath());
                mediaPlayer.prepareAsync();
            } else {
                mediaPlayer.start();
                previewButton.setText("⏸️  עצור השמעה");
            }
        } catch (Exception e) {
            e.printStackTrace();
            status.setText("❌ לא ניתן להשמיע את התוצאה.");
            stopPreview();
        }
    }

    private void stopPreview() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (previewButton != null) {
            previewButton.setText("▶️  השמע את התוצאה");
        }
    }

    private void saveMp3() {
        if (resultMp3 == null) return;

        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("audio/mpeg");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, "chipmunk_song.mp3");
        startActivityForResult(i, SAVE_MP3);
    }

    private void copyToUri(File source, Uri dest) {
        if (source == null || dest == null) return;
        executor.execute(() -> {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(dest)) {
                if (out == null) throw new IllegalStateException("No output");
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
                out.flush();
                runOnUiThread(() -> status.setText("✅ קובץ ה‑MP3 נשמר בהצלחה."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("❌ השמירה נכשלה."));
            }
        });
    }

    private void copyUriToFile(Uri source, File dest) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IllegalStateException("No input");
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
    }

    private String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String fileName(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p == null || p.length() == 0) return "השיר שנבחר";
        return p.replaceFirst("^.*/", "");
    }

    private void busy(boolean working, String message) {
        progress.setVisibility(working ? View.VISIBLE : View.GONE);
        status.setText(message);
        makeButton.setEnabled(!working && inputUri != null);
        makeButton.setAlpha(!working && inputUri != null ? 1f : .45f);
        if (working) saveButton.setVisibility(View.GONE);
    }

    private void deleteQuiet(File f) {
        if (f != null) {
            try { f.delete(); } catch (Exception ignored) {}
        }
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15), dp(15), dp(15), dp(15));
        l.setBackground(bg(color, radius));
        return l;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(63, 47, 37));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(13), dp(9), dp(13), dp(9));
        b.setBackground(bg(Color.WHITE, 18));
        return b;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.argb(35, 70, 45, 25));
        return d;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int n) {
        return Math.round(getResources().getDisplayMetrics().density * n);
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
        executor.shutdownNow();
    }
}
