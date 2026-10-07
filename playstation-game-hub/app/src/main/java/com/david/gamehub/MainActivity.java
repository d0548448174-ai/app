package com.david.gamehub;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {
    HubView hub;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.rgb(7,8,15));
        getWindow().setStatusBarColor(Color.rgb(7,8,15));
        hub = new HubView();
        setContentView(hub);
        immersive();
    }
    void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    void chooseApk() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/vnd.android.package-archive");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,77);
    }
    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==77 && c==RESULT_OK && d!=null && d.getData()!=null) {
            Uri u=d.getData();
            try {
                Intent install=new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(u,"application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(install);
            } catch(Exception e) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+getPackageName()))); } catch(Exception ignored) {}
            }
        }
    }

    class HubView extends View {
        Paint p=new Paint(3); Paint stroke=new Paint(3);
        String[] names={"NEON RUSH","STARFALL","BRICK//BREAK","CYBER MATCH","ORBIT RUN"};
        String[] subs={"Arcade Racer","Space Shooter","Breakout","Memory Duel","Endless Dash"};
        int[] accents={0xff6c5ce7,0xff00d9ff,0xffff4d8d,0xffffb000,0xff4dff9d};
        int selected=0; int screen=0; int game=0; boolean favorite=false;
        float downX,downY; long last=System.nanoTime(); float dt;
        int score=0, high=0; boolean playing=false, gameOver=false;
        Random rnd=new Random();
        ArrayList<RectF> blocks=new ArrayList<>();
        float px,py,vx,vy;
        int lives=3;
        int[][] mem=new int[4][4]; boolean[][] shown=new boolean[4][4]; int first=-1, second=-1; long flipAt;
        ArrayList<Particle> particles=new ArrayList<>();
        public HubView(){ super(MainActivity.this); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); stroke.setStyle(Paint.Style.STROKE); setFocusable(true); }

        protected void onDraw(Canvas c){
            super.onDraw(c);
            dt=Math.min(.05f,(System.nanoTime()-last)/1e9f); last=System.nanoTime();
            if(screen==0) drawHome(c); else if(screen==1) drawDetails(c); else drawGame(c);
            if(screen==2 && playing) { updateGame(); postInvalidateDelayed(16); }
        }
        void bg(Canvas c){
            LinearGradient g=new LinearGradient(0,0,getWidth(),getHeight(),0xff080912,0xff11162b,Shader.TileMode.CLAMP);
            p.setShader(g); c.drawRect(0,0,getWidth(),getHeight(),p); p.setShader(null);
            p.setColor(0x1200d9ff);
            for(int i=0;i<12;i++) c.drawCircle((i*173+40)%getWidth(),80+(i*97)%getHeight(),2,p);
        }
        void text(Canvas c,String s,float x,float y,float size,int color){
            p.setTextSize(size); p.setColor(color); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); c.drawText(s,x,y,p);
        }
        void rr(Canvas c,float l,float t,float r,float b,float rad,int color){
            p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(l,t,r,b,rad,rad,p);
        }
        void drawHome(Canvas c){
            bg(c);
            text(c,"GAMESTATION",42,58,28,Color.WHITE);
            text(c,"GAME LIBRARY",43,82,12,0xff8992ad);
            text(c,"▣  LIBRARY",getWidth()-360,55,14,0xffcbd2e8);
            text(c,"＋  ADD APK",getWidth()-225,55,14,0xffcbd2e8);
            float w=Math.min(260,getWidth()/4.5f), h=175, gap=18, y=128;
            float start=32;
            for(int i=0;i<5;i++){
                float x=start+i*(w+gap);
                if(x+w>getWidth()-25) break;
                card(c,i,x,y,w,h,i==selected);
            }
            float bx=32, by=330;
            rr(c,bx,by,getWidth()-32,getHeight()-30,26,0x18000000);
            text(c,"SELECTED",bx+26,by+34,11,0xff7f8ba8);
            text(c,names[selected],bx+26,by+72,30,Color.WHITE);
            text(c,subs[selected],bx+26,by+96,14,0xff9ba6c2);
            text(c,"A polished offline arcade collection. Pick a title and press PLAY.",bx+26,by+128,14,0xff707b98);
            rr(c,getWidth()-190,by+42,getWidth()-55,by+92,16,accents[selected]);
            text(c,"OPEN  ›",getWidth()-157,by+74,14,Color.WHITE);
            text(c,"← →  SELECT    ENTER  OPEN    MENU  ADD APK",42,getHeight()-8,11,0xff66708b);
        }
        void card(Canvas c,int i,float x,float y,float w,float h,boolean sel){
            if(sel){ rr(c,x-4,y-4,x+w+4,y+h+4,20,accents[i]); }
            rr(c,x,y,x+w,y+h,18,0xff141a2c);
            // cover illustration
            int a=accents[i];
            p.setShader(new LinearGradient(x,y,x+w,y+h,a,0xff0b0d18,Shader.TileMode.CLAMP));
            c.drawRoundRect(x,y,x+w,y+h,18,18,p); p.setShader(null);
            if(i==0){ p.setColor(0x55ffffff); for(int k=0;k<8;k++) c.drawRect(x+15+k*28,y+30+k*12,x+23+k*28,y+38+k*12,p); c.drawCircle(x+w*.7f,y+h*.58f,28,p); }
            if(i==1){ p.setColor(0xfff7fbff); c.drawCircle(x+w*.68f,y+h*.42f,25,p); p.setColor(a); c.drawCircle(x+w*.68f,y+h*.42f,12,p); for(int k=0;k<4;k++){p.setColor(0xffdce8ff); c.drawRect(x+20+k*35,y+h-45,x+26+k*35,y+h-25,p);} }
            if(i==2){ p.setColor(0xffe8f0ff); for(int r=0;r<3;r++)for(int q=0;q<6;q++) rr(c,x+18+q*34,y+28+r*20,x+47+q*34,y+43+r*20,4,a); p.setColor(0xffffffff); c.drawCircle(x+w*.52f,y+h*.76f,7,p); }
            if(i==3){ p.setColor(0xffffffff); for(int r=0;r<2;r++)for(int q=0;q<2;q++) rr(c,x+35+q*65,y+35+r*50,x+87+q*65,y+78+r*50,9,0xff232b45); }
            if(i==4){ p.setColor(0xffd9fff0); c.drawCircle(x+w*.48f,y+h*.55f,31,p); p.setColor(a); c.drawCircle(x+w*.48f,y+h*.55f,17,p); p.setColor(0xffe8fff6); c.drawCircle(x+w*.48f,y+h*.55f,7,p); }
            rr(c,x+12,y+h-46,x+w-12,y+h-12,10,0xcc080912);
            text(c,names[i],x+22,y+h-25,13,Color.WHITE);
            if(sel) text(c,"●",x+w-32,y+24,10,Color.WHITE);
        }
        void drawDetails(Canvas c){
            bg(c);
            text(c,"‹  BACK",35,45,13,0xffb9c2d9);
            float x=42,y=85,w=getWidth()*.42f,h=300;
            rr(c,x,y,x+w,y+h,24,0xff141a2c);
            p.setColor(accents[selected]); p.setStrokeWidth(5); c.drawRoundRect(x+3,y+3,x+w-3,y+h-3,22,22,p);
            p.setStyle(Paint.Style.FILL);
            text(c,names[selected],x+w+35,y+48,38,Color.WHITE);
            text(c,subs[selected],x+w+35,y+76,16,accents[selected]);
            text(c,gameDescription(selected),x+w+35,y+120,15,0xffaeb7ce);
            text(c,"OFFLINE • LANDSCAPE • CONTROLLER READY",x+w+35,y+151,11,0xff69738d);
            rr(c,x+w+35,y+185,x+w+195,y+238,16,accents[selected]);
            text(c,"PLAY NOW  ▶",x+w+58,y+218,14,Color.WHITE);
            rr(c,x+w+210,y+185,x+w+280,y+238,16,0xff20283d);
            text(c,favorite?"★":"☆",x+w+233,y+220,25,Color.WHITE);
            text(c,"BEST SCORE  "+high,x+w+35,y+278,13,0xff7f8ba8);
        }
        String gameDescription(int i){
            switch(i){
                case 0:return "Dodge the traffic, chain near-misses and survive the neon highway.";
                case 1:return "Drag your fighter, destroy incoming waves and chase a high score.";
                case 2:return "Classic brick action with satisfying rebounds, lives and combo scoring.";
                case 3:return "Find matching pairs before the board fills your memory.";
                default:return "Time your jumps, clear hazards and push your run as far as possible.";
            }
        }
        void drawGame(Canvas c){
            bg(c);
            text(c,"‹  "+names[game],28,40,16,Color.WHITE);
            text(c,"SCORE "+score, getWidth()-230,40,14,0xffd9e0f2);
            text(c,"BEST "+high, getWidth()-105,40,12,0xff78839d);
            if(game==0) drawNeon(c); else if(game==1) drawSpace(c); else if(game==2) drawBrick(c); else if(game==3) drawMemory(c); else drawOrbit(c);
            if(gameOver){
                p.setColor(0xbb050711); c.drawRect(0,0,getWidth(),getHeight(),p);
                text(c,"RUN OVER",getWidth()/2-100,getHeight()/2-20,34,Color.WHITE);
                text(c,"SCORE  "+score,getWidth()/2-55,getHeight()/2+15,15,0xffaeb8cf);
                rr(c,getWidth()/2-85,getHeight()/2+40,getWidth()/2+85,getHeight()/2+92,16,accents[game]);
                text(c,"RESTART",getWidth()/2-43,getHeight()/2+72,14,Color.WHITE);
            }
        }
        void resetGame(){
            score=0; gameOver=false; playing=true; particles.clear(); blocks.clear(); lives=3; vx=0; vy=0;
            if(game==0){px=getWidth()/2; py=getHeight()-105; for(int i=0;i<7;i++) blocks.add(new RectF(90+i*150,-80-i*100,145+i*150,-25-i*100));}
            if(game==1){px=getWidth()/2; py=getHeight()-95; for(int i=0;i<8;i++) blocks.add(new RectF(40+i*120,-80-i*130,95+i*120,-25-i*130));}
            if(game==2){px=getWidth()/2; py=getHeight()-70; vx=260; vy=-260;}
            if(game==3){ArrayList<Integer> vals=new ArrayList<>();for(int i=0;i<8;i++){vals.add(i);vals.add(i);}Collections.shuffle(vals);int k=0;for(int r=0;r<4;r++)for(int q=0;q<4;q++){mem[r][q]=vals.get(k++);shown[r][q]=false;}}
            if(game==4){px=95;py=getHeight()-95;vy=0; for(int i=0;i<6;i++)blocks.add(new RectF(getWidth()+i*180,getHeight()-120,getWidth()+i*180+45,getHeight()-55));}
        }
        void updateGame(){
            if(gameOver)return;
            if(game==0){ for(RectF b:blocks){b.offset(-220*dt,210*dt); if(b.top>getHeight()){b.set(getWidth()+rnd.nextInt(180),-80-rnd.nextInt(250),b.left+55,b.bottom+55);score+=10;} if(b.intersects(new RectF(px-22,py-22,px+22,py+22))){gameOver=true;high=Math.max(high,score);}} for(Particle z:particles)z.tick(dt); }
            if(game==1){for(RectF b:blocks){b.offset(-150*dt,190*dt);if(b.top>getHeight()){b.set(40+rnd.nextInt(Math.max(50,getWidth()-100)),-80-rnd.nextInt(300),b.left+55,b.bottom+55);score+=15;}}for(Particle z:particles)z.tick(dt);for(RectF b:blocks)if(b.intersects(new RectF(px-18,py-18,px+18,py+18))){gameOver=true;high=Math.max(high,score);}}
            if(game==2){px+=vx*dt;py+=vy*dt;if(px<30||px>getWidth()-30)vx=-vx;if(py<65){py=65;vy=-vy;}if(py>getHeight()-45){py=getHeight()-45;vy=-Math.abs(vy);lives--;if(lives<=0){gameOver=true;high=Math.max(high,score);}}if(Math.random()<.02)score++;for(int r=0;r<3;r++)for(int q=0;q<8;q++){float bx=60+q*75,by=75+r*32;if(Math.abs(px-bx)<34&&Math.abs(py-by)<18){vy=-Math.abs(vy);score+=25;}}}
            if(game==3){if(second>=0 && System.currentTimeMillis()-flipAt>600){shown[first/4][first%4]=false;shown[second/4][second%4]=false;first=second=-1;}if(first>=0&&second>=0&&mem[first/4][first%4]==mem[second/4][second%4]){shown[first/4][first%4]=shown[second/4][second%4]=true;score+=100;first=second=-1;boolean done=true;for(int r=0;r<4;r++)for(int q=0;q<4;q++)if(!shown[r][q])done=false;if(done){gameOver=true;high=Math.max(high,score);}}}
            if(game==4){py+=vy*dt;vy+=720*dt;for(RectF b:blocks)b.offset(-280*dt,0);if(blocks.get(blocks.size()-1).right<getWidth())blocks.add(new RectF(getWidth()+120,getHeight()-120,getWidth()+165,getHeight()-55));for(RectF b:blocks)if(b.right<0){score+=10;b.set(getWidth()+100+rnd.nextInt(180),getHeight()-120,getWidth()+145+rnd.nextInt(180),getHeight()-55);}if(py>getHeight()-95){py=getHeight()-95;vy=0;}for(RectF b:blocks)if(b.intersects(new RectF(px-20,py-20,px+20,py+20))){gameOver=true;high=Math.max(high,score);}}
        }
        void drawNeon(Canvas c){rr(c,25,55,getWidth()-25,getHeight()-25,24,0xff0b1020);p.setColor(0xff202943);for(int i=0;i<8;i++)c.drawRect(80+i*120,55,84+i*120,getHeight()-25,p);p.setColor(accents[0]);for(RectF b:blocks)c.drawRoundRect(b,10,10,p);p.setColor(Color.WHITE);c.drawCircle(px,py,22,p);p.setColor(accents[0]);c.drawCircle(px,py,13,p);text(c,"STEER  ◀  ▶",35,getHeight()-5,11,0xff6d7894);}
        void drawSpace(Canvas c){rr(c,25,55,getWidth()-25,getHeight()-25,24,0xff070e1e);p.setColor(0xffdce8ff);for(int i=0;i<45;i++)c.drawCircle((i*83)%getWidth(),65+(i*47)%(getHeight()-90),1.5f,p);p.setColor(accents[1]);for(RectF b:blocks)c.drawRoundRect(b,8,8,p);p.setColor(Color.WHITE);Path ship=new Path();ship.moveTo(px,py-24);ship.lineTo(px-20,py+18);ship.lineTo(px,py+9);ship.lineTo(px+20,py+18);ship.close();c.drawPath(ship,p);text(c,"DRAG TO MOVE",35,getHeight()-5,11,0xff6d7894);}
        void drawBrick(Canvas c){rr(c,25,55,getWidth()-25,getHeight()-25,24,0xff0d1020);for(int r=0;r<3;r++)for(int q=0;q<8;q++){p.setColor(accents[(q+r)%5]);c.drawRoundRect(45+q*75,75+r*32,112+q*75,99+r*32,6,6,p);}p.setColor(Color.WHITE);c.drawCircle(px,py,8,p);p.setColor(0xffd9e3ff);c.drawRoundRect(px-48,getHeight()-42,px+48,getHeight()-30,6,6,p);text(c,"LIVES "+lives,35,getHeight()-5,11,0xff6d7894);}
        void drawMemory(Canvas c){rr(c,25,55,getWidth()-25,getHeight()-25,24,0xff0d1020);float s=75, sx=(getWidth()-s*4-24*3)/2,sy=75;for(int r=0;r<4;r++)for(int q=0;q<4;q++){float x=sx+q*(s+24),y=sy+r*(s+24);if(shown[r][q]){rr(c,x,y,x+s,y+s,12,accents[mem[r][q]%5]);text(c,""+(mem[r][q]+1),x+30,y+48,24,Color.WHITE);}else rr(c,x,y,x+s,y+s,12,0xff202941);}}
        void drawOrbit(Canvas c){rr(c,25,55,getWidth()-25,getHeight()-25,24,0xff07151a);p.setColor(0xff21433c);c.drawRect(25,getHeight()-55,getWidth()-25,getHeight()-25,p);for(RectF b:blocks){p.setColor(accents[4]);c.drawRoundRect(b,8,8,p);}p.setColor(0xffeafff7);c.drawCircle(px,py,20,p);p.setColor(accents[4]);c.drawCircle(px,py,11,p);text(c,"TAP TO JUMP",35,getHeight()-5,11,0xff6d7894);}
        public boolean onTouchEvent(android.view.MotionEvent e){
            float x=e.getX(),y=e.getY();
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=x;downY=y;
                if(screen==0){for(int i=0;i<5;i++){float w=Math.min(260,getWidth()/4.5f),gap=18,xx=32+i*(w+gap);if(x>=xx&&x<=xx+w&&y>=128&&y<=303){selected=i;invalidate();return true;}}if(y<80&&x>getWidth()-260){chooseApk();return true;}if(y>330&&x>getWidth()-220){screen=1;invalidate();return true;}}
                else if(screen==1){if(y<70){screen=0;}else if(x>getWidth()*.45f&&y>240&&y<360){game=selected;screen=2;resetGame();}else if(x>getWidth()*.45f&&y>250&&x<getWidth()*.45f+300){favorite=!favorite;}invalidate();return true;}
                else if(screen==2){if(gameOver){if(y>getHeight()/2+35&&y<getHeight()/2+115){resetGame();invalidate();return true;}if(y<60){screen=0;invalidate();return true;}}else{if(game==0){px=x;}if(game==1){px=x;py=Math.max(70,Math.min(getHeight()-80,y));}if(game==4){vy=-390;}if(game==3){float s=75,sx=(getWidth()-s*4-24*3)/2,sy=75;for(int r=0;r<4;r++)for(int q=0;q<4;q++){float xx=sx+q*(s+24),yy=sy+r*(s+24);if(x>=xx&&x<=xx+s&&y>=yy&&y<=yy+s&&!shown[r][q]){int id=r*4+q;if(first<0)first=id;else if(second<0&&id!=first){second=id;flipAt=System.currentTimeMillis();}}}}invalidate();return true;}}
            } else if(e.getAction()==MotionEvent.ACTION_MOVE && screen==2 && !gameOver){if(game==0||game==1){px=x;if(game==1)py=Math.max(70,Math.min(getHeight()-80,y));}invalidate();}
            return true;
        }
    }
    class Particle {float x,y,vx,vy,life;Particle(float a,float b){x=a;y=b;vx=(rnd.nextFloat()-.5f)*300;vy=(rnd.nextFloat()-.5f)*300;life=1;}void tick(float d){x+=vx*d;y+=vy*d;life-=d;}}
}