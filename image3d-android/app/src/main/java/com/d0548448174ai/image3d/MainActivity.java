package com.d0548448174ai.image3d;

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
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 21;
    private static final int SAVE_PLY = 22;

    private Uri imageUri;
    private Bitmap originalBitmap;
    private DepthEstimator depthEstimator;
    private DepthMeshView meshView;

    private TextView selectedText;
    private TextView status;
    private ProgressBar progress;
    private Button buildButton;
    private Button saveButton;
    private Button flipButton;
    private SeekBar depthBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(247, 243, 255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(26));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(247, 243, 255));
        scroll.addView(root);

        LinearLayout hero = card(Color.rgb(238, 232, 255), 26);
        TextView badge = text("🤖  AI 3D BUILDER", 12, Color.rgb(71, 48, 130), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));
        badge.setBackground(bg(Color.rgb(220, 211, 255), 40));
        hero.addView(badge, wrap());

        TextView title = text("תמונה ➜ תלת־ממד", 29, Color.rgb(58, 39, 110), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(2));
        hero.addView(title, wrap());

        TextView sub = text(
                "מעלים תמונה • ה‑AI מנתח עומק • מקבלים משטח 3D שאפשר לסובב ולייצא",
                14, Color.rgb(91, 75, 120), false);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub, match(0, 2, 0, 0));
        root.addView(hero, match(0, 0, 0, 12));

        LinearLayout pickCard = card(Color.WHITE, 22);
        pickCard.addView(text("1  •  בחר תמונה", 18, Color.rgb(54, 44, 76), true));
        selectedText = text("עדיין לא נבחרה תמונה", 13, Color.GRAY, false);
        selectedText.setPadding(0, dp(6), 0, dp(10));
        pickCard.addView(selectedText);
        Button pick = button("🖼️  העלאת תמונה");
        pick.setOnClickListener(v -> pickImage());
        pickCard.addView(pick);
        root.addView(pickCard, match(0, 0, 0, 10));

        LinearLayout controls = card(Color.WHITE, 22);
        controls.addView(text("2  •  עומק המודל", 18, Color.rgb(54, 44, 76), true));

        depthBar = new SeekBar(this);
        depthBar.setMax(20);
        depthBar.setProgress(10);
        depthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (meshView != null) meshView.setDepthStrength(0.2f + p / 10f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        controls.addView(depthBar);

        flipButton = button("↕️  הפוך את כיוון העומק");
        flipButton.setEnabled(false);
        flipButton.setOnClickListener(v -> {
            meshView.toggleDepth();
            flipButton.setText(meshView.isFlipped() ? "↕️  עומק הפוך — לחץ שוב" : "↕️  הפוך את כיוון העומק");
        });
        controls.addView(flipButton, match(0, 4, 0, 0));
        root.addView(controls, match(0, 0, 0, 10));

        buildButton = button("✨  צור מודל 3D עם AI");
        buildButton.setEnabled(false);
        buildButton.setAlpha(.45f);
        buildButton.setTextSize(17);
        buildButton.setTextColor(Color.WHITE);
        buildButton.setBackground(bg(Color.rgb(108, 75, 232), 20));
        buildButton.setOnClickListener(v -> buildModel());
        root.addView(buildButton, match(0, 0, 0, 8));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(34), dp(34));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        status = text("", 13, Color.rgb(83, 69, 110), true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, wrap());

        meshView = new DepthMeshView(this);
        meshView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        meshView.setVisibility(View.GONE);
        root.addView(meshView, match(0, dp(8), 0, 8));

        TextView help = text(
                "גרור עם האצבע כדי לסובב את המודל. הקובץ נשמר כ‑PLY וניתן לפתוח אותו בתוכנות 3D.",
                12, Color.rgb(111, 96, 136), false);
        help.setGravity(Gravity.CENTER);
        root.addView(help, match(0, 0, 0, 6));

        saveButton = button("💾  שמור מודל 3D (.PLY)");
        saveButton.setVisibility(View.GONE);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackground(bg(Color.rgb(67, 52, 102), 18));
        saveButton.setOnClickListener(v -> saveModel());
        root.addView(saveButton, match(0, 4, 0, 0));

        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    private void buildModel() {
        if (imageUri == null) return;
        busy(true, "🤖 ה‑AI מנתח את התמונה ובונה עומק 3D…");

        executor.execute(() -> {
            try {
                Bitmap b = loadBitmap(imageUri);
                if (b == null) throw new IllegalStateException("לא ניתן לקרוא את התמונה");

                if (depthEstimator == null) {
                    depthEstimator = new DepthEstimator(getApplicationContext());
                }

                float[] normalized = DepthEstimator.normalize(depthEstimator.estimate(b));
                originalBitmap = b;

                runOnUiThread(() -> {
                    meshView.setModel(b, normalized);
                    meshView.setVisibility(View.VISIBLE);
                    flipButton.setEnabled(true);
                    saveButton.setVisibility(View.VISIBLE);
                    busy(false, "✅ מוכן! גרור על המודל כדי לראות את העומק.");
                });
            } catch (Throwable e) {
                e.printStackTrace();
                runOnUiThread(() -> busy(false, "❌ ה‑AI לא הצליח לעבד את התמונה: " +
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

    private void saveModel() {
        if (meshView == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/octet-stream");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, "image_ai_3d.ply");
        startActivityForResult(i, SAVE_PLY);
    }

    private void savePlyToUri(Uri uri) {
        busy(true, "💾 שומר את מודל ה‑3D…");
        executor.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("לא ניתן לפתוח קובץ לשמירה");
                meshView.saveAsPly(out);
                runOnUiThread(() -> busy(false, "✅ המודל נשמר בהצלחה."));
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
            imageUri = data.getData();
            selectedText.setText(imageUri.getLastPathSegment());
            buildButton.setEnabled(true);
            buildButton.setAlpha(1f);
            meshView.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            flipButton.setEnabled(false);
            status.setText("התמונה מוכנה לעיבוד.");
        } else if (requestCode == SAVE_PLY) {
            savePlyToUri(data.getData());
        }
    }

    private void busy(boolean working, String message) {
        progress.setVisibility(working ? View.VISIBLE : View.GONE);
        status.setText(message);
        buildButton.setEnabled(!working && imageUri != null);
        buildButton.setAlpha(!working && imageUri != null ? 1f : .45f);
        if (working) saveButton.setVisibility(View.GONE);
        else if (meshView.getVisibility() == View.VISIBLE) saveButton.setVisibility(View.VISIBLE);
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

    private int dp(int n) {
        return Math.round(getResources().getDisplayMetrics().density * n);
    }

    @Override
    protected void onDestroy() {
        if (depthEstimator != null) depthEstimator.close();
        if (originalBitmap != null) originalBitmap.recycle();
        executor.shutdownNow();
        super.onDestroy();
    }
}
