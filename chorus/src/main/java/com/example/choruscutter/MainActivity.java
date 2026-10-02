package com.example.choruscutter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 100;
    private static final int PICK_FOLDER = 101;
    private static final int CREATE_OUTPUT = 102;

    private static final int BG = Color.rgb(13, 16, 32);
    private static final int CARD = Color.rgb(24, 29, 52);
    private static final int CARD_2 = Color.rgb(31, 37, 65);
    private static final int PURPLE = Color.rgb(124, 92, 255);
    private static final int CYAN = Color.rgb(111, 231, 255);
    private static final int TEXT = Color.rgb(245, 247, 255);
    private static final int MUTED = Color.rgb(173, 181, 207);
    private static final int GREEN = Color.rgb(84, 212, 156);
    private static final int RED = Color.rgb(255, 104, 126);

    private Uri songUri;
    private String songName = "";
    private long songDurationMs = 0;
    private long detectedStartMs = 0;
    private long detectedLengthMs = 12000;

    private TextView songTitle;
    private TextView songMeta;
    private TextView detectionText;
    private TextView progressText;
    private TextView folderTitle;
    private TextView startValue;
    private TextView lengthValue;
    private LinearLayout recentContainer;
    private LinearLayout folderFiles;
    private LinearLayout resultCard;
    private LinearLayout progressCard;
    private Button playButton;
    private Button detectButton;
    private Button previewButton;
    private Button saveButton;
    private SeekBar startBar;
    private SeekBar lengthBar;
    private ProgressBar progressBar;

    private MediaPlayer player;
    private final Handler handler = new Handler();
    private final SharedPreferences prefs;


    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button actionButton(String value, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(bg(color, 18));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(bg(CARD, 22));
        return c;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void margin(View v, int top, int bottom) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (p == null) p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        v.setLayoutParams(p);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 19, 38));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 38));
        prefs = getSharedPreferences("chorus_cutter", MODE_PRIVATE);
        buildUi();
        refreshRecent();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(28));

        FrameLayout hero = new FrameLayout(this);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(gradient(new int[]{Color.rgb(78, 56, 170), Color.rgb(28, 143, 175)}, 26));
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.example.choruscutter.R.drawable.ic_chorus);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.END | Gravity.CENTER_VERTICAL);
        hero.addView(icon, ip);

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView h1 = label("פזמון חכם", 28, Color.WHITE, true);
        TextView h2 = label("מוצא קטע חוזר בשיר • משמיע • חותך • שומר", 14, Color.WHITE, false);
        heroText.addView(h1, lp(-1, -2));
        heroText.addView(h2, lp(dp(235), -2));
        hero.addView(heroText, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        root.addView(hero, lp(-1, -2));

        LinearLayout choose = card();
        TextView chooseTitle = label("🎵 בחירת שיר", 19, TEXT, true);
        choose.addView(chooseTitle, lp(-1, -2));

        LinearLayout chooseRow = new LinearLayout(this);
        chooseRow.setGravity(Gravity.CENTER_VERTICAL);
        Button pick = actionButton("בחר שיר", PURPLE);
        Button folder = actionButton("עיין בתיקייה", CARD_2);
        chooseRow.addView(pick, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(48), 1);
        fp.leftMargin = dp(8);
        chooseRow.addView(folder, fp);
        choose.addView(chooseRow, lp(-1, dp(56)));
        margin(chooseRow, 10, 0);
        pick.setOnClickListener(v -> openAudioPicker());
        folder.setOnClickListener(v -> openFolderPicker());

        TextView tip = label("💡 אפשר לבחור שיר מהורדות, מוזיקה, WhatsApp או כל תיקייה אחרת במכשיר.", 12, MUTED, false);
        choose.addView(tip, lp(-1, -2));
        margin(tip, 8, 0);
        root.addView(choose, lp(-1, -2));
        margin(choose, 12, 0);

        TextView recentTitle = label("שירים אחרונים", 17, TEXT, true);
        root.addView(recentTitle, lp(-1, -2));
        margin(recentTitle, 12, 6);
        recentContainer = new LinearLayout(this);
        recentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(recentContainer, lp(-1, -2));

        LinearLayout selected = card();
        songTitle = label("עדיין לא נבחר שיר", 20, TEXT, true);
        songMeta = label("בחר קובץ שמע כדי להתחיל", 13, MUTED, false);
        selected.addView(songTitle, lp(-1, -2));
        selected.addView(songMeta, lp(-1, -2));
        margin(songMeta, 5, 8);

        LinearLayout playerRow = new LinearLayout(this);
        playButton = actionButton("▶ השמע", CARD_2);
        Button stop = actionButton("■ עצור", Color.rgb(72, 45, 66));
        playerRow.addView(playButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(48), 1);
        sp.leftMargin = dp(8);
        playerRow.addView(stop, sp);
        selected.addView(playerRow, lp(-1, dp(52)));
        playButton.setOnClickListener(v -> toggleSong());
        stop.setOnClickListener(v -> stopPlayer());
        root.addView(selected, lp(-1, -2));
        margin(selected, 12, 0);
        selected.setVisibility(View.GONE);

        LinearLayout detectCard = card();
        TextView dTitle = label("✨ זיהוי הפזמון", 19, TEXT, true);
        TextView dSub = label("המנוע משווה תבניות עוצמה לאורך השיר ומחפש קטע שחוזר בצורה דומה.", 13, MUTED, false);
        detectCard.addView(dTitle, lp(-1, -2));
        detectCard.addView(dSub, lp(-1, -2));
        margin(dSub, 5, 10);
        detectButton = actionButton("מצא את הפזמון", PURPLE);
        detectCard.addView(detectButton, lp(-1, dp(50)));
        detectButton.setOnClickListener(v -> detectChorusAsync());
        root.addView(detectCard, lp(-1, -2));
        margin(detectCard, 12, 0);

        progressCard = card();
        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressCard.addView(progressBar, lp(-1, dp(44)));
        progressText = label("מנתח את השיר…", 13, MUTED, false);
        progressText.setGravity(Gravity.CENTER);
        progressCard.addView(progressText, lp(-1, dp(34)));
        root.addView(progressCard, lp(-1, -2));
        margin(progressCard, 12, 0);
        progressCard.setVisibility(View.GONE);

        resultCard = card();
        TextView rTitle = label("🎯 הפזמון שנמצא", 19, TEXT, true);
        detectionText = label("—", 15, CYAN, true);
        resultCard.addView(rTitle, lp(-1, -2));
        resultCard.addView(detectionText, lp(-1, -2));
        margin(detectionText, 6, 12);

        TextView sLabel = label("התחלה", 13, MUTED, true);
        resultCard.addView(sLabel, lp(-1, -2));
        startValue = label("00:00", 15, TEXT, true);
        startValue.setGravity(Gravity.END);
        resultCard.addView(startValue, lp(-1, dp(28)));
        startBar = new SeekBar(this);
        resultCard.addView(startBar, lp(-1, dp(42)));

        TextView lLabel = label("אורך הקטע", 13, MUTED, true);
        resultCard.addView(lLabel, lp(-1, -2));
        lengthValue = label("00:12", 15, TEXT, true);
        lengthValue.setGravity(Gravity.END);
        resultCard.addView(lengthValue, lp(-1, dp(28)));
        lengthBar = new SeekBar(this);
        resultCard.addView(lengthBar, lp(-1, dp(42)));

        LinearLayout resultActions = new LinearLayout(this);
        previewButton = actionButton("▶ שמע את הפזמון", CARD_2);
        saveButton = actionButton("✂ חתוך ושמור", GREEN);
        resultActions.addView(previewButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(0, dp(50), 1);
        svp.leftMargin = dp(8);
        resultActions.addView(saveButton, svp);
        resultCard.addView(resultActions, lp(-1, dp(56)));
        previewButton.setOnClickListener(v -> previewChorus());
        saveButton.setOnClickListener(v -> chooseSaveLocation());

        startBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                syncRangeFromBars();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        lengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (progress < 5) bar.setProgress(5);
                if (fromUser) syncRangeFromBars();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        root.addView(resultCard, lp(-1, -2));
        margin(resultCard, 12, 0);
        resultCard.setVisibility(View.GONE);

        LinearLayout folderCard = card();
        folderTitle = label("📁 קבצי שמע בתיקייה", 18, TEXT, true);
        folderCard.addView(folderTitle, lp(-1, -2));
        folderFiles = new LinearLayout(this);
        folderFiles.setOrientation(LinearLayout.VERTICAL);
        folderCard.addView(folderFiles, lp(-1, -2));
        root.addView(folderCard, lp(-1, -2));
        margin(folderCard, 12, 0);
        folderCard.setVisibility(View.GONE);

        TextView footer = label("החיתוך נשמר כ־M4A באיכות שמע טובה. אפשר לבחור בכל פעם תיקיית יעד אחרת.", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, lp(-1, -2));
        margin(footer, 18, 0);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void openAudioPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_AUDIO);
    }

    private void openFolderPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }

    private void chooseSaveLocation() {
        if (songUri == null) {
            toast("בחר קודם שיר");
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/mp4");
        String safe = songName.replaceAll("[\\\\/:*?\"<>|]", "_");
        i.putExtra(Intent.EXTRA_TITLE, safe + "_פזמון.m4a");
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, CREATE_OUTPUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_AUDIO) {
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {}
            setSong(uri, getDisplayName(uri));
        } else if (requestCode == PICK_FOLDER) {
            try {
                int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {}
            loadFolder(uri);
        } else if (requestCode == CREATE_OUTPUT) {
            startCutAndCopy(uri);
        }
    }

    private void setSong(Uri uri, String name) {
        stopPlayer();
        songUri = uri;
        songName = name == null || name.isEmpty() ? "שיר" : name;
        songDurationMs = getDuration(uri);
        songTitle.setText("🎧 " + songName);
        songMeta.setText("אורך: " + formatTime(songDurationMs) + "  •  מוכן לניתוח");
        playButton.setText("▶ השמע");
        resultCard.setVisibility(View.GONE);
        selectedSongVisible(true);
        addRecent(uri.toString());
    }

    private void selectedSongVisible(boolean visible) {
        View parent = songTitle;
        LinearLayout selected = (LinearLayout) parent.getParent();
        selected.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private long getDuration(Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(this, uri);
            String s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return s == null ? 0 : Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private String getDisplayName(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(uri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}
        String s = uri.getLastPathSegment();
        return s == null ? "שיר" : s;
    }

    private void addRecent(String uri) {
        ArrayList<String> list = readRecent();
        list.remove(uri);
        list.add(0, uri);
        while (list.size() > 6) list.remove(list.size() - 1);
        prefs.edit().putString("recent", join(list)).apply();
        refreshRecent();
    }

    private ArrayList<String> readRecent() {
        String s = prefs.getString("recent", "");
        ArrayList<String> list = new ArrayList<>();
        if (!s.isEmpty()) Collections.addAll(list, s.split("\\n"));
        return list;
    }

    private String join(ArrayList<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (b.length() > 0) b.append("\n");
            b.append(s);
        }
        return b.toString();
    }

    private void refreshRecent() {
        if (recentContainer == null) return;
        recentContainer.removeAllViews();
        ArrayList<String> list = readRecent();
        int count = 0;
        for (String s : list) {
            try {
                Uri u = Uri.parse(s);
                String name = getDisplayName(u);
                Button b = actionButton("♪  " + name, CARD);
                b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                recentContainer.addView(b, lp(-1, dp(46)));
                margin(b, 3, 3);
                b.setOnClickListener(v -> setSong(u, name));
                count++;
            } catch (Exception ignored) {}
        }
        if (count == 0) {
            TextView empty = label("השירים שבחרת יופיעו כאן לגישה מהירה.", 13, MUTED, false);
            recentContainer.addView(empty, lp(-1, dp(38)));
        }
    }

    private void loadFolder(Uri treeUri) {
        if (folderFiles == null) return;
        folderFiles.removeAllViews();
        folderTitle.setText("📁 " + getDisplayName(treeUri));
        LinearLayout parent = (LinearLayout) folderTitle.getParent();
        parent.setVisibility(View.VISIBLE);

        new Thread(() -> {
            ArrayList<FileItem> items = new ArrayList<>();
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                String[] projection = {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                };
                android.database.Cursor c = getContentResolver().query(children, projection, null, null, null);
                if (c != null) {
                    try {
                        while (c.moveToNext() && items.size() < 40) {
                            String id = c.getString(0);
                            String name = c.getString(1);
                            String mime = c.getString(2);
                            if (mime != null && mime.startsWith("audio/")) {
                                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                                items.add(new FileItem(name, fileUri));
                            }
                        }
                    } finally {
                        c.close();
                    }
                }
            } catch (Exception ignored) {}

            runOnUiThread(() -> {
                if (items.isEmpty()) {
                    TextView empty = label("לא נמצאו קבצי שמע ישירות בתיקייה הזאת. אפשר להיכנס לתיקיית משנה ולבחור שיר דרך הכפתור הרגיל.", 13, MUTED, false);
                    folderFiles.addView(empty, lp(-1, dp(70)));
                    return;
                }
                for (FileItem item : items) {
                    Button b = actionButton("♪  " + item.name, CARD_2);
                    b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    folderFiles.addView(b, lp(-1, dp(48)));
                    margin(b, 3, 3);
                    b.setOnClickListener(v -> setSong(item.uri, item.name));
                }
            });
        }).start();
    }

    private void toggleSong() {
        if (songUri == null) {
            toast("בחר קודם שיר");
            return;
        }
        if (player != null && player.isPlaying()) {
            player.pause();
            playButton.setText("▶ המשך");
            return;
        }
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(this, songUri);
            player.setOnPreparedListener(mp -> {
                mp.start();
                playButton.setText("❚❚ השהה");
            });
            player.setOnCompletionListener(mp -> playButton.setText("▶ השמע"));
            player.prepareAsync();
        } catch (Exception e) {
            toast("לא הצלחתי להשמיע את הקובץ");
            releasePlayer();
        }
    }

    private void previewChorus() {
        if (songUri == null) return;
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(this, songUri);
            player.setOnPreparedListener(mp -> {
                mp.seekTo((int) detectedStartMs);
                mp.start();
                previewButton.setText("❚❚ מנגן פזמון");
                handler.postDelayed(this::stopPreviewButton, (detectedLengthMs + 500));
            });
            player.setOnCompletionListener(mp -> stopPreviewButton());
            player.prepareAsync();
        } catch (Exception e) {
            toast("לא הצלחתי לנגן את הקטע");
            releasePlayer();
        }
    }

    private void stopPreviewButton() {
        if (previewButton != null) previewButton.setText("▶ שמע את הפזמון");
    }

    private void stopPlayer() {
        releasePlayer();
        if (playButton != null) playButton.setText("▶ השמע");
        stopPreviewButton();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void detectChorusAsync() {
        if (songUri == null) {
            toast("בחר שיר לפני הזיהוי");
            return;
        }
        detectButton.setEnabled(false);
        progressCard.setVisibility(View.VISIBLE);
        progressText.setText("מנתח את מבנה השיר…");
        new Thread(() -> {
            try {
                Analysis analysis = analyzeSong(songUri);
                long[] result = chooseRepeatedSection(analysis.features, analysis.durationMs);
                detectedStartMs = result[0];
                detectedLengthMs = result[1];

                runOnUiThread(() -> {
                    progressCard.setVisibility(View.GONE);
                    detectButton.setEnabled(true);
                    resultCard.setVisibility(View.VISIBLE);
                    configureBars();
                    updateRangeLabels();
                    detectionText.setText("מצאתי קטע חוזר/בולט סביב " + formatTime(detectedStartMs) +
                            "  •  אורך " + formatTime(detectedLengthMs));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressCard.setVisibility(View.GONE);
                    detectButton.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("לא הצלחתי לנתח")
                            .setMessage("הקובץ לא נתמך לניתוח או שהמכשיר לא הצליח לפענח אותו. אפשר לנסות MP3, M4A, AAC, WAV או קובץ וידאו עם שמע.")
                            .setPositiveButton("בסדר", null)
                            .show();
                });
            }
        }).start();
    }

    private void configureBars() {
        int total = (int) Math.max(1, songDurationMs / 1000);
        int len = (int) Math.max(5, Math.min(30, detectedLengthMs / 1000));
        startBar.setMax(Math.max(0, total - len));
        lengthBar.setMax(Math.max(5, Math.min(30, total)));
        startBar.setProgress(Math.min((int)(detectedStartMs / 1000), startBar.getMax()));
        lengthBar.setProgress(Math.min(len, lengthBar.getMax()));
        syncRangeFromBars();
    }

    private void syncRangeFromBars() {
        int length = Math.max(5, lengthBar.getProgress());
        int maxStart = Math.max(0, (int)(songDurationMs / 1000) - length);
        if (startBar.getProgress() > maxStart) startBar.setProgress(maxStart);
        detectedStartMs = startBar.getProgress() * 1000L;
        detectedLengthMs = length * 1000L;
        updateRangeLabels();
        detectionText.setText("הקטע שבחרת: " + formatTime(detectedStartMs) + "  •  " + formatTime(detectedLengthMs));
    }

    private void updateRangeLabels() {
        if (startValue != null) startValue.setText(formatTime(detectedStartMs));
        if (lengthValue != null) lengthValue.setText(formatTime(detectedLengthMs));
    }

    private Analysis analyzeSong(Uri uri) throws Exception {
        return readRmsFeatures(uri, 44100);
    }

    private Analysis readRmsFeatures(Uri uri, int ignoredSampleRate) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, uri, null);
        int track = findAudioTrack(extractor);
        if (track < 0) throw new IllegalStateException("No audio");
        extractor.selectTrack(track);
        MediaFormat format = extractor.getTrackFormat(track);
        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : songDurationMs * 1000L;
        String mime = format.getString(MediaFormat.KEY_MIME);

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();

        ArrayList<Float> features = new ArrayList<>();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long blockSamples = Math.max(1, (long)(sampleRate * 0.5f) * channels);
        long count = 0;
        double sum = 0;
        boolean inputDone = false;
        boolean outputDone = false;

        try {
            while (!outputDone) {
                if (!inputDone) {
                    int in = decoder.dequeueInputBuffer(10000);
                    if (in >= 0) {
                        ByteBuffer ib = decoder.getInputBuffer(in);
                        ib.clear();
                        int size = extractor.readSampleData(ib, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            if (pts < 0) pts = 0;
                            decoder.queueInputBuffer(in, 0, size, pts, extractor.getSampleFlags());
                            extractor.advance();
                        }
                    }
                }

                int out = decoder.dequeueOutputBuffer(info, 10000);
                if (out == MediaCodec.INFO_TRY_AGAIN_LATER) continue;
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
                if (out >= 0) {
                    ByteBuffer buf = decoder.getOutputBuffer(out);
                    if (buf != null && info.size > 0) {
                        ByteBuffer pcm = buf.duplicate();
                        pcm.position(Math.max(0, info.offset));
                        pcm.limit(Math.min(buf.capacity(), info.offset + info.size));
                        pcm.order(ByteOrder.LITTLE_ENDIAN);
                        while (pcm.remaining() >= 2) {
                            short s = pcm.getShort();
                            double x = s / 32768.0;
                            sum += x * x;
                            count++;
                            if (count >= blockSamples) {
                                features.add((float)Math.sqrt(sum / count));
                                count = 0;
                                sum = 0;
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
        } finally {
            try { decoder.stop(); } catch (Exception ignored) {}
            try { decoder.release(); } catch (Exception ignored) {}
            extractor.release();
        }

        if (count > 0) features.add((float)Math.sqrt(sum / count));
        long durationMs = durationUs / 1000L;
        if (durationMs <= 0) durationMs = songDurationMs;
        return new Analysis(features, durationMs);
    }

    private int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private long[] chooseRepeatedSection(ArrayList<Float> f, long durationMs) {
        if (f == null || f.size() < 16) {
            long len = Math.min(12000, durationMs);
            return new long[]{Math.max(0, (durationMs - len) / 2), len};
        }

        int window = Math.min(36, Math.max(18, f.size() / 16));
        if (f.size() < window * 2 + 20) {
            window = Math.max(10, Math.min(20, f.size() / 3));
        }
        if (f.size() <= window * 2) {
            long len = Math.min(12000, durationMs);
            return new long[]{Math.max(0, (durationMs - len) / 2), len};
        }

        double global = 0;
        for (float x : f) global += x;
        global /= f.size();
        if (global < 0.0001) global = 0.0001;

        double bestScore = -999;
        int bestA = 0;
        int minGap = Math.max(window + 8, 34);
        int startLimit = Math.max(2, Math.min(30, f.size() / 12));
        int endLimit = Math.max(startLimit + window + minGap + 1, f.size() - window - 2);

        for (int i = startLimit; i < endLimit; i++) {
            int maxJ = f.size() - window;
            for (int j = i + minGap; j <= maxJ; j += 2) {
                double corr = correlation(f, i, j, window);
                double meanA = mean(f, i, window);
                double meanB = mean(f, j, window);
                double energy = Math.min(2.0, ((meanA + meanB) * 0.5) / global);
                double score = corr + 0.10 * energy;
                if (score > bestScore) {
                    bestScore = score;
                    bestA = meanA >= meanB * 0.92 ? i : j;
                }
            }
        }

        long start = bestA * 500L;
        long len = Math.min(window * 500L, 30000L);
        if (start + len > durationMs) start = Math.max(0, durationMs - len);
        return new long[]{start, Math.max(5000, len)};
    }

    private double mean(ArrayList<Float> f, int start, int count) {
        double x = 0;
        for (int i = 0; i < count; i++) x += f.get(start + i);
        return x / count;
    }

    private double correlation(ArrayList<Float> f, int a, int b, int count) {
        double ma = mean(f, a, count);
        double mb = mean(f, b, count);
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < count; i++) {
            double xa = f.get(a + i) - ma;
            double xb = f.get(b + i) - mb;
            num += xa * xb;
            da += xa * xa;
            db += xb * xb;
        }
        if (da < 1e-8 || db < 1e-8) return 0;
        return num / Math.sqrt(da * db);
    }

    private void startCutAndCopy(Uri destination) {
        if (songUri == null) return;
        progressCard.setVisibility(View.VISIBLE);
        progressText.setText("חותך את הפזמון וממיר ל־M4A…");
        saveButton.setEnabled(false);
        new Thread(() -> {
            File temp = new File(getCacheDir(), "chorus_" + System.currentTimeMillis() + ".m4a");
            try {
                encodeClip(songUri, detectedStartMs * 1000L, (detectedStartMs + detectedLengthMs) * 1000L, temp);
                OutputStream out = getContentResolver().openOutputStream(destination, "w");
                if (out == null) throw new IllegalStateException("No output stream");
                FileInputStream in = new FileInputStream(temp);
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (n > 0) out.write(buffer, 0, n);
                }
                out.flush();
                out.close();
                in.close();
                temp.delete();
                runOnUiThread(() -> {
                    progressCard.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("✅ הפזמון נשמר")
                            .setMessage("הקטע נשמר בהצלחה כקובץ M4A בתיקייה שבחרת.")
                            .setPositiveButton("סגור", null)
                            .show();
                });
            } catch (Exception e) {
                if (temp.exists()) temp.delete();
                runOnUiThread(() -> {
                    progressCard.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("השמירה נכשלה")
                            .setMessage(e.getMessage() == null ? "שגיאה לא ידועה" : e.getMessage())
                            .setPositiveButton("בסדר", null)
                            .show();
                });
            }
        }).start();
    }

    private void encodeClip(Uri source, long startUs, long endUs, File output) throws Exception {
        PcmData pcm = decodeClip(source, startUs, endUs);
        if (pcm.bytes.length < pcm.channels * 2) throw new IllegalStateException("הקטע קצר מדי");

        MediaFormat encFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", pcm.sampleRate, pcm.channels);
        encFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, 2);
        encFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        encFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        MediaCodec encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        MediaMuxer muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean[] muxStarted = new boolean[]{false};
        boolean inputDone = false;
        boolean outputDone = false;
        int offset = 0;
        long ptsUs = 0;
        int frameBytes = pcm.channels * 2;

        try {
            while (!inputDone) {
                int in = encoder.dequeueInputBuffer(10000);
                if (in >= 0) {
                    ByteBuffer ib = encoder.getInputBuffer(in);
                    int remaining = pcm.bytes.length - offset;
                    if (remaining <= 0) {
                        encoder.queueInputBuffer(in, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        int take = Math.min(ib.capacity(), remaining);
                        take -= take % frameBytes;
                        if (take <= 0) take = Math.min(frameBytes, remaining);
                        ib.clear();
                        ib.put(pcm.bytes, offset, take);
                        long samples = take / frameBytes;
                        long durationUs = samples * 1000000L / pcm.sampleRate;
                        encoder.queueInputBuffer(in, 0, take, ptsUs, 0);
                        ptsUs += durationUs;
                        offset += take;
                    }
                }
                drainEncoder(encoder, muxer, muxStarted);
            }

            while (!outputDone) {
                outputDone = drainEncoder(encoder, muxer, muxStarted);
                if (!outputDone) Thread.sleep(5);
            }

            if (!muxStarted[0]) throw new IllegalStateException("לא נוצר קובץ שמע");
        } finally {
            try { encoder.stop(); } catch (Exception ignored) {}
            try { encoder.release(); } catch (Exception ignored) {}
            try { muxer.stop(); } catch (Exception ignored) {}
            try { muxer.release(); } catch (Exception ignored) {}
        }
    }

    private boolean drainEncoder(MediaCodec encoder, MediaMuxer muxer, boolean[] muxStarted) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int out = encoder.dequeueOutputBuffer(info, 10000);
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxStarted[0]) throw new IllegalStateException("format changed twice");
                int track = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                muxStarted[0] = true;
            } else if (out >= 0) {
                ByteBuffer data = encoder.getOutputBuffer(out);
                if (data != null && info.size > 0 && muxStarted[0] &&
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    muxer.writeSampleData(0, data, info);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                encoder.releaseOutputBuffer(out, false);
                if (eos) return true;
            }
        }
    }

    private PcmData decodeClip(Uri source, long startUs, long endUs) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, source, null);
        int track = findAudioTrack(extractor);
        if (track < 0) throw new IllegalStateException("אין רצועת שמע");
        extractor.selectTrack(track);
        MediaFormat format = extractor.getTrackFormat(track);
        String mime = format.getString(MediaFormat.KEY_MIME);
        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();
        extractor.seekTo(Math.max(0, startUs - 1000000L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;

        try {
            while (!outputDone) {
                if (!inputDone) {
                    int in = decoder.dequeueInputBuffer(10000);
                    if (in >= 0) {
                        ByteBuffer ib = decoder.getInputBuffer(in);
                        ib.clear();
                        int size = extractor.readSampleData(ib, 0);
                        long pts = extractor.getSampleTime();
                        if (size < 0 || pts > endUs + 1000000L) {
                            decoder.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            if (pts < 0) pts = 0;
                            decoder.queueInputBuffer(in, 0, size, pts, extractor.getSampleFlags());
                            extractor.advance();
                        }
                    }
                }

                int out = decoder.dequeueOutputBuffer(info, 10000);
                if (out == MediaCodec.INFO_TRY_AGAIN_LATER) continue;
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
                if (out >= 0) {
                    ByteBuffer buf = decoder.getOutputBuffer(out);
                    long pts = info.presentationTimeUs;
                    if (buf != null && info.size > 0 && pts >= startUs && pts <= endUs) {
                        ByteBuffer part = buf.duplicate();
                        part.position(Math.max(0, info.offset));
                        part.limit(Math.min(buf.capacity(), info.offset + info.size));
                        byte[] data = new byte[part.remaining()];
                        part.get(data);
                        pcm.write(data);
                    }
                    decoder.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    if (pts > endUs + 500000L) outputDone = true;
                }
            }
        } finally {
            try { decoder.stop(); } catch (Exception ignored) {}
            try { decoder.release(); } catch (Exception ignored) {}
            extractor.release();
        }

        return new PcmData(pcm.toByteArray(), sampleRate, channels);
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long minutes = total / 60;
        long seconds = total % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private static class Analysis {
        final ArrayList<Float> features;
        final long durationMs;
        Analysis(ArrayList<Float> f, long d) { features = f; durationMs = d; }
    }

    private static class PcmData {
        final byte[] bytes;
        final int sampleRate;
        final int channels;
        PcmData(byte[] b, int sr, int ch) { bytes = b; sampleRate = sr; channels = ch; }
    }

    private static class FileItem {
        final String name;
        final Uri uri;
        FileItem(String n, Uri u) { name = n; uri = u; }
    }
}
