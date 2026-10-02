package com.example.chipmunkvoice;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1001;
    private static final int SAVE_AUDIO = 1002;

    private TextView selectedText;
    private TextView pitchText;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button convertButton;
    private Button playButton;
    private Button saveButton;
    private Uri selectedUri;
    private File resultFile;
    private MediaPlayer player;
    private float pitchFactor = 1.45f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private android.graphics.drawable.GradientDrawable bg(int color, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private Button makeButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(0xFF2B1A15);
        b.setAllCaps(false);
        b.setMinHeight(dp(54));
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        b.setBackground(bg(color, 20));
        return b;
    }

    private void buildUi() {
        final int brown = 0xFF2B1A15;
        final int cream = 0xFFFFF8F0;
        final int card = 0xFFFFFFFF;
        final int orange = 0xFFFFB74D;
        final int darkOrange = 0xFFFF8A3D;
        final int muted = 0xFF785C50;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cream);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(24));
        scroll.addView(root);

        TextView title = text("🐿️  Chipmunk Voice", 28, brown);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView subtitle = text("הופכים כל שיר לקול סנאי גבוה ושובב", 16, muted);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(18), dp(12), dp(18), dp(18));
        hero.setBackground(bg(0xFFFFE0B2, 24));
        TextView squirrel = text("🐿️🎵", 54, brown);
        hero.addView(squirrel, new LinearLayout.LayoutParams(-1, dp(74)));
        TextView heroText = text("קול ציפמאנק • עובד מקומית במכשיר • בלי העלאה לענן", 14, brown);
        heroText.setGravity(Gravity.CENTER);
        hero.addView(heroText, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(hero, new LinearLayout.LayoutParams(-1, dp(132)));

        root.addView(space(12));

        LinearLayout card1 = card();
        TextView c1 = text("1. בחר שיר", 19, brown);
        c1.setGravity(Gravity.RIGHT);
        c1.setTypeface(null, android.graphics.Typeface.BOLD);
        card1.addView(c1, lp(-1, 38));

        selectedText = text("עדיין לא נבחר שיר", 15, muted);
        selectedText.setGravity(Gravity.RIGHT);
        selectedText.setPadding(dp(10), 0, dp(10), 0);
        card1.addView(selectedText, lp(-1, 42));

        Button choose = makeButton("📁  בחר שיר מהמכשיר", orange);
        choose.setOnClickListener(v -> pickAudio());
        card1.addView(choose, lp(-1, 58));
        root.addView(card1);

        root.addView(space(12));

        LinearLayout card2 = card();
        TextView c2 = text("2. כוון את גובה הציפמאנק", 19, brown);
        c2.setGravity(Gravity.RIGHT);
        c2.setTypeface(null, android.graphics.Typeface.BOLD);
        card2.addView(c2, lp(-1, 38));

        pitchText = text("145%  •  ציפמאנק קלאסי", 16, darkOrange);
        pitchText.setTypeface(null, android.graphics.Typeface.BOLD);
        card2.addView(pitchText, lp(-1, 42));

        SeekBar seek = new SeekBar(this);
        seek.setMax(60);
        seek.setProgress(25);
        seek.setPadding(dp(6), 0, dp(6), 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                pitchFactor = 1.20f + (p / 100f);
                int pct = Math.round(pitchFactor * 100);
                String desc = pct < 132 ? "עדין" : pct < 151 ? "ציפמאנק קלאסי" : pct < 170 ? "סנאי מטורף" : "סופר-סנאי";
                pitchText.setText(pct + "%  •  " + desc);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        card2.addView(seek, lp(-1, 54));
        root.addView(card2);

        root.addView(space(12));

        convertButton = makeButton("✨  הפוך לציפמאנק", darkOrange);
        convertButton.setTextColor(ColorCompat.WHITE);
        convertButton.setOnClickListener(v -> convert());
        root.addView(convertButton, lp(-1, 62));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, lp(-1, 16));

        statusText = text("בחר שיר כדי להתחיל", 14, muted);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, lp(-1, 36));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setWeightSum(2f);

        playButton = makeButton("▶️  נגן תוצאה", 0xFFE7D7CE);
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlay());
        actions.addView(playButton, new LinearLayout.LayoutParams(0, dp(58), 1f));

        saveButton = makeButton("💾  שמור עותק", 0xFFD7E9C5);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveResult());
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, dp(58), 1f));

        root.addView(actions);
        root.addView(space(10));

        TextView note = text("הקובץ נשמר כ-WAV איכותי. אפשר לבחור בכל תיקייה דרך חלון השמירה.", 13, muted);
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(-1, 46));

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(10), dp(16), dp(14));
        c.setBackground(bg(0xFFFFFFFF, 22));
        return c;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, dp(h));
    }

    private View space(int h) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return s;
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == PICK_AUDIO) {
            selectedUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                    selectedUri,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                );
            } catch (Exception ignored) {}

            String name = selectedUri.getLastPathSegment();
            if (name == null) name = "השיר שנבחר";
            selectedText.setText("✅  " + name);
            statusText.setText("מוכן להמרה לציפמאנק 🐿️");
        } else if (requestCode == SAVE_AUDIO) {
            saveToUri(data.getData());
        }
    }

    private void convert() {
        if (selectedUri == null) {
            Toast.makeText(this, "קודם בחר שיר 🎵", Toast.LENGTH_SHORT).show();
            return;
        }

        stopPlayer();
        convertButton.setEnabled(false);
        playButton.setEnabled(false);
        saveButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusText.setText("מנתח את השיר...");

        new Thread(() -> {
            try {
                resultFile = AudioProcessor.convert(this, selectedUri, pitchFactor,
                    (progress, message) -> runOnUiThread(() -> {
                        progressBar.setProgress(progress);
                        statusText.setText(message);
                    })
                );

                runOnUiThread(() -> {
                    convertButton.setEnabled(true);
                    playButton.setEnabled(true);
                    saveButton.setEnabled(true);
                    progressBar.setProgress(100);
                    statusText.setText("✅ מוכן! אפשר לנגן או לשמור עותק.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    convertButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("⚠️ ההמרה נכשלה: " + friendlyError(e));
                    Toast.makeText(this, friendlyError(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String friendlyError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return "לא הצלחתי לקרוא את קובץ האודיו.";
        return m.length() > 120 ? m.substring(0, 120) : m;
    }

    private void togglePlay() {
        if (resultFile == null || !resultFile.exists()) return;

        if (player != null && player.isPlaying()) {
            player.pause();
            playButton.setText("▶️  המשך ניגון");
            return;
        }

        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setDataSource(resultFile.getAbsolutePath());
                player.setOnCompletionListener(mp -> playButton.setText("▶️  נגן תוצאה"));
                player.prepare();
            }
            player.start();
            playButton.setText("⏸️  עצור / השהה");
        } catch (Exception e) {
            Toast.makeText(this, "לא הצלחתי לנגן את התוצאה.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
            playButton.setText("▶️  נגן תוצאה");
        }
    }

    private void saveResult() {
        if (resultFile == null || !resultFile.exists()) return;

        String base = "chipmunk_song.wav";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/wav");
        i.putExtra(Intent.EXTRA_TITLE, base);
        startActivityForResult(i, SAVE_AUDIO);
    }

    private void saveToUri(Uri uri) {
        if (resultFile == null || !resultFile.exists() || uri == null) return;

        new Thread(() -> {
            try (InputStream in = new FileInputStream(resultFile);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {

                if (out == null) throw new Exception("לא ניתן לפתוח את מיקום השמירה.");
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.flush();

                runOnUiThread(() -> {
                    statusText.setText("💾 נשמר בהצלחה!");
                    Toast.makeText(this, "העותק נשמר בהצלחה 🎉", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "השמירה נכשלה: " + friendlyError(e), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        stopPlayer();
        super.onDestroy();
    }

    private static final class ColorCompat {
        static final int WHITE = 0xFFFFFFFF;
    }
}
