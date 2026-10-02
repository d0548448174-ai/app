package com.example.ballcatcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int PICK_VIDEOS = 4101;
    private static final String PREFS = "cinebox";
    private static final String KEY_URIS = "uris";
    private static final String KEY_LAST_URI = "last_uri";
    private static final String KEY_LAST_POS = "last_pos";

    private SharedPreferences prefs;
    private LinearLayout libraryRoot;
    private TextView emptyView;
    private EditText searchBox;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private ProgressBar loading;
    private final ArrayList<String> allUris = new ArrayList<>();

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int white() { return Color.WHITE; }
    private int muted() { return Color.rgb(166, 173, 194); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(9, 11, 20));
        getWindow().setNavigationBarColor(Color.rgb(9, 11, 20));
        showHome();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable strokeBg(int fill, int stroke, float widthDp, float radius) {
        GradientDrawable g = bg(fill, radius);
        g.setStroke(dp(widthDp), stroke);
        return g;
    }

    private Button primaryButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        b.setBackground(bg(Color.rgb(108, 77, 255), 18));
        b.setPadding(dp(18), 0, dp(18), 0);
        return b;
    }

    private void showHome() {
        setNormalBars();
        allUris.clear();
        allUris.addAll(loadUris());

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(bg(Color.rgb(9, 11, 20), 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 11, 20));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(32));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("CINEBOX", 23, Color.WHITE, true);
        logo.setLetterSpacing(0.08f);
        top.addView(logo, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button add = primaryButton("+ הוסף סרטונים");
        top.addView(add, new LinearLayout.LayoutParams(dp(138), dp(44)));
        add.setOnClickListener(v -> openPicker());
        content.addView(top);

        TextView offline = text("הספרייה שלך • עובדת אופליין", 13, Color.rgb(125, 132, 154), false);
        content.addView(offline, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(16), dp(18), dp(18));
        hero.setBackground(bg(Color.rgb(24, 27, 43), 24));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(178));
        hp.topMargin = dp(14);
        content.addView(hero, hp);

        TextView badge = text("המשך צפייה", 12, Color.rgb(194, 184, 255), true);
        hero.addView(badge, new LinearLayout.LayoutParams(-1, dp(25)));
        heroTitle = text("הספרייה שלך מחכה", 28, Color.WHITE, true);
        hero.addView(heroTitle, new LinearLayout.LayoutParams(-1, dp(45)));
        heroSubtitle = text("הוסף סרטונים מהמכשיר כדי לבנות ספריית צפייה אישית.", 14, Color.rgb(176, 182, 202), false);
        heroSubtitle.setMaxLines(2);
        hero.addView(heroSubtitle, new LinearLayout.LayoutParams(-1, 0, 1));
        Button continueBtn = primaryButton("▶ המשך");
        hero.addView(continueBtn, new LinearLayout.LayoutParams(dp(120), dp(44)));
        continueBtn.setOnClickListener(v -> openLastVideo());

        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(dp(12), 0, dp(12), 0);
        searchWrap.setBackground(strokeBg(Color.rgb(18, 21, 33), Color.rgb(40, 44, 64), 1, 16));
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("חיפוש בספרייה");
        searchBox.setHintTextColor(Color.rgb(104, 110, 132));
        searchBox.setTextColor(Color.WHITE);
        searchBox.setTextSize(14);
        searchBox.setBackgroundColor(Color.TRANSPARENT);
        searchWrap.addView(searchBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView mag = text("⌕", 25, Color.rgb(143, 150, 175), false);
        searchWrap.addView(mag, new LinearLayout.LayoutParams(dp(34), dp(48)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(50));
        sp.topMargin = dp(16);
        content.addView(searchWrap, sp);
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { renderLibrary(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        TextView section = text("הסרטונים שלך", 20, Color.WHITE, true);
        LinearLayout.LayoutParams sec = new LinearLayout.LayoutParams(-1, dp(48));
        sec.topMargin = dp(10);
        content.addView(section, sec);

        libraryRoot = new LinearLayout(this);
        libraryRoot.setOrientation(LinearLayout.VERTICAL);
        content.addView(libraryRoot);

        emptyView = text("אין עדיין סרטונים.\nלחץ על ״הוסף סרטונים״ ובחר קבצי וידיאו מהמכשיר.", 15, Color.rgb(155, 162, 183), false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(20), dp(30), dp(20), dp(30));
        content.addView(emptyView, new LinearLayout.LayoutParams(-1, dp(170)));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        content.addView(loading, new LinearLayout.LayoutParams(-1, dp(46)));

        scroll.addView(content);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(outer);

        renderHero();
        renderLibrary("");
    }

    private void renderHero() {
        String last = prefs.getString(KEY_LAST_URI, "");
        if (last == null || last.isEmpty()) {
            heroTitle.setText("הספרייה שלך מחכה");
            heroSubtitle.setText("הוסף סרטונים מהמכשיר כדי לבנות ספריית צפייה אישית.");
            return;
        }
        String name = getDisplayName(Uri.parse(last));
        heroTitle.setText(name);
        long pos = prefs.getLong(KEY_LAST_POS, 0);
        heroSubtitle.setText(pos > 1000 ? "המשך מהמקום שבו עצרת • " + formatTime(pos) : "מוכן לצפייה");
    }

    private void renderLibrary(String query) {
        if (libraryRoot == null) return;
        libraryRoot.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        ArrayList<String> filtered = new ArrayList<>();
        for (String s : allUris) {
            String n = getDisplayName(Uri.parse(s));
            if (q.isEmpty() || n.toLowerCase(Locale.getDefault()).contains(q)) filtered.add(s);
        }

        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (filtered.isEmpty()) return;

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);

        for (String uriString : filtered) {
            View card = createCard(uriString);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0;
            gp.height = dp(230);
            gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gp.setMargins(dp(4), dp(4), dp(4), dp(10));
            grid.addView(card, gp);
        }
        libraryRoot.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }

    private View createCard(String uriString) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(9), dp(9), dp(8));
        card.setBackground(bg(Color.rgb(18, 21, 33), 18));

        FrameLayout thumb = new FrameLayout(this);
        thumb.setBackground(bg(Color.rgb(28, 32, 48), 15));

        TextView play = text("▶", 34, Color.WHITE, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(bg(0x553B356F, 60));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.CENTER);
        thumb.addView(play, pp);

        TextView videoBadge = text("VIDEO", 9, Color.WHITE, true);
        videoBadge.setGravity(Gravity.CENTER);
        videoBadge.setBackground(bg(0xAA6C4DFF, 8));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(54), dp(23), Gravity.TOP | Gravity.START);
        bp.setMargins(dp(8), dp(8), 0, 0);
        thumb.addView(videoBadge, bp);

        ImageButton more = new ImageButton(this);
        more.setImageResource(android.R.drawable.ic_menu_more);
        more.setColorFilter(Color.WHITE);
        more.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP | Gravity.END);
        thumb.addView(more, mp);
        more.setOnClickListener(v -> removeUri(uriString));

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(142));
        card.addView(thumb, tp);

        TextView name = text(getDisplayName(Uri.parse(uriString)), 14, Color.WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(48));
        np.topMargin = dp(6);
        card.addView(name, np);

        TextView meta = text(getDuration(Uri.parse(uriString)), 11, Color.rgb(129, 136, 157), false);
        card.addView(meta, new LinearLayout.LayoutParams(-1, dp(22)));

        card.setOnClickListener(v -> playVideo(Uri.parse(uriString)));
        more.setOnClickListener(v -> removeUri(uriString));
        return card;
    }

    private void removeUri(String uriString) {
        allUris.remove(uriString);
        saveUris(allUris);
        if (uriString.equals(prefs.getString(KEY_LAST_URI, ""))) {
            prefs.edit().remove(KEY_LAST_URI).remove(KEY_LAST_POS).apply();
        }
        renderHero();
        renderLibrary(searchBox == null ? "" : searchBox.getText().toString());
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_VIDEOS);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != PICK_VIDEOS || result != RESULT_OK || data == null) return;

        HashSet<String> set = new HashSet<>(allUris);
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                addPickedUri(data.getClipData().getItemAt(i).getUri(), set);
            }
        } else if (data.getData() != null) {
            addPickedUri(data.getData(), set);
        }
        allUris.clear();
        allUris.addAll(set);
        saveUris(allUris);
        renderHero();
        renderLibrary(searchBox == null ? "" : searchBox.getText().toString());
    }

    private void addPickedUri(Uri uri, Set<String> set) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        set.add(uri.toString());
    }

    private ArrayList<String> loadUris() {
        ArrayList<String> list = new ArrayList<>();
        String raw = prefs.getString(KEY_URIS, "");
        if (raw == null || raw.isEmpty()) return list;
        String[] parts = raw.split("\n");
        for (String s : parts) if (!s.trim().isEmpty()) list.add(s.trim());
        return list;
    }

    private void saveUris(ArrayList<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) b.append(s).append("\n");
        prefs.edit().putString(KEY_URIS, b.toString()).apply();
    }

    private String getDisplayName(Uri uri) {
        try {
            ContentResolver cr = getContentResolver();
            Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    String n = c.getString(0);
                    c.close();
                    return n == null ? "סרטון" : n;
                }
                c.close();
            }
        } catch (Exception ignored) {}
        String s = uri.getLastPathSegment();
        return s == null ? "סרטון" : s;
    }

    private String getDuration(Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) return formatTime(Long.parseLong(d));
        } catch (Exception ignored) {} finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return "וידיאו";
    }

    private String formatTime(long ms) {
        long total = Math.max(0, ms / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private void openLastVideo() {
        String s = prefs.getString(KEY_LAST_URI, "");
        if (s != null && !s.isEmpty()) {
            playVideo(Uri.parse(s));
        } else if (!allUris.isEmpty()) {
            playVideo(Uri.parse(allUris.get(0)));
        } else {
            openPicker();
        }
    }

    private void playVideo(Uri uri) {
        setFullscreenBars();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        VideoView vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), 0, dp(12), 0);
        top.setBackgroundColor(0xAA070910);

        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setColorFilter(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(54)));

        TextView title = text(getDisplayName(uri), 15, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));

        TextView offline = text("אופליין", 11, Color.rgb(187, 179, 255), true);
        top.addView(offline, new LinearLayout.LayoutParams(dp(70), dp(54)));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP);
        root.addView(top, topParams);

        back.setOnClickListener(v -> {
            long pos = vv.getCurrentPosition();
            prefs.edit().putString(KEY_LAST_URI, uri.toString()).putLong(KEY_LAST_POS, pos).apply();
            showHome();
        });

        vv.setVideoURI(uri);
        vv.setOnPreparedListener(mp -> {
            long pos = prefs.getString(KEY_LAST_URI, "").equals(uri.toString())
                    ? prefs.getLong(KEY_LAST_POS, 0) : 0;
            if (pos > 5000 && pos < mp.getDuration() - 5000) vv.seekTo((int) pos);
            vv.start();
        });
        vv.setOnCompletionListener(mp -> prefs.edit().putString(KEY_LAST_URI, uri.toString()).putLong(KEY_LAST_POS, 0).apply());

        setContentView(root);
    }

    private void setNormalBars() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(Color.rgb(9, 11, 20));
        getWindow().setNavigationBarColor(Color.rgb(9, 11, 20));
    }

    private void setFullscreenBars() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onBackPressed() {
        showHome();
    }
}
