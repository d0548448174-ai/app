package com.vybe.musicfeed;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.GestureDetector;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 804;
    private static final int PREVIEW_MS = 18000;
    private static final String PREFS = "vybe_music_feed";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Track> tracks = new ArrayList<>();

    private SharedPreferences preferences;
    private FrameLayout root;
    private ImageView backdrop;
    private View scrim;
    private LinearLayout header, details, sideRail, progressPanel, navBar, emptyPanel;
    private TextView countBadge, titleText, artistText, albumText, timeCurrent, timeTotal;
    private TextView playButton, likeIcon, likeLabel, markButton, fullButton, accessButton;
    private TextView emptyTitle, emptySubtitle, feedNav, likedNav, libraryNav;
    private ImageView discImage;
    private SeekBar timeline;
    private MediaPlayer player;
    private GestureDetector gestureDetector;
    private int currentIndex = -1;
    private boolean fullSongMode = false;
    private boolean favoritesOnly = false;
    private boolean userSeeking = false;
    private boolean loading = false;
    private long snippetEndMs = 0L;
    private long currentStartMs = 0L;
    private String searchQuery = "";

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (player != null && currentIndex >= 0) {
                try {
                    if (player.isPlaying()) {
                        int position = player.getCurrentPosition();
                        if (!fullSongMode && position >= snippetEndMs) {
                            nextTrack(1);
                            return;
                        }
                        if (!userSeeking) {
                            Track track = tracks.get(currentIndex);
                            int duration = (int) Math.max(1L, track.duration);
                            timeline.setProgress((int) (position * 1000L / duration));
                            timeCurrent.setText(formatTime(position));
                        }
                    }
                } catch (IllegalStateException ignored) {
                    // A track was changed while the last progress tick was queued.
                }
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(8, 10, 16));
        window.setNavigationBarColor(Color.rgb(8, 10, 16));
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        handler.post(progressTick);
        if (hasAudioPermission()) {
            loadLibraryAsync();
        } else {
            showEmptyState("כל המוזיקה שלך. פיד אחד.", "תני ל‑VYBE גישה לקובצי האודיו במכשיר. המוזיקה נשארת אצלך — בלי העלאה לענן.", "גישה למוזיקה");
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 26, 52), Color.rgb(8, 10, 16), Color.rgb(21, 11, 35)}));

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.40f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD9080A10, 0x50101422, 0xF2080A10}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(20), dp(12), dp(18), dp(8));
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, dp(72), Gravity.TOP);
        root.addView(header, headerLp);

        TextView logo = text("VYBE", 25, Color.WHITE, true);
        logo.setLetterSpacing(0.10f);
        header.addView(logo, new LinearLayout.LayoutParams(-2, -2));
        TextView logoDot = text("●", 10, Color.rgb(215, 255, 79), true);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(-2, -2);
        dotLp.leftMargin = dp(4);
        header.addView(logoDot, dotLp);

        countBadge = text("LOCAL MIX", 10, Color.rgb(215, 255, 79), true);
        countBadge.setGravity(Gravity.CENTER);
        countBadge.setPadding(dp(10), dp(7), dp(10), dp(7));
        countBadge.setBackground(pill(0x332DFFB7, 0x552BFFB7, 99));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.leftMargin = dp(12);
        header.addView(countBadge, badgeLp);
        View headerSpacer = new View(this);
        header.addView(headerSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView search = text("⌕", 29, Color.WHITE, true);
        search.setGravity(Gravity.CENTER);
        search.setBackground(pill(0x22FFFFFF, 0x33FFFFFF, 100));
        header.addView(search, new LinearLayout.LayoutParams(dp(46), dp(44)));
        search.setOnClickListener(v -> showSearchDialog());

        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.BOTTOM | Gravity.START);
        details.setPadding(dp(22), dp(8), dp(8), dp(4));
        FrameLayout.LayoutParams detailsLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        detailsLp.leftMargin = 0;
        detailsLp.rightMargin = dp(92);
        detailsLp.bottomMargin = dp(210);
        root.addView(details, detailsLp);

        TextView vibeTag = text("✦  YOUR PHONE. YOUR SOUND.", 10, Color.rgb(215, 255, 79), true);
        vibeTag.setLetterSpacing(0.06f);
        details.addView(vibeTag, new LinearLayout.LayoutParams(-2, -2));

        titleText = text("בחרי את הביט שלך", 27, Color.WHITE, true);
        titleText.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(8);
        details.addView(titleText, titleLp);

        artistText = text("האוסף המקומי שלך", 16, 0xFFF0F0F4, true);
        LinearLayout.LayoutParams artistLp = new LinearLayout.LayoutParams(-1, -2);
        artistLp.topMargin = dp(5);
        details.addView(artistText, artistLp);

        albumText = text("מיני־קטעים • גלילה למעבר", 12, 0xFFBFC3D0, false);
        LinearLayout.LayoutParams albumLp = new LinearLayout.LayoutParams(-1, -2);
        albumLp.topMargin = dp(4);
        details.addView(albumText, albumLp);

        sideRail = new LinearLayout(this);
        sideRail.setGravity(Gravity.CENTER_HORIZONTAL);
        sideRail.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(dp(76), -2, Gravity.END | Gravity.BOTTOM);
        railLp.rightMargin = dp(12);
        railLp.bottomMargin = dp(220);
        root.addView(sideRail, railLp);

        FrameLayout discFrame = new FrameLayout(this);
        discFrame.setBackground(pill(0xCC151721, 0x88FFFFFF, 100));
        discFrame.setClipToOutline(true);
        discFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        discImage = new ImageView(this);
        discImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        discFrame.addView(discImage, new FrameLayout.LayoutParams(-1, -1));
        TextView discFallback = text("♫", 25, Color.WHITE, true);
        discFallback.setGravity(Gravity.CENTER);
        discFallback.setOnClickListener(v -> togglePlayback());
        discImage.setOnClickListener(v -> togglePlayback());
        // A fallback note sits behind the album-art view when no cover exists.
        discFrame.addView(discFallback, new FrameLayout.LayoutParams(-1, -1));
        sideRail.addView(discFrame, new LinearLayout.LayoutParams(dp(62), dp(62)));

        addRailAction("♥", "אהבתי", () -> toggleLike());
        addRailAction("♫", "הבא", () -> nextTrack(1));
        addRailAction("Ⅱ", "נגן / עצור", () -> togglePlayback());

        progressPanel = new LinearLayout(this);
        progressPanel.setOrientation(LinearLayout.VERTICAL);
        progressPanel.setPadding(dp(18), dp(10), dp(18), dp(12));
        progressPanel.setBackground(pill(0xB5151721, 0x22000000, 22));
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        progressLp.leftMargin = dp(14);
        progressLp.rightMargin = dp(14);
        progressLp.bottomMargin = dp(76);
        root.addView(progressPanel, progressLp);

        LinearLayout times = new LinearLayout(this);
        times.setGravity(Gravity.CENTER_VERTICAL);
        times.setOrientation(LinearLayout.HORIZONTAL);
        timeCurrent = text("0:00", 11, 0xFFE5E7EE, true);
        timeTotal = text("0:00", 11, 0xFFAAAEBB, false);
        times.addView(timeCurrent, new LinearLayout.LayoutParams(-2, -2));
        View timeSpacer = new View(this);
        times.addView(timeSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        times.addView(timeTotal, new LinearLayout.LayoutParams(-2, -2));
        progressPanel.addView(times);

        timeline = new SeekBar(this);
        timeline.setMax(1000);
        timeline.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(-1, dp(28));
        seekLp.topMargin = dp(1);
        progressPanel.addView(timeline, seekLp);
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && currentIndex >= 0) {
                    long target = tracks.get(currentIndex).duration * progress / 1000L;
                    timeCurrent.setText(formatTime(target));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (player != null && currentIndex >= 0) {
                    long target = tracks.get(currentIndex).duration * bar.getProgress() / 1000L;
                    jumpTo(target);
                }
            }
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(42));
        actionsLp.topMargin = dp(5);
        progressPanel.addView(actions, actionsLp);

        fullButton = text("⤢  נגן את כל השיר", 13, Color.rgb(8, 10, 16), true);
        fullButton.setGravity(Gravity.CENTER);
        fullButton.setBackground(pill(0xFFD7FF4F, 0xFFD7FF4F, 100));
        actions.addView(fullButton, new LinearLayout.LayoutParams(0, -1, 1f));
        fullButton.setOnClickListener(v -> toggleFullSong());

        markButton = text("  ♡  סמן פזמון", 12, Color.WHITE, true);
        markButton.setGravity(Gravity.CENTER);
        markButton.setBackground(pill(0x222BFFB7, 0x6637E6B2, 100));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(0, -1, 1f);
        markLp.leftMargin = dp(8);
        actions.addView(markButton, markLp);
        markButton.setOnClickListener(v -> saveHighlight());

        navBar = new LinearLayout(this);
        navBar.setGravity(Gravity.CENTER);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setPadding(dp(12), dp(4), dp(12), dp(6));
        navBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF20A0B12, 0xFF080A10}));
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(-1, dp(66), Gravity.BOTTOM);
        root.addView(navBar, navLp);
        feedNav = navItem("⌁\nפיד", true);
        likedNav = navItem("♥\nאהבתי", false);
        libraryNav = navItem("▤\nהשירים שלי", false);
        navBar.addView(feedNav, new LinearLayout.LayoutParams(0, -1, 1f));
        navBar.addView(likedNav, new LinearLayout.LayoutParams(0, -1, 1f));
        navBar.addView(libraryNav, new LinearLayout.LayoutParams(0, -1, 1f));
        feedNav.setOnClickListener(v -> {
            favoritesOnly = false;
            searchQuery = "";
            styleNav();
            if (currentIndex < 0 && !tracks.isEmpty()) playTrack(0, false);
        });
        likedNav.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            searchQuery = "";
            styleNav();
            List<Integer> visible = visibleIndices();
            if (favoritesOnly && visible.isEmpty()) {
                favoritesOnly = false;
                styleNav();
                Toast.makeText(this, "עוד לא סימנת שירים שאהבת", Toast.LENGTH_SHORT).show();
            } else if (!visible.isEmpty() && !visible.contains(currentIndex)) {
                playTrack(visible.get(0), false);
            }
        });
        libraryNav.setOnClickListener(v -> showLibraryDialog());

        emptyPanel = new LinearLayout(this);
        emptyPanel.setOrientation(LinearLayout.VERTICAL);
        emptyPanel.setGravity(Gravity.CENTER);
        emptyPanel.setPadding(dp(32), dp(20), dp(32), dp(20));
        FrameLayout.LayoutParams emptyLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        emptyLp.bottomMargin = dp(18);
        root.addView(emptyPanel, emptyLp);

        emptyTitle = text("כל המוזיקה שלך. פיד אחד.", 25, Color.WHITE, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setMaxLines(3);
        emptyPanel.addView(emptyTitle, new LinearLayout.LayoutParams(-1, -2));

        emptySubtitle = text("פיד מוזיקה מקומי", 14, 0xFFC4C8D4, false);
        emptySubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(12);
        emptyPanel.addView(emptySubtitle, subLp);

        accessButton = text("גישה למוזיקה  →", 14, Color.rgb(8, 10, 16), true);
        accessButton.setGravity(Gravity.CENTER);
        accessButton.setPadding(dp(24), dp(14), dp(24), dp(14));
        accessButton.setBackground(pill(0xFFD7FF4F, 0xFFD7FF4F, 100));
        LinearLayout.LayoutParams accessLp = new LinearLayout.LayoutParams(-1, -2);
        accessLp.topMargin = dp(22);
        emptyPanel.addView(accessButton, accessLp);
        accessButton.setOnClickListener(v -> {
            if (!hasAudioPermission()) requestAudioPermission();
            else loadLibraryAsync();
        });

        setContentView(root);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { togglePlayback(); return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dy) > dp(70) && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                    nextTrack(dy < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        root.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        details.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        backdrop.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        scrim.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void addRailAction(String icon, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, dp(65));
        itemLp.topMargin = dp(11);
        sideRail.addView(item, itemLp);
        TextView glyph = text(icon, 26, Color.WHITE, true);
        glyph.setGravity(Gravity.CENTER);
        item.addView(glyph, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView caption = text(label, 10, 0xFFF4F5F8, true);
        caption.setGravity(Gravity.CENTER);
        item.addView(caption, new LinearLayout.LayoutParams(-1, dp(20)));
        item.setOnClickListener(v -> action.run());
    }

    private TextView navItem(String label, boolean selected) {
        TextView view = text(label, 12, selected ? 0xFFD7FF4F : 0xFF9FA4B2, true);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private void styleNav() {
        feedNav.setTextColor((!favoritesOnly) ? 0xFFD7FF4F : 0xFF9FA4B2);
        likedNav.setTextColor((favoritesOnly) ? 0xFFD7FF4F : 0xFF9FA4B2);
        libraryNav.setTextColor(0xFF9FA4B2);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return tv;
    }

    private GradientDrawable pill(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_AUDIO);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLibraryAsync();
            } else {
                showEmptyState("בלי הרשאה, אין פיד 🎧", "צריך לאפשר גישה לאודיו כדי להציג את השירים ששמורים במכשיר. שום קובץ לא מועלה לשרת.", "נסי שוב");
            }
        }
    }

    private void showEmptyState(String title, String subtitle, String button) {
        emptyPanel.setVisibility(View.VISIBLE);
        emptyTitle.setText(title);
        emptySubtitle.setText(subtitle);
        accessButton.setText(button + "  →");
        header.setVisibility(View.VISIBLE);
        details.setVisibility(View.GONE);
        sideRail.setVisibility(View.GONE);
        progressPanel.setVisibility(View.GONE);
        navBar.setVisibility(View.GONE);
    }

    private void loadLibraryAsync() {
        if (loading) return;
        loading = true;
        showEmptyState("מסדרת לך את הווייב…", "סורקת את ספריית האודיו המקומית", "רענון ספרייה");
        accessButton.setEnabled(false);
        new Thread(() -> {
            ArrayList<Track> found = queryTracks();
            runOnUiThread(() -> {
                loading = false;
                accessButton.setEnabled(true);
                tracks.clear();
                tracks.addAll(found);
                if (tracks.isEmpty()) {
                    showEmptyState("לא מצאתי שירים עדיין", "ודאי שיש קובצי מוזיקה שמורים במכשיר ושההרשאה אושרה.", "סריקה מחדש");
                    return;
                }
                emptyPanel.setVisibility(View.GONE);
                details.setVisibility(View.VISIBLE);
                sideRail.setVisibility(View.VISIBLE);
                progressPanel.setVisibility(View.VISIBLE);
                navBar.setVisibility(View.VISIBLE);
                countBadge.setText(tracks.size() + " TRACKS");
                favoritesOnly = false;
                searchQuery = "";
                styleNav();
                playTrack(0, false);
            });
        }).start();
    }

    private ArrayList<Track> queryTracks() {
        ArrayList<Track> result = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
        };
        String selection = MediaStore.Audio.Media.DURATION + " > ?";
        String[] args = new String[]{"10000"};
        String sort = MediaStore.Audio.Media.DATE_ADDED + " DESC, " + MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(collection, projection, selection, args, sort);
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                while (cursor.moveToNext()) {
                    Track t = new Track();
                    t.id = cursor.getLong(idCol);
                    t.title = safe(cursor.getString(titleCol), "ללא שם");
                    t.artist = safe(cursor.getString(artistCol), "אמן לא ידוע");
                    t.album = safe(cursor.getString(albumCol), "אלבום לא ידוע");
                    t.duration = Math.max(0, cursor.getLong(durationCol));
                    t.albumId = cursor.getLong(albumIdCol);
                    t.uri = ContentUris.withAppendedId(collection, t.id);
                    t.liked = preferences.getBoolean("like_" + t.id, false);
                    result.add(t);
                }
            }
        } catch (SecurityException ignored) {
            // Permission may have been withdrawn while scanning.
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value.trim())) return fallback;
        return value.trim();
    }

    private List<Integer> visibleIndices() {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            if (favoritesOnly && !t.liked) continue;
            if (!searchQuery.isEmpty()) {
                String haystack = (t.title + " " + t.artist + " " + t.album).toLowerCase(Locale.ROOT);
                if (!haystack.contains(searchQuery.toLowerCase(Locale.ROOT))) continue;
            }
            result.add(i);
        }
        return result;
    }

    private void playTrack(int index, boolean fullSong) {
        if (index < 0 || index >= tracks.size()) return;
        releasePlayer();
        currentIndex = index;
        fullSongMode = fullSong;
        Track t = tracks.get(index);
        titleText.setText(t.title);
        artistText.setText(t.artist);
        albumText.setText(t.artist + "  •  " + t.album);
        timeTotal.setText(formatTime(t.duration));
        timeCurrent.setText("0:00");
        timeline.setProgress(0);
        bindArt(t);
        likeIconText(t);
        updatePlaybackButtons();
        applyTrackBackdrop(index);

        long start = fullSong ? 0L : getHighlightStart(t);
        currentStartMs = start;
        snippetEndMs = Math.min(t.duration, start + PREVIEW_MS);
        try {
            MediaPlayer newPlayer = new MediaPlayer();
            player = newPlayer;
            newPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            newPlayer.setDataSource(this, t.uri);
            newPlayer.setOnPreparedListener(mp -> {
                if (player != mp) return;
                int safeStart = (int) Math.max(0L, Math.min(start, Math.max(0L, t.duration - 500L)));
                mp.seekTo(safeStart);
                snippetEndMs = Math.min(t.duration, safeStart + PREVIEW_MS);
                mp.start();
                updatePlaybackButtons();
            });
            newPlayer.setOnCompletionListener(mp -> nextTrack(1));
            newPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "לא הצלחתי לנגן את הקובץ הזה", Toast.LENGTH_SHORT).show();
                nextTrack(1);
                return true;
            });
            newPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן לפתוח את קובץ האודיו", Toast.LENGTH_SHORT).show();
            nextTrack(1);
        }
    }

    private long getHighlightStart(Track t) {
        long saved = preferences.getLong("highlight_" + t.id, -1L);
        if (saved >= 0 && saved < Math.max(1L, t.duration)) return saved;
        // A reasonable preview guess for tracks without a saved marker; not guaranteed to be the chorus.
        if (t.duration <= PREVIEW_MS) return 0L;
        long estimate = (long) (t.duration * 0.36f);
        return Math.max(0L, Math.min(estimate, t.duration - PREVIEW_MS));
    }

    private void bindArt(Track track) {
        Uri art = track.albumId > 0
                ? Uri.parse("content://media/external/audio/albumart/" + track.albumId)
                : null;
        backdrop.setImageDrawable(null);
        discImage.setImageDrawable(null);
        if (art != null) {
            try {
                backdrop.setImageURI(art);
                discImage.setImageURI(art);
            } catch (Exception ignored) {
                // The artwork is optional; the gradient remains as a fallback.
            }
        }
        discImage.setBackground(pill(0xFF222536, 0x66FFFFFF, 100));
        discImage.setClipToOutline(true);
        discImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
    }

    private void applyTrackBackdrop(int index) {
        int[][] palettes = new int[][]{
                {0xFF41245F, 0xFF0D1428, 0xFF090A11},
                {0xFF183B51, 0xFF10202D, 0xFF090A11},
                {0xFF552634, 0xFF20122C, 0xFF090A11},
                {0xFF363A1D, 0xFF17201B, 0xFF090A11},
                {0xFF342A68, 0xFF17142C, 0xFF090A11}
        };
        int[] p = palettes[Math.abs(index) % palettes.length];
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, p));
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xC8080A10, 0x45101422, 0xF4080A10}));
    }

    private void likeIconText(Track t) {
        // Update the first action in the rail without rebuilding the entire control stack.
        if (sideRail.getChildCount() > 1) {
            LinearLayout likeRow = (LinearLayout) sideRail.getChildAt(1);
            if (likeRow.getChildCount() > 1) {
                TextView glyph = (TextView) likeRow.getChildAt(0);
                TextView caption = (TextView) likeRow.getChildAt(1);
                glyph.setText(t.liked ? "♥" : "♡");
                glyph.setTextColor(t.liked ? 0xFFFF5D83 : Color.WHITE);
                caption.setText(t.liked ? "נשמר" : "אהבתי");
            }
        }
    }

    private void toggleLike() {
        if (currentIndex < 0) return;
        Track t = tracks.get(currentIndex);
        t.liked = !t.liked;
        preferences.edit().putBoolean("like_" + t.id, t.liked).apply();
        likeIconText(t);
        if (favoritesOnly && !t.liked) {
            nextTrack(1);
        }
        Toast.makeText(this, t.liked ? "נוסף לשירים שאהבת ♥" : "הוסר מהאהובים", Toast.LENGTH_SHORT).show();
    }

    private void togglePlayback() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.pause();
            else player.start();
            updatePlaybackButtons();
        } catch (IllegalStateException ignored) { }
    }

    private void updatePlaybackButtons() {
        if (playButton != null) playButton.setText((player != null && safeIsPlaying()) ? "Ⅱ" : "▶");
        if (fullButton != null) fullButton.setText(fullSongMode ? "↺  חזרה לקטע בפיד" : "⤢  נגן את כל השיר");
    }

    private boolean safeIsPlaying() {
        try { return player != null && player.isPlaying(); }
        catch (IllegalStateException e) { return false; }
    }

    private void toggleFullSong() {
        if (currentIndex < 0) return;
        if (fullSongMode) playTrack(currentIndex, false);
        else playTrack(currentIndex, true);
    }

    private void jumpTo(long targetMs) {
        if (player == null || currentIndex < 0) return;
        Track track = tracks.get(currentIndex);
        long target = Math.max(0L, Math.min(targetMs, Math.max(0L, track.duration - 500L)));
        try {
            player.seekTo((int) target);
            timeCurrent.setText(formatTime(target));
            if (!fullSongMode) {
                currentStartMs = target;
                snippetEndMs = Math.min(track.duration, target + PREVIEW_MS);
            }
        } catch (IllegalStateException ignored) { }
    }

    private void saveHighlight() {
        if (player == null || currentIndex < 0) {
            Toast.makeText(this, "בחרי שיר קודם", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            long position = player.getCurrentPosition();
            Track t = tracks.get(currentIndex);
            preferences.edit().putLong("highlight_" + t.id, position).apply();
            currentStartMs = position;
            snippetEndMs = Math.min(t.duration, position + PREVIEW_MS);
            Toast.makeText(this, "הקטע נשמר לשיר הזה ✓", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException ignored) { }
    }

    private void nextTrack(int direction) {
        List<Integer> visible = visibleIndices();
        if (visible.isEmpty()) {
            if (favoritesOnly) Toast.makeText(this, "אין כרגע שירים אהובים בפיד", Toast.LENGTH_SHORT).show();
            return;
        }
        int pos = visible.indexOf(currentIndex);
        if (pos < 0) pos = direction > 0 ? -1 : 0;
        int nextPos = pos + (direction >= 0 ? 1 : -1);
        if (nextPos >= visible.size()) nextPos = 0;
        if (nextPos < 0) nextPos = visible.size() - 1;
        playTrack(visible.get(nextPos), false);
    }

    private void showLibraryDialog() {
        if (tracks.isEmpty()) {
            Toast.makeText(this, "ספריית המוזיקה עדיין ריקה", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> map = new ArrayList<>();
        for (int i : visibleIndices()) {
            Track t = tracks.get(i);
            labels.add(t.title + "\n" + t.artist + " • " + formatTime(t.duration));
            map.add(i);
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "אין שירים שמתאימים לסינון הנוכחי", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        new AlertDialog.Builder(this)
                .setTitle("השירים במכשיר")
                .setAdapter(adapter, (dialog, which) -> playTrack(map.get(which), false))
                .setNegativeButton("סגירה", null)
                .show();
    }

    private void showSearchDialog() {
        if (tracks.isEmpty()) {
            Toast.makeText(this, "קודם טעני את ספריית המוזיקה", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("שם שיר, אמן או אלבום");
        input.setText(searchQuery);
        new AlertDialog.Builder(this)
                .setTitle("חיפוש במוזיקה שלך")
                .setView(input)
                .setPositiveButton("חיפוש", (dialog, which) -> {
                    searchQuery = input.getText().toString().trim();
                    favoritesOnly = false;
                    styleNav();
                    List<Integer> matches = visibleIndices();
                    if (matches.isEmpty()) {
                        Toast.makeText(this, "לא מצאתי התאמה", Toast.LENGTH_SHORT).show();
                    } else {
                        playTrack(matches.get(0), false);
                        Toast.makeText(this, matches.size() + " תוצאות · החלקה למעבר", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("ניקוי", (dialog, which) -> {
                    searchQuery = "";
                    favoritesOnly = false;
                    styleNav();
                    if (!tracks.isEmpty()) playTrack(0, false);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private String formatTime(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        return String.format(Locale.getDefault(), "%d:%02d", total / 60L, total % 60L);
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressTick);
        handler.postDelayed(progressTick, 250);
        if (player != null) {
            try { player.setOnCompletionListener(null); player.setOnErrorListener(null); player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Keep playback alive for a short transition; Android will pause naturally if the activity is destroyed.
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressTick);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        super.onDestroy();
    }

    private static class Track {
        long id;
        long albumId;
        long duration;
        String title;
        String artist;
        String album;
        Uri uri;
        boolean liked;
    }
}
