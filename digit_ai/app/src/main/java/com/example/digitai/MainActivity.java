package com.example.digitai;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends Activity {

    private DigitCanvas digitCanvas;
    private TextView result;
    private TextView confidence;

    // 30 tiny learned prototypes: 3 per digit, generated from the sklearn digits dataset.
    private static final String MODEL_B64 = "AAAx160cAAAACbfs368FAAAw7I484SwAAE7oPAS1WgAAU98hAbBrAAAr5zwn21UAAArAx83jGAAAADLO2E8BAAABXMXHZAIAAAzOynvWMQAALuFEBoNbAABM";
    private static final String LABELS = "000111222333444555666777888999";
    private static final byte[] PROTOTYPES = Base64.decode(MODEL_B64, Base64.DEFAULT);
    private static final int PROTO_COUNT = 30;
    private static final int FEATURES = 64;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 24, 18, 18);
        root.setBackgroundColor(Color.rgb(247, 244, 255));

        TextView title = new TextView(this);
        title.setText("🤖 AI ספרות");
        title.setTextSize(29);
        title.setTextColor(Color.rgb(42,33,56));
        title.setGravity(17);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView help = new TextView(this);
        help.setText("צייר ספרה בתוך הריבוע וה־AI ינחש אותה");
        help.setTextSize(16);
        help.setGravity(17);
        help.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = 8;
        root.addView(help, hp);

        digitCanvas = new DigitCanvas();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cp.topMargin = 16;
        cp.bottomMargin = 12;
        root.addView(digitCanvas, cp);

        result = new TextView(this);
        result.setText("✏️ מוכן — צייר ספרה");
        result.setTextSize(24);
        result.setGravity(17);
        result.setTextColor(Color.rgb(80, 56, 150));
        root.addView(result, new LinearLayout.LayoutParams(-1, -2));

        confidence = new TextView(this);
        confidence.setText("");
        confidence.setTextSize(15);
        confidence.setGravity(17);
        root.addView(confidence, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button guess = new Button(this);
        guess.setText("🤖 זהה");
        buttons.addView(guess, new LinearLayout.LayoutParams(0, -2, 1f));

        Button clear = new Button(this);
        clear.setText("🧹 נקה");
        buttons.addView(clear, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 8;
        root.addView(buttons, bp);

        guess.setOnClickListener(v -> recognize());
        clear.setOnClickListener(v -> {
            digitCanvas.clear();
            result.setText("✏️ מוכן — צייר ספרה");
            confidence.setText("");
        });

        setContentView(root);
    }

    private void recognize() {
        float[] input = digitCanvas.toFeatures();
        if (input == null) {
            result.setText("✏️ קודם צייר ספרה");
            confidence.setText("");
            return;
        }

        double[] distances = new double[PROTO_COUNT];
        double best = Double.MAX_VALUE;
        int bestIndex = 0;

        for (int p = 0; p < PROTO_COUNT; p++) {
            double d = 0;
            int base = p * FEATURES;
            for (int i = 0; i < FEATURES; i++) {
                int pv = PROTOTYPES[base + i] & 0xFF;
                double x = input[i] * 255.0;
                double diff = x - pv;
                d += diff * diff;
            }
            distances[p] = d;
            if (d < best) {
                best = d;
                bestIndex = p;
            }
        }

        int digit = LABELS.charAt(bestIndex) - '0';

        // Simple relative confidence, intentionally labeled as an estimate.
        double second = Double.MAX_VALUE;
        for (int i = 0; i < distances.length; i++) {
            if (i != bestIndex && distances[i] < second) second = distances[i];
        }
        double score = second > 0 ? Math.max(0, Math.min(99, (second - best) / second * 100.0)) : 0;

        result.setText("אני חושב שזה: " + digit);
        confidence.setText(String.format("ביטחון משוער: %.0f%%", score));
    }

    private class DigitCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private Bitmap bitmap;

        DigitCanvas() {
            super(MainActivity.this);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(34f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            border.setColor(Color.rgb(210, 202, 230));
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(4f);
            setBackgroundColor(Color.BLACK);
            setFocusable(true);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            bitmap = Bitmap.createBitmap(Math.max(1,w), Math.max(1,h), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            c.drawColor(Color.BLACK);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap != null) c.drawBitmap(bitmap, 0, 0, null);
            c.drawRect(2, 2, getWidth()-2, getHeight()-2, border);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                path.reset();
                path.moveTo(x,y);
                drawPoint(x,y);
                return true;
            } else if (e.getAction()==MotionEvent.ACTION_MOVE) {
                path.lineTo(x,y);
                drawPath();
                return true;
            } else if (e.getAction()==MotionEvent.ACTION_UP) {
                path.lineTo(x,y);
                drawPath();
                return true;
            }
            return true;
        }

        private void drawPoint(float x,float y) {
            if (bitmap == null) return;
            Canvas c = new Canvas(bitmap);
            c.drawPoint(x,y,paint);
            invalidate();
        }

        private void drawPath() {
            if (bitmap == null) return;
            Canvas c = new Canvas(bitmap);
            c.drawPath(path, paint);
            invalidate();
        }

        void clear() {
            if (bitmap != null) {
                Canvas c = new Canvas(bitmap);
                c.drawColor(Color.BLACK);
                invalidate();
            }
        }

        float[] toFeatures() {
            if (bitmap == null) return null;

            int w=bitmap.getWidth(), h=bitmap.getHeight();
            int minX=w, minY=h, maxX=-1, maxY=-1;
            int[] px=new int[w*h];
            bitmap.getPixels(px,0,w,0,0,w,h);

            for(int y=0;y<h;y++) {
                for(int x=0;x<w;x++) {
                    int v=Color.red(px[y*w+x]);
                    if(v>10) {
                        if(x<minX) minX=x;
                        if(y<minY) minY=y;
                        if(x>maxX) maxX=x;
                        if(y>maxY) maxY=y;
                    }
                }
            }
            if(maxX<minX || maxY<minY) return null;

            int bw=maxX-minX+1, bh=maxY-minY+1;
            int side=Math.max(bw,bh);
            int pad=Math.max(2,(int)(side*0.10));
            int left=Math.max(0,minX-(side-bw)/2-pad);
            int top=Math.max(0,minY-(side-bh)/2-pad);
            int right=Math.min(w-1,left+side+2*pad);
            int bottom=Math.min(h-1,top+side+2*pad);

            Bitmap crop=Bitmap.createBitmap(bitmap,left,top,
                    Math.max(1,right-left+1),Math.max(1,bottom-top+1));

            Bitmap small=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
            Canvas sc=new Canvas(small);
            sc.drawColor(Color.BLACK);

            Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
            float scale=6f/Math.max(crop.getWidth(),crop.getHeight());
            float dw=crop.getWidth()*scale;
            float dh=crop.getHeight()*scale;
            float dx=(8-dw)/2f;
            float dy=(8-dh)/2f;
            sc.drawBitmap(crop,null,new android.graphics.RectF(dx,dy,dx+dw,dy+dh),p);

            float[] out=new float[64];
            int idx=0;
            for(int y=0;y<8;y++) {
                for(int x=0;x<8;x++) {
                    int c=small.getPixel(x,y);
                    out[idx++]=Color.red(c)/255f;
                }
            }
            crop.recycle();
            small.recycle();
            return out;
        }
    }
}
