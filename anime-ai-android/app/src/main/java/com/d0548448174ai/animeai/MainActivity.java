package com.d0548448174ai.animeai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 10;
    private static final int SAVE_IMAGE = 11;

    private Uri sourceUri;
    private Bitmap sourceBitmap;
    private Bitmap animeBitmap;
    private AnimeGan animeGan;

    private TextView fileLabel;
    private TextView status;
    private ImageView beforeView;
    private ImageView afterView;
    private Button makeButton;
    private Button saveButton;
    private ProgressBar progress;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(247, 244, 255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(26));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(247, 244, 255));
        scroll.addView(root);

        LinearLayout hero = card(Color.rgb(238, 233, 255), 26);

        TextView badge = text("✨  AI ANIME", 12, Color.rgb(71, 51, 135), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));
        badge.setBackground(bg(Color.rgb(220, 212, 255), 40));
        hero.addView(badge, wrap());

        TextView title = text("תמונה ➜ אנימה", 30, Color.rgb(58, 43, 112), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(2));
        hero.addView(title, wrap());

        TextView subtitle = text(
                "מעלים תמונה • ה‑AI מעבד אותה במכשיר • מקבלים גרסת אנימה לשמירה",
                14, Color.rgb(93, 78, 125), false);
        subtitle.setGravity(Gravity.CENTER);
        hero.addView(subtitle, match(0, 2, 0, 0));

        root.addView(hero, match(0, 0, 0, 12));

        LinearLayout pickCard = card(Color.WHITE, 22);
        pickCard.addView(text("1  •  בחר תמונה", 18, Color.rgb(54, 44, 76), true));

        fileLabel = text("עדיין לא נבחרה תמונה", 13, Color.GRAY, false);
        fileLabel.setPadding(0, dp(6), 0, dp(10));
        pickCard.addView(fileLabel);

        Button pickButton = button("🖼️  העלאת תמונה");
        pickButton.setOnClickListener(v -> pickImage());
        pickCard.addView(pickButton);

        root.addView(pickCard, match(0, 0, 0, 10));

        LinearLayout beforeCard = card(Color.WHITE, 22);
        beforeCard.addView(text("2  •  התמונה המקורית", 17, Color.rgb(54, 44, 76), true));
        beforeView = preview();
        beforeView.setVisibility(View.GONE);
        beforeCard.addView(beforeView, imageParams(dp(270)));
        root.addView(beforeCard, match(0, 0, 0, 10));

        makeButton = button("✨  הפוך לאנימה");
        makeButton.setEnabled(false);
        makeButton.setAlpha(.45f);
        makeButton.setTextSize(17);
        makeButton.setTextColor(Color.WHITE);
        makeButton.setBackground(bg(Color.rgb(107, 78, 255), 20));
        makeButton.setOnClickListener(v -> convert());
        root.addView(makeButton, match(0, 0, 0, 8));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(34), dp(34));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        status = text("", 13, Color.rgb(79, 66, 105), true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, wrap());

        LinearLayout afterCard = card(Color.rgb(249, 247, 255), 22);
        afterCard.addView(text("3  •  תוצאת האנימה", 18, Color.rgb(54, 44, 76), true));
        afterCard.addView(text(
                "העיבוד מתבצע מקומית במכשיר — אין צורך להעלות את התמונה לאתר",
                12, Color.rgb(119, 103, 145), false));

        afterView = preview();
        afterView.setVisibility(View.GONE);
        afterCard.addView(afterView, imageParams(dp(320)));

        saveButton = button("💾  שמור את התמונה");
        saveButton.setVisibility(View.GONE);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackground(bg(Color.rgb(65, 52, 100), 18));
        saveButton.setOnClickListener(v -> saveResult());
        afterCard.addView(saveButton, match(0, dp(8), 0, 0));

        root.addView(afterCard, match(0, dp(4), 0, 0));

        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    private void convert() {
        if (sourceUri == null) return;

        busy(true, "🤖 ה‑AI מצייר את התמונה מחדש בסגנון אנימה…");

        executor.execute(() -> {
            try {
                Bitmap b = loadBitmap(sourceUri);
                if (b == null) throw new IllegalStateException("לא ניתן לקרוא את התמונה");

                if (animeGan == null) {
                    animeGan = new AnimeGan(getApplicationContext());
                }

                Bitmap result = animeGan.stylize(b);
                sourceBitmap = b;
                animeBitmap = result;

                runOnUiThread(() -> {
                    afterView.setImageBitmap(animeBitmap);
                    afterView.setVisibility(View.VISIBLE);
                    saveButton.setVisibility(View.VISIBLE);
                    busy(false, "✅ מוכן! זו גרסת האנימה של התמונה.");
                });
            } catch (Throwable e) {
                e.printStackTrace();
                runOnUiThread(() -> busy(false,
                        "❌ העיבוד נכשל: " +
                        (e.getMessage() == null ? "שגיאה לא ידועה" : e.getMessage())));
            }
        });
    }

    private Bitmap loadBitmap(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private void saveResult() {
        if (animeBitmap == null) return;

        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("image/png");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, "anime_image.png");
        startActivityForResult(i, SAVE_IMAGE);
    }

    private void writeResult(Uri uri) {
        busy(true, "💾 שומר את התוצאה…");
        executor.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("לא ניתן לפתוח קובץ");
                if (!animeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IllegalStateException("שמירה נכשלה");
                }
                runOnUiThread(() -> busy(false, "✅ התמונה נשמרה בהצלחה."));
            } catch (Throwable e) {
                runOnUiThread(() -> busy(false, "❌ השמירה נכשלה."));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == PICK_IMAGE) {
            sourceUri = data.getData();
            fileLabel.setText(sourceUri.getLastPathSegment());
            try {
                sourceBitmap = loadBitmap(sourceUri);
                beforeView.setImageBitmap(sourceBitmap);
                beforeView.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                status.setText("❌ לא ניתן לטעון את התמונה.");
            }
            afterBitmapReset();
            makeButton.setEnabled(true);
            makeButton.setAlpha(1f);
            status.setText("התמונה מוכנה. לחץ על "הפוך לאנימה".");
        } else if (requestCode == SAVE_IMAGE) {
            writeResult(data.getData());
        }
    }

    private void afterBitmapReset() {
        if (animeBitmap != null) {
            try { animeBitmap.recycle(); } catch (Exception ignored) {}
            animeBitmap = null;
        }
        afterView.setImageDrawable(null);
        afterView.setVisibility(View.GONE);
        saveButton.setVisibility(View.GONE);
    }

    private void busy(boolean working, String message) {
        progress.setVisibility(working ? View.VISIBLE : View.GONE);
        status.setText(message);
        makeButton.setEnabled(!working && sourceUri != null);
        makeButton.setAlpha(!working && sourceUri != null ? 1f : .45f);
        if (working) saveButton.setVisibility(View.GONE);
        else if (animeBitmap != null) saveButton.setVisibility(View.VISIBLE);
    }

    private ImageView preview() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setAdjustViewBounds(true);
        v.setBackground(bg(Color.rgb(245, 242, 250), 18));
        return v;
    }

    private LinearLayout.LayoutParams imageParams(int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.setMargins(0, dp(8), 0, 0);
        return p;
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

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(58, 45, 82));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(bg(Color.WHITE, 18));
        return b;
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15), dp(15), dp(15), dp(15));
        l.setBackground(bg(color, radius));
        return l;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.argb(30, 80, 65, 120));
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

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        if (animeGan != null) animeGan.close();
        if (sourceBitmap != null) {
            try { sourceBitmap.recycle(); } catch (Exception ignored) {}
        }
        if (animeBitmap != null) {
            try { animeBitmap.recycle(); } catch (Exception ignored) {}
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
