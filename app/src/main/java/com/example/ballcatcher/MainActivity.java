package com.example.ballcatcher;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.content.*;
import java.util.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(16,20,38));
        setContentView(new GameView(this));
    }

    static class GameView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random();
        ArrayList<Ball> balls = new ArrayList<>();
        float catcherX, catcherY;
        int score = 0, best = 0, lives = 3, level = 1;
        long start, lastSpawn, lastFrame;
        boolean playing = true, gameOver = false;
        int w, h;
        RectF catcher = new RectF();

        GameView(Context c) {
            super(c);
            p.setTypeface(Typeface.create("sans", Typeface.BOLD));
            setFocusable(true);
            start = System.currentTimeMillis();
            lastFrame = start;
        }

        void reset() {
            balls.clear(); score = 0; lives = 3; level = 1;
            playing = true; gameOver = false;
            start = lastSpawn = lastFrame = System.currentTimeMillis();
            invalidate();
        }

        @Override protected void onSizeChanged(int ww, int hh, int ow, int oh) {
            w = ww; h = hh;
            catcherX = w / 2f; catcherY = h - 120;
        }

        void spawn() {
            float r = 24 + random.nextInt(12);
            float x = r + random.nextFloat() * (w - 2*r);
            float speed = 260 + level * 35 + random.nextFloat() * 140;
            int[] colors = {0xFF7C5CFC,0xFF00D4FF,0xFFFFC857,0xFFFF5D8F,0xFF55EFC4};
            balls.add(new Ball(x, 115, r, speed, colors[random.nextInt(colors.length)]));
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            long now = System.currentTimeMillis();
            float dt = Math.min(0.04f, (now-lastFrame)/1000f);
            lastFrame = now;

            p.setShader(new LinearGradient(0,0,0,h,0xFF101426,0xFF20294A,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);

            // subtle stars
            p.setColor(0x22FFFFFF);
            for (int i=0;i<18;i++) c.drawCircle((i*83)%w, 100+(i*47)%Math.max(1,h-180), 2, p);

            drawHeader(c);
            if (playing && !gameOver) update(dt);

            for (Ball b : balls) {
                p.setColor(b.color);
                c.drawCircle(b.x,b.y,b.r,p);
                p.setColor(0x55FFFFFF);
                c.drawCircle(b.x-b.r*.32f,b.y-b.r*.32f,b.r*.25f,p);
            }

            catcher.set(catcherX-65, catcherY-18, catcherX+65, catcherY+18);
            p.setColor(0xFF7C5CFC); c.drawRoundRect(catcher,22,22,p);
            p.setColor(0x33FFFFFF); c.drawRoundRect(catcher.left+8,catcher.top+5,catcher.right-8,catcher.bottom-5,16,16,p);

            if (gameOver) drawGameOver(c);
            postInvalidateDelayed(16);
        }

        void update(float dt) {
            if (System.currentTimeMillis()-lastSpawn > Math.max(320, 850-level*35)) {
                spawn(); lastSpawn = System.currentTimeMillis();
            }
            level = 1 + score/10;
            Iterator<Ball> it = balls.iterator();
            while (it.hasNext()) {
                Ball b = it.next(); b.y += b.speed*dt;
                if (b.y+b.r >= catcher.top && b.y-b.r <= catcher.bottom &&
                    b.x >= catcher.left-b.r*.35f && b.x <= catcher.right+b.r*.35f) {
                    score++; if (score>best) best=score; it.remove();
                } else if (b.y-b.r > h) {
                    lives--; it.remove();
                    if (lives <= 0) { gameOver=true; playing=false; }
                }
            }
        }

        void drawHeader(Canvas c) {
            p.setColor(Color.WHITE); p.setTextSize(22); p.setTextAlign(Paint.Align.LEFT);
            c.drawText("תפוס את הכדור",24,42,p);
            p.setTextSize(14); p.setColor(0xFFB9C0D8);
            c.drawText("רמה "+level,24,67,p);
            p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(20); p.setColor(Color.WHITE);
            c.drawText(String.valueOf(score),w-24,43,p);
            p.setTextSize(13); p.setColor(0xFFB9C0D8);
            c.drawText("שיא "+best,w-24,67,p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFF6B8A); p.setTextSize(18);
            c.drawText("♥ ".repeat(Math.max(0,lives)),w/2f,42,p);
        }

        void drawGameOver(Canvas c) {
            p.setColor(0xCC0B0E1A); c.drawRect(0,0,w,h,p);
            p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE);
            p.setTextSize(34); c.drawText("המשחק נגמר",w/2f,h/2f-60,p);
            p.setTextSize(20); p.setColor(0xFFB9C0D8);
            c.drawText("תפסת "+score+" כדורים",w/2f,h/2f-22,p);
            p.setColor(0xFF7C5CFC); c.drawRoundRect(w/2f-100,h/2f+20,w/2f+100,h/2f+78,30,30,p);
            p.setColor(Color.WHITE); p.setTextSize(18); c.drawText("שחק שוב",w/2f,h/2f+57,p);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_MOVE) {
                if (gameOver) {
                    if (e.getAction()==MotionEvent.ACTION_DOWN && e.getY()>h/2f+5) reset();
                } else {
                    catcherX = Math.max(70, Math.min(w-70, e.getX()));
                }
                invalidate(); return true;
            }
            return true;
        }

        static class Ball {
            float x,y,r,speed; int color;
            Ball(float x,float y,float r,float speed,int color){this.x=x;this.y=y;this.r=r;this.speed=speed;this.color=color;}
        }
    }
}
