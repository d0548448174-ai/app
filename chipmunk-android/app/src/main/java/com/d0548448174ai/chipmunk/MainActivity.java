package com.d0548448174ai.chipmunk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

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
    private Button saveButton;
    private SeekBar effectBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(255, 248, 240));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(255, 248, 240));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout hero = card(Color.rgb(255, 247, 228), 28);
        hero.setPadding(dp(16), dp(14), dp(16), dp(16));

        TextView badge = text("🐿️  CHIPMUNK VOICE", 12, Color.rgb(122, 72, 35), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(bg(Color.rgb(255, 229, 177), 40));
        hero.addView(badge, wrap());

        FrameLayout art = new FrameLayout(this);
        art.setPadding(0, dp(8), 0, dp(4));

        ChipmunkArtView left = new ChipmunkArtView(this, false);
        ChipmunkArtView right = new ChipmunkArtView(this, true);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(142), dp(142), Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = dp(-54);
        lp.rightMargin = dp(54);
        art.addView(left, lp);

        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(142), dp(142), Gravity.CENTER_HORIZONTAL);
        rp.leftMargin = dp(54);
        rp.rightMargin = dp(-54);
        art.addView(right, rp);

        hero.addView(art, match(0, 2, 0, 0));

        TextView title = text("קול צ'יפמאנק", 30, Color.rgb(88, 49, 25), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, 0);
        hero.addView(title, match(0, 0, 0, 0));

        TextView subtitle = text(
                "מאט → מעלה גובה → מחזיר למהירות המקורית",
                14, Color.rgb(116, 83, 62), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(3), 0, 0);
        hero.addView(subtitle, match(0, 0, 0, 0));

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
        effect.addView(marks, match(0, 2, 0, 0));

        root.addView(effect, match(0, 0, 0, 10));
        updateEffectLabel();

        LinearLayout recipe = card(Color.rgb(255, 241, 220), 22);
        recipe.addView(text(
                "האפקט עובד כך:",
                15, Color.rgb(105, 66, 36), true));
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

        saveButton = button("💾  שמור את קובץ ה‑MP3");
        saveButton.setVisibility(View.GONE);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackground(bg(Color.rgb(122, 72, 35), 18));
        saveButton.setOnClickListener(v -> saveMp3());
        root.addView(saveButton, match(0, dp(8), 0, 0));

        setContentView(root);
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
            resultMp3 = null;
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

                // User's requested recipe:
                // 1) slow down while preserving pitch
                // 2) reinterpret at a higher sample rate
                // 3) resample back to the normal rate
                // The 0.50 preset produces approximately +12 semitones.
                String filter = String.format(
                        Locale.US,
                        "aresample=44100,atempo=%.6f,asetrate=%.2f,aresample=44100",
                        slow, 44100.0 / slow);

                String command =
                        "-y " +
                        "-i " + quote(input.getAbsolutePath()) + " " +
                        "-vn " +
                        "-af " + quote(filter) + " " +
                        "-c:a libmp3lame " +
                        "-b:a 192k " +
                        "-ar 44100 " +
                        quote(output.getAbsolutePath());

                com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.execute(command);

                if (!ReturnCode.isSuccess(session.getReturnCode()) || !output.exists() || output.length() < 5000) {
                    throw new IllegalStateException("FFmpeg failed: " + session.getReturnCode());
                }

                resultMp3 = output;
                deleteQuiet(input);

                runOnUiThread(() -> {
                    busy(false, "✅ מוכן! נוצר MP3 עם קול צ'יפמאנק.");
                    saveButton.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                e.printStackTrace();
                deleteQuiet(input);
                runOnUiThread(() ->
                        busy(false, "❌ ההמרה נכשלה. בחר שיר אחר ונסה שוב."));
            }
        });
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
        super.onDestroy();
        executor.shutdownNow();
    }

    private static class ChipmunkArtView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean second;

        ChipmunkArtView(Activity c, boolean second) {
            super(c);
            this.second = second;
            p.setStrokeCap(Paint.Cap.ROUND);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f + dpLocal(3), r = Math.min(w,h) * .30f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(111, 67, 39));
            c.drawCircle(cx, cy, r * 1.18f, p);

            p.setColor(Color.rgb(145, 94, 55));
            c.drawCircle(cx - r * .9f, cy - r * .62f, r * .52f, p);
            c.drawCircle(cx + r * .9f, cy - r * .62f, r * .52f, p);

            p.setColor(Color.rgb(232, 183, 127));
            c.drawCircle(cx, cy + r * .28f, r * .72f, p);

            p.setColor(Color.WHITE);
            c.drawCircle(cx - r * .43f, cy - r * .06f, r * .20f, p);
            c.drawCircle(cx + r * .43f, cy - r * .06f, r * .20f, p);

            p.setColor(Color.rgb(38, 29, 23));
            c.drawCircle(cx - r * .43f, cy - r * .06f, r * .095f, p);
            c.drawCircle(cx + r * .43f, cy - r * .06f, r * .095f, p);

            p.setColor(Color.rgb(73, 38, 29));
            Path nose = new Path();
            nose.moveTo(cx, cy + r * .16f);
            nose.quadTo(cx - r*.18f, cy + r*.02f, cx - r*.02f, cy - r*.02f);
            nose.quadTo(cx + r*.18f, cy + r*.02f, cx, cy + r*.16f);
            c.drawPath(nose, p);

            p.setColor(second ? Color.rgb(68, 130, 196) : Color.rgb(211, 66, 52));
            c.drawRoundRect(cx - r*.75f, cy + r*.76f, cx + r*.75f, cy + r*.98f, r*.18f, r*.18f, p);
        }

        private float dpLocal(float n) {
            return n * getResources().getDisplayMetrics().density;
        }
    }
}
