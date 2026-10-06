package com.d0548448174ai.image3d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;

import java.io.OutputStream;
import java.util.Locale;

public final class DepthMeshView extends View {
    private static final int GRID_W = 96;
    private static final int GRID_H = 72;

    private Bitmap textureBitmap;
    private float[] depth;
    private boolean flipDepth = false;
    private float depthStrength = 0.9f;

    private float yaw = -12f;
    private float pitch = 8f;
    private float lastX;
    private float lastY;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shadowPath = new Path();

    private float[] projected;
    private float[] shadowProjected;

    public DepthMeshView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackground(new ColorDrawable(Color.rgb(244, 240, 255)));
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setDither(true);

        shadowPaint.setColor(Color.argb(48, 35, 26, 70));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        borderPaint.setColor(Color.argb(90, 108, 75, 232));

        labelPaint.setColor(Color.rgb(82, 67, 115));
        labelPaint.setTextSize(dp(14));
        labelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        setFocusable(true);
        setClickable(true);
    }

    public void setModel(Bitmap source, float[] normalizedDepth) {
        int maxSide = 720;
        float scale = Math.min(1f,
                maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(2, Math.round(source.getWidth() * scale));
        int h = Math.max(2, Math.round(source.getHeight() * scale));

        Bitmap scaled = Bitmap.createScaledBitmap(source, w, h, true);
        Bitmap old = textureBitmap;
        textureBitmap = scaled;
        if (old != null && old != scaled) {
            try { old.recycle(); } catch (Exception ignored) {}
        }

        depth = normalizedDepth;
        invalidate();
    }

    public void setDepthStrength(float strength) {
        depthStrength = strength;
        invalidate();
    }

    public void toggleDepth() {
        flipDepth = !flipDepth;
        invalidate();
    }

    public boolean isFlipped() {
        return flipDepth;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.rgb(244, 240, 255));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("גרור את התמונה כדי לסובב את מודל ה‑3D",
                getWidth() / 2f, dp(24), labelPaint);

        if (textureBitmap == null || depth == null) {
            return;
        }

        ensureArrays();

        float aspect = textureBitmap.getWidth() / (float) textureBitmap.getHeight();
        float scale = Math.min(
                (getWidth() - dp(34)) / (aspect * 2.4f),
                (getHeight() - dp(70)) / 2.4f);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f + dp(12);

        projectMesh(aspect, scale, cx, cy, projected, 0f);

        shadowPath.reset();
        float[] corners = {
                projected[0], projected[1],
                projected[(GRID_W - 1) * 2], projected[(GRID_W - 1) * 2 + 1],
                projected[((GRID_H - 1) * GRID_W + (GRID_W - 1)) * 2],
                projected[((GRID_H - 1) * GRID_W + (GRID_W - 1)) * 2 + 1],
                projected[((GRID_H - 1) * GRID_W) * 2],
                projected[((GRID_H - 1) * GRID_W) * 2 + 1]
        };
        shadowPath.moveTo(corners[0] + dp(8), corners[1] + dp(12));
        shadowPath.lineTo(corners[2] + dp(8), corners[3] + dp(12));
        shadowPath.lineTo(corners[4] + dp(8), corners[5] + dp(12));
        shadowPath.lineTo(corners[6] + dp(8), corners[7] + dp(12));
        shadowPath.close();
        canvas.drawPath(shadowPath, shadowPaint);

        canvas.drawBitmapMesh(
                textureBitmap,
                GRID_W - 1,
                GRID_H - 1,
                projected,
                0,
                null,
                0,
                bitmapPaint);

        Path frame = new Path();
        frame.moveTo(projected[0], projected[1]);
        frame.lineTo(projected[(GRID_W - 1) * 2], projected[(GRID_W - 1) * 2 + 1]);
        int lastRow = (GRID_H - 1) * GRID_W;
        frame.lineTo(projected[(lastRow + GRID_W - 1) * 2],
                projected[(lastRow + GRID_W - 1) * 2 + 1]);
        frame.lineTo(projected[lastRow * 2], projected[lastRow * 2 + 1]);
        frame.close();
        canvas.drawPath(frame, borderPaint);
    }

    private void ensureArrays() {
        int count = GRID_W * GRID_H * 2;
        if (projected == null || projected.length != count) {
            projected = new float[count];
        }
        if (shadowProjected == null || shadowProjected.length != count) {
            shadowProjected = new float[count];
        }
    }

    private void projectMesh(float aspect, float scale, float cx, float cy,
                             float[] out, float zOffset) {
        final float camera = 5.0f;
        final double yawRad = Math.toRadians(yaw);
        final double pitchRad = Math.toRadians(pitch);

        float cosY = (float) Math.cos(yawRad);
        float sinY = (float) Math.sin(yawRad);
        float cosX = (float) Math.cos(pitchRad);
        float sinX = (float) Math.sin(pitchRad);

        int k = 0;
        for (int gy = 0; gy < GRID_H; gy++) {
            float v = gy / (float) (GRID_H - 1);
            for (int gx = 0; gx < GRID_W; gx++) {
                float u = gx / (float) (GRID_W - 1);

                float d = sampleDepth(u, v);
                if (flipDepth) d = 1f - d;

                float x = (u - 0.5f) * aspect * 2f;
                float y = (0.5f - v) * 2f;
                float z = (d - 0.5f) * depthStrength + zOffset;

                float rx = x * cosY + z * sinY;
                float rz = -x * sinY + z * cosY;

                float ry = y * cosX - rz * sinX;
                float rz2 = y * sinX + rz * cosX;

                float perspective = camera / Math.max(0.8f, camera - rz2);
                out[k++] = cx + rx * scale * perspective;
                out[k++] = cy - ry * scale * perspective;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                yaw += dx * 0.55f;
                pitch += dy * 0.35f;
                pitch = Math.max(-70f, Math.min(70f, pitch));
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                performClick();
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public void saveAsPly(OutputStream out) throws Exception {
        if (textureBitmap == null || depth == null) {
            throw new IllegalStateException("אין מודל מוכן");
        }

        int vertexCount = GRID_W * GRID_H;
        int faceCount = (GRID_W - 1) * (GRID_H - 1) * 2;
        StringBuilder header = new StringBuilder();
        header.append("ply\n");
        header.append("format ascii 1.0\n");
        header.append("comment AI depth mesh generated from a single image\n");
        header.append("element vertex ").append(vertexCount).append("\n");
        header.append("property float x\nproperty float y\nproperty float z\n");
        header.append("property uchar red\nproperty uchar green\nproperty uchar blue\n");
        header.append("element face ").append(faceCount).append("\n");
        header.append("property list uchar int vertex_indices\n");
        header.append("end_header\n");
        out.write(header.toString().getBytes("UTF-8"));

        float aspect = textureBitmap.getWidth() / (float) textureBitmap.getHeight();

        for (int gy = 0; gy < GRID_H; gy++) {
            for (int gx = 0; gx < GRID_W; gx++) {
                float u = gx / (float) (GRID_W - 1);
                float v = gy / (float) (GRID_H - 1);
                float d = sampleDepth(u, v);
                if (flipDepth) d = 1f - d;

                float x = (u - 0.5f) * 2f * aspect;
                float y = (0.5f - v) * 2f;
                float z = (d - 0.5f) * depthStrength;

                int px = Math.min(textureBitmap.getWidth() - 1,
                        Math.max(0, Math.round(u * (textureBitmap.getWidth() - 1))));
                int py = Math.min(textureBitmap.getHeight() - 1,
                        Math.max(0, Math.round(v * (textureBitmap.getHeight() - 1))));
                int color = textureBitmap.getPixel(px, py);

                String line = String.format(Locale.US,
                        "%.5f %.5f %.5f %d %d %d\n",
                        x, y, z, Color.red(color), Color.green(color), Color.blue(color));
                out.write(line.getBytes("UTF-8"));
            }
        }

        for (int gy = 0; gy < GRID_H - 1; gy++) {
            for (int gx = 0; gx < GRID_W - 1; gx++) {
                int a = gy * GRID_W + gx;
                int b = a + 1;
                int c = a + GRID_W;
                int d = c + 1;
                out.write((String.format(Locale.US, "3 %d %d %d\n",
                        a, c, b)).getBytes("UTF-8"));
                out.write((String.format(Locale.US, "3 %d %d %d\n",
                        b, c, d)).getBytes("UTF-8"));
            }
        }
        out.flush();
    }

    private float sampleDepth(float u, float v) {
        if (depth == null || depth.length < 686 * 518) {
            return 0.5f;
        }

        float fx = u * 685f;
        float fy = v * 517f;

        int x0 = Math.max(0, Math.min(685, (int) Math.floor(fx)));
        int y0 = Math.max(0, Math.min(517, (int) Math.floor(fy)));
        int x1 = Math.min(685, x0 + 1);
        int y1 = Math.min(517, y0 + 1);

        float ax = fx - x0;
        float ay = fy - y0;

        float d00 = depth[y0 * 686 + x0];
        float d10 = depth[y0 * 686 + x1];
        float d01 = depth[y1 * 686 + x0];
        float d11 = depth[y1 * 686 + x1];

        float top = d00 + (d10 - d00) * ax;
        float bottom = d01 + (d11 - d01) * ax;
        return top + (bottom - top) * ay;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
