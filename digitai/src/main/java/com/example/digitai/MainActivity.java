package com.example.digitai;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private DigitPad pad;
    private TextView answer;
    private TextView info;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 20, 18, 18);
        root.setBackgroundColor(Color.rgb(247,244,255));

        TextView title = new TextView(this);
        title.setText("🤖 AI ספרות");
        title.setTextSize(30);
        title.setGravity(17);
        title.setTypeface(null, 1);
        title.setTextColor(Color.rgb(42,33,56));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("צייר ספרה — והבינה המלאכותית תנחש אותה");
        sub.setTextSize(16);
        sub.setGravity(17);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin = 6;
        root.addView(sub, sp);

        pad = new DigitPad();
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,0,1f);
        pp.topMargin = 14;
        pp.bottomMargin = 10;
        root.addView(pad, pp);

        answer = new TextView(this);
        answer.setText("✏️ צייר ספרה");
        answer.setTextSize(25);
        answer.setGravity(17);
        answer.setTypeface(null, 1);
        answer.setTextColor(Color.rgb(92,65,155));
        root.addView(answer);

        info = new TextView(this);
        info.setText("המודל מובנה בתוך האפליקציה • אופליין");
        info.setTextSize(13);
        info.setGravity(17);
        root.addView(info);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button test = new Button(this);
        test.setText("🤖 זהה");
        Button clear = new Button(this);
        clear.setText("🧹 נקה");
        buttons.addView(test, new LinearLayout.LayoutParams(0,-2,1f));
        buttons.addView(clear, new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1,-2);
        bp.topMargin = 8;
        root.addView(buttons, bp);

        test.setOnClickListener(v -> {
            float[] features = pad.toFeatures();
            if (features == null) {
                answer.setText("✏️ קודם צייר ספרה");
                return;
            }
            int digit = DigitModel.predict(features);
            answer.setText("🎯 נראה לי שזה: " + digit);
        });

        clear.setOnClickListener(v -> {
            pad.clearPad();
            answer.setText("✏️ צייר ספרה");
        });

        setContentView(root);
    }

    private class DigitPad extends View {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private Bitmap bitmap;

        DigitPad() {
            super(MainActivity.this);
            ink.setColor(Color.WHITE);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(32);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
            frame.setColor(Color.rgb(210,202,230));
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(4);
            setBackgroundColor(Color.BLACK);
        }

        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            bitmap = Bitmap.createBitmap(Math.max(1,w),Math.max(1,h),Bitmap.Config.ARGB_8888);
            new Canvas(bitmap).drawColor(Color.BLACK);
        }

        @Override protected void onDraw(Canvas c) {
            if (bitmap != null) c.drawBitmap(bitmap,0,0,null);
            c.drawRect(2,2,getWidth()-2,getHeight()-2,frame);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            switch(e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.reset();
                    path.moveTo(x,y);
                    Canvas c1 = new Canvas(bitmap);
                    c1.drawPoint(x,y,ink);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    path.lineTo(x,y);
                    Canvas c2 = new Canvas(bitmap);
                    c2.drawPath(path,ink);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    path.lineTo(x,y);
                    Canvas c3 = new Canvas(bitmap);
                    c3.drawPath(path,ink);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        void clearPad() {
            if (bitmap != null) {
                new Canvas(bitmap).drawColor(Color.BLACK);
                invalidate();
            }
        }

        float[] toFeatures() {
            if (bitmap == null) return null;
            int w=bitmap.getWidth(), h=bitmap.getHeight();
            int[] px=new int[w*h];
            bitmap.getPixels(px,0,w,0,0,w,h);

            int minX=w,minY=h,maxX=-1,maxY=-1;
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                if(Color.red(px[y*w+x]) > 12) {
                    if(x<minX) minX=x;
                    if(x>maxX) maxX=x;
                    if(y<minY) minY=y;
                    if(y>maxY) maxY=y;
                }
            }
            if(maxX<minX || maxY<minY) return null;

            int bw=maxX-minX+1, bh=maxY-minY+1;
            int side=Math.max(bw,bh);
            int left=Math.max(0,minX-(side-bw)/2);
            int top=Math.max(0,minY-(side-bh)/2);
            int right=Math.min(w,left+side);
            int bottom=Math.min(h,top+side);

            Bitmap crop=Bitmap.createBitmap(bitmap,left,top,Math.max(1,right-left),Math.max(1,bottom-top));
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
            int k=0;
            for(int y=0;y<8;y++) for(int x=0;x<8;x++)
                out[k++]=Color.red(small.getPixel(x,y))/255f;

            crop.recycle();
            small.recycle();
            return out;
        }
    }
}
