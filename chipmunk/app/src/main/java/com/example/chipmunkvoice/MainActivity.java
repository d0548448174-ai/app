package com.example.chipmunkvoice;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int PICK_AUDIO = 100;
    private static final int SAVE_AUDIO = 101;

    private Uri selectedUri;
    private File resultFile;
    private MediaPlayer player;

    private TextView selectedText;
    private TextView pitchText;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button convertButton;
    private Button playButton;
    private Button saveButton;

    private float factor = 1.45f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams params(int height) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(height)
        );
    }

    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private Button button(String text, int backgroundColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(0xFF2A1B16);
        b.setMinHeight(dp(52));
        b.setBackground(round(backgroundColor, 20));
        return b;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private View gap(int height) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return s;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(14));
        box.setBackground(round(0xFFFFFFFF, 22));
        return box;
    }

    private void buildUi() {
        int brown = 0xFF2A1B16;
        int muted = 0xFF725A50;
        int cream = 0xFFFFF7EE;
        int orange = 0xFFFFB04A;
        int deepOrange = 0xFFFF8A3D;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cream);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(24));
        scroll.addView(root);

        TextView title = label("🐿️ Chipmunk Voice", 28, brown);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, params(58));

        TextView sub = label("הופכים שיר לקול סנאי גבוה ושובב", 16, muted);
        root.addView(sub, params(34));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(14), dp(8), dp(14), dp(8));
        hero.setBackground(round(0xFFFFE0B5, 24));

        TextView icon = label("🐿️🎵", 54, brown);
        hero.addView(icon, params(70));

        TextView local = label("עובד מקומית במכשיר • בלי העלאה לענן", 14, brown);
        hero.addView(local, params(38));

        root.addView(hero, params(122));
        root.addView(gap(12));

        LinearLayout pickCard = card();

        TextView pickTitle = label("1. בחר שיר", 19, brown);
        pickTitle.setGravity(Gravity.RIGHT);
        pickTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        pickCard.addView(pickTitle, params(36));

        selectedText = label("עדיין לא נבחר שיר", 15, muted);
        selectedText.setGravity(Gravity.RIGHT);
        pickCard.addView(selectedText, params(44));

        Button pickButton = button("📁  בחר שיר מהמכשיר", orange);
        pickButton.setOnClickListener(v -> pickAudio());
        pickCard.addView(pickButton, params(56));

        root.addView(pickCard);
        root.addView(gap(12));

        LinearLayout pitchCard = card();

        TextView pitchTitle = label("2. כוון את קול הציפמאנק", 19, brown);
        pitchTitle.setGravity(Gravity.RIGHT);
        pitchTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        pitchCard.addView(pitchTitle, params(36));

        pitchText = label("145% • ציפמאנק קלאסי", 16, deepOrange);
        pitchText.setTypeface(null, android.graphics.Typeface.BOLD);
        pitchCard.addView(pitchText, params(40));

        SeekBar seek = new SeekBar(this);
        seek.setMax(80);
        seek.setProgress(25);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                factor = 1.20f + (progress / 100f);
                int percent = Math.round(factor * 100f);

                String description;
                if (percent < 130) description = "עדין";
                else if (percent < 150) description = "ציפמאנק קלאסי";
                else if (percent < 170) description = "סנאי מטורף";
                else description = "סופר-סנאי";

                pitchText.setText(percent + "% • " + description);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        pitchCard.addView(seek, params(52));
        root.addView(pitchCard);
        root.addView(gap(12));

        convertButton = button("✨  הפוך לציפמאנק", deepOrange);
        convertButton.setTextColor(0xFFFFFFFF);
        convertButton.setOnClickListener(v -> convert());
        root.addView(convertButton, params(60));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, params(16));

        statusText = label("בחר שיר כדי להתחיל", 14, muted);
        root.addView(statusText, params(40));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setWeightSum(2f);

        playButton = button("▶️ נגן תוצאה", 0xFFE8D9D0);
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> playResult());
        actions.addView(playButton, new LinearLayout.LayoutParams(0, dp(56), 1f));

        saveButton = button("💾 שמור עותק", 0xFFD5E7C6);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveResult());
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, dp(56), 1f));

        root.addView(actions);
        root.addView(gap(10));

        TextView note = label("התוצאה נשמרת כ-WAV באיכות גבוהה.", 13, muted);
        root.addView(note, params(36));

        setContentView(scroll);
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        if (requestCode == PICK_AUDIO) {
            selectedUri = data.getData();

            try {
                int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(selectedUri, flags);
            } catch (Exception ignored) {
            }

            String name = selectedUri.getLastPathSegment();
            if (name == null || name.trim().isEmpty()) {
                name = "השיר שנבחר";
            }

            selectedText.setText("✅ " + name);
            statusText.setText("מוכן להמרה 🐿️");
        } else if (requestCode == SAVE_AUDIO) {
            saveResultToUri(data.getData());
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

        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("פותח את השיר...");

        new Thread(() -> {
            try {
                File result = AudioProcessor.convert(
                        this,
                        selectedUri,
                        factor,
                        (progress, message) -> runOnUiThread(() -> {
                            progressBar.setProgress(progress);
                            statusText.setText(message);
                        })
                );

                runOnUiThread(() -> {
                    resultFile = result;
                    convertButton.setEnabled(true);
                    playButton.setEnabled(true);
                    saveButton.setEnabled(true);
                    progressBar.setProgress(100);
                    statusText.setText("✅ מוכן! נגן או שמור את התוצאה.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    convertButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("⚠️ " + friendlyMessage(e));
                    Toast.makeText(this, friendlyMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String friendlyMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "לא הצלחתי לעבד את קובץ האודיו.";
        }
        return message.length() > 140 ? message.substring(0, 140) : message;
    }

    private void playResult() {
        if (resultFile == null || !resultFile.exists()) {
            return;
        }

        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setDataSource(resultFile.getAbsolutePath());
                player.setOnCompletionListener(mp -> {
                    playButton.setText("▶️ נגן תוצאה");
                });
                player.prepare();
            }

            if (player.isPlaying()) {
                player.pause();
                playButton.setText("▶️ המשך ניגון");
            } else {
                player.start();
                playButton.setText("⏸️ עצור / השהה");
            }
        } catch (Exception e) {
            Toast.makeText(this, "לא הצלחתי לנגן את התוצאה.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayer() {
        if (player == null) {
            return;
        }

        try {
            player.stop();
        } catch (Exception ignored) {
        }

        try {
            player.release();
        } catch (Exception ignored) {
        }

        player = null;

        if (playButton != null) {
            playButton.setText("▶️ נגן תוצאה");
        }
    }

    private void saveResult() {
        if (resultFile == null || !resultFile.exists()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, "chipmunk_song.wav");
        startActivityForResult(intent, SAVE_AUDIO);
    }

    private void saveResultToUri(Uri uri) {
        if (uri == null || resultFile == null || !resultFile.exists()) {
            return;
        }

        new Thread(() -> {
            try (InputStream in = new FileInputStream(resultFile);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {

                if (out == null) {
                    throw new Exception("לא ניתן לפתוח את מיקום השמירה.");
                }

                byte[] buffer = new byte[64 * 1024];
                int count;

                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }

                out.flush();

                runOnUiThread(() -> {
                    statusText.setText("💾 נשמר בהצלחה!");
                    Toast.makeText(this, "העותק נשמר בהצלחה 🎉", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "השמירה נכשלה: " + friendlyMessage(e),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        stopPlayer();
        super.onDestroy();
    }
}
