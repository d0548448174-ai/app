package com.david.gamestation;

import android.graphics.*;
import android.view.*;
import java.util.*;

public class GameView extends View {
    final MainActivity a;
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Random rnd=new Random();
    final String[] games={"Turbo Drive 3D","Cyber Arena 3D","Astro Defender 3D"};
    final String[] desc={"Neon highway racing with traffic, depth and boost.","Survive escalating waves in a 3D combat arena.","Defend the station through a deep-space asteroid field."};
    final int[] acc={0xff4f9fff,0xff9c69ff,0xff38dcc7};
    int page=0,sel=0,active=0,score=0;
    float px=0,py=0; boolean left,right,up,down,fire,boost;
    long last=System.nanoTime();
    ArrayList<Cube> cubes=new ArrayList<>(); ArrayList<Star> stars=new ArrayList<>();
    ArrayList<GameStore.Item> imported=new ArrayList<>();
    static class Cube{float x,y,z,s;int c;Cube(float x,float y,float z,float s,int c){this.x=x;this.y=y;this.z=z;this.s=s;this.c=c;}}
    static class Star{float x,y,z;Star(float x,float y,float z){this.x=x;this.y=y;this.z=z;}}
    public GameView(MainActivity a){super(a);this.a=a;setFocusable(true);refreshLibrary();}
    public void refreshLibrary(){imported=a.store.list();invalidate();}
    public void backToHome(){page=0;invalidate();}
    void txt(Canvas c,String s,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans",Typeface.BOLD));c.drawText(s,x,y,p);}
    void round(Canvas c,float l,float t,float r,float b,float rad,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,p);}
    void background(Canvas c,int accent){
        p.setShader(new LinearGradient(0,0,getWidth(),getHeight(),0xff06101c,0xff0b1524,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(null);
        p.setColor(accent&0x25ffffff);c.drawCircle(getWidth()*0.82f,120,260,p);
        p.setColor(0x182f5f9c);c.drawCircle(getWidth()*0.14f,getHeight()*0.85f,250,p);
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(page==0)drawHome(c);else drawGame(c);if(page==1){update();postInvalidateDelayed(16);}}
    void drawHome(Canvas c){
        int color=sel<3?acc[sel]:0xffff709b;background(c,color);
        txt(c,"GAMESTATION",30,39,22,Color.WHITE);txt(c,"Games",192,38,15,Color.WHITE);txt(c,"Media",255,38,15,0xff8997ad);
        txt(c,"⌕",getWidth()-148,39,22,0xffb0bdd0);txt(c,"⚙",getWidth()-108,38,18,0xffb0bdd0);round(c,getWidth()-59,12,getWidth()-25,46,17,0xff6ca5ff);
        int total=3+imported.size();
        for(int i=0;i<Math.min(total,7);i++){float x=30+i*124;if(i==sel)round(c,x-5,62,x+109,152,21,Color.WHITE);int z=i<3?acc[i]:0xffff709b;
            p.setShader(new LinearGradient(x,66,x+104,146,z,0xff0a1322,Shader.TileMode.CLAMP));c.drawRoundRect(x,66,x+104,146,17,17,p);p.setShader(null);
            txt(c,i<3?"3D":"APK",x+10,84,9,Color.WHITE);txt(c,i<3?games[i]:imported.get(i-3).name,x+10,135,10,Color.WHITE);}
        round(c,30,173,getWidth()-30,463,28,0xff0b1524);
        p.setShader(new LinearGradient(30,173,getWidth()-30,463,color,0xff0b1524,Shader.TileMode.CLAMP));c.drawRoundRect(30,173,getWidth()-30,463,28,28,p);p.setShader(null);
        if(sel<3){
            txt(c,"OFFLINE  •  3D  •  LANDSCAPE",54,214,10,0xffcad4e2);txt(c,games[sel],54,260,37,Color.WHITE);txt(c,desc[sel],54,288,14,0xffb7c3d5);
            round(c,54,328,190,380,14,Color.WHITE);txt(c,"PLAY GAME  ›",77,360,13,0xff08111d);
            round(c,204,328,316,380,14,0x20ffffff);txt(c,"ADD APK",228,360,12,Color.WHITE);hero(c,sel,getWidth()-340,198);
        }else{
            GameStore.Item g=imported.get(sel-3);txt(c,"IMPORTED GAME",54,214,10,0xffd1dbea);txt(c,g.name,54,260,32,Color.WHITE);txt(c,g.pkg,54,288,11,0xff8c9bb0);
            round(c,54,328,210,380,14,Color.WHITE);txt(c,"PLAY / INSTALL",74,360,12,0xff08111d);
            round(c,220,328,316,380,14,0x20ffffff);txt(c,"REMOVE",243,360,11,Color.WHITE);
        }
        round(c,30,482,getWidth()*0.57f,getHeight()-22,21,0xe30b1524);txt(c,"CONTINUE PLAYING",49,511,10,0xff8d9ab0);txt(c,"Turbo Drive 3D",49,550,22,Color.WHITE);txt(c,"Offline • high score",49,572,11,0xff77879f);
        round(c,49,592,300,598,4,0xff263347);round(c,49,592,210,598,4,0xff6fa8ff);
        round(c,getWidth()*0.59f,482,getWidth()-30,getHeight()-22,21,0xe30b1524);txt(c,"IMPORTED GAMES",getWidth()*0.59f+17,511,10,0xff8d9ab0);
        float yy=533;if(imported.isEmpty())txt(c,"No APKs — press ADD APK",getWidth()*0.59f+17,560,12,0xff6d7d96);
        for(GameStore.Item g:imported){if(yy>getHeight()-35)break;txt(c,g.name,getWidth()*0.59f+17,yy+16,11,Color.WHITE);txt(c,g.pkg,getWidth()*0.59f+17,yy+31,9,0xff6d7d95);yy+=45;}
        txt(c,"◀ ▶ SELECT     ENTER PLAY     A ADD APK",30,getHeight()-7,9,0xff697991);
    }
    void hero(Canvas c,int id,float x,float y){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x50ffffff);
        for(int i=0;i<7;i++){Path q=new Path();q.moveTo(x+i*34,y+230);q.lineTo(x+132,y+30);q.lineTo(x+270-i*28,y+230);c.drawPath(q,p);}p.setStyle(Paint.Style.FILL);
        if(id==0){round(c,x+74,y+112,x+202,y+182,22,0xff2d9cff);round(c,x+96,y+124,x+180,y+151,11,0xffe3f4ff);}
        if(id==1){p.setColor(0xffa26bff);c.drawCircle(x+132,y+120,68,p);p.setColor(0xff2c1d43);c.drawCircle(x+132,y+120,27,p);}
        if(id==2){p.setColor(0xff38dac8);Path q=new Path();q.moveTo(x+132,y+56);q.lineTo(x+96,y+180);q.lineTo(x+132,y+154);q.lineTo(x+168,y+180);q.close();c.drawPath(q,p);}
    }
    void start(int id){
        active=id;score=0;px=py=0;cubes.clear();stars.clear();page=1;last=System.nanoTime();
        if(id==0)for(int i=0;i<11;i++)cubes.add(new Cube((rnd.nextInt(5)-2)*2.1f,0,12+i*8,.9f,rnd.nextBoolean()?0xffff4b63:0xffffa832));
        if(id==1)for(int i=0;i<8;i++)cubes.add(new Cube(rnd.nextFloat()*12-6,rnd.nextFloat()*4-2,12+i*7,.8f,0xffa26bff));
        if(id==2){for(int i=0;i<17;i++)cubes.add(new Cube(rnd.nextFloat()*16-8,rnd.nextFloat()*10-5,5+rnd.nextFloat()*70,.65f,0xff8993a5));for(int i=0;i<100;i++)stars.add(new Star(rnd.nextFloat()*16-8,rnd.nextFloat()*10-5,2+rnd.nextFloat()*70));}
    }
    float sx(float x,float z){return getWidth()/2f+x*280f/(z+8f);} float sy(float y,float z){return getHeight()*.43f-y*250f/(z+8f);}
    void cube(Canvas c,float x,float y,float z,float s,int color){
        float l=sx(x-s,z),r=sx(x+s,z),l2=sx(x-s,z+s*1.5f),r2=sx(x+s,z+s*1.5f);float t=sy(y+s,z),b=sy(y-s,z),t2=sy(y+s,z+s*1.5f),b2=sy(y-s,z+s*1.5f);
        p.setColor(color);Path q=new Path();q.moveTo(l,t);q.lineTo(r,t);q.lineTo(r2,t2);q.lineTo(l2,t2);q.close();c.drawPath(q,p);
        p.setColor(color&0x88ffffff);Path side=new Path();side.moveTo(r,t);side.lineTo(r,b);side.lineTo(r2,b2);side.lineTo(r2,t2);side.close();c.drawPath(side,p);
    }
    void drawGame(Canvas c){
        background(c,acc[active]);txt(c,"‹ GAME LIBRARY",20,35,11,Color.WHITE);txt(c,games[active],getWidth()-220,35,12,Color.WHITE);txt(c,"SCORE "+score,getWidth()-95,35,10,0xffdce6f4);
        if(active==0)drawRacer(c);else if(active==1)drawArena(c);else drawSpace(c);
        round(c,18,getHeight()-78,225,getHeight()-16,18,0xc70a1421);txt(c,active==0?"◀   ▶   BOOST":"◀   ▶   ▲   ▼   FIRE",38,getHeight()-43,13,Color.WHITE);txt(c,"TOUCH • KEYBOARD READY",38,getHeight()-24,9,0xff71829b);
    }
    void drawRacer(Canvas c){
        p.setColor(0xff102136);Path road=new Path();road.moveTo(getWidth()*.14f,getHeight());road.lineTo(getWidth()*.38f,getHeight()*.43f);road.lineTo(getWidth()*.62f,getHeight()*.43f);road.lineTo(getWidth()*.86f,getHeight());road.close();c.drawPath(road,p);
        for(Cube q:cubes)cube(c,q.x,0,q.z,q.s,q.c);cube(c,px,.1f,3,.95f,0xff39a9ff);cube(c,px,.68f,3,.55f,0xffe2f3ff);
    }
    void drawArena(Canvas c){
        for(int i=0;i<8;i++){float z=4+i*5;p.setColor(i%2==0?0xff10243a:0xff0d1d30);Path q=new Path();q.moveTo(getWidth()*.1f,sy(-.5f,z));q.lineTo(getWidth()*.9f,sy(-.5f,z));q.lineTo(getWidth()*.72f,sy(-.5f,z+4));q.lineTo(getWidth()*.28f,sy(-.5f,z+4));q.close();c.drawPath(q,p);}
        for(Cube q:cubes)cube(c,q.x,q.y,q.z,q.s,q.c);cube(c,px,py,3,.7f,0xff64b7ff);
    }
    void drawSpace(Canvas c){
        for(Star s:stars){s.z-=1.0f;if(s.z<1){s.z=70;s.x=rnd.nextFloat()*16-8;s.y=rnd.nextFloat()*10-5;}p.setColor(0xffbfddff);c.drawCircle(sx(s.x,s.z),sy(s.y,s.z),Math.max(1,42/s.z),p);}
        for(Cube q:cubes){float r=Math.max(4,70/(q.z+8));p.setColor(q.c);c.drawCircle(sx(q.x,q.z),sy(q.y,q.z),r,p);}cube(c,px,py,3,.7f,0xff43dfcf);
    }
    void update(){
        float dt=Math.min(.035f,(System.nanoTime()-last)/1e9f);last=System.nanoTime();
        if(active==0){if(left)px-=6*dt;if(right)px+=6*dt;px=Math.max(-4.7f,Math.min(4.7f,px));for(Cube q:cubes){q.z-=(boost?38:27)*dt;if(q.z<1){q.z=80+rnd.nextInt(25);q.x=(rnd.nextInt(5)-2)*2.1f;score+=30;}}score+=boost?2:1;}
        else if(active==1){if(left)px-=6*dt;if(right)px+=6*dt;if(up)py+=5*dt;if(down)py-=5*dt;px=Math.max(-6,Math.min(6,px));py=Math.max(-3,Math.min(3,py));for(Cube q:cubes){q.z-=8*dt;if(q.z<1){q.z=30+rnd.nextInt(18);q.x=rnd.nextFloat()*12-6;q.y=rnd.nextFloat()*4-2;}}if(fire){for(Cube q:cubes)if(Math.abs(q.x-px)<1.1f&&q.z<8){q.z=30;score+=150;break;}fire=false;}}
        else {if(left)px-=7*dt;if(right)px+=7*dt;if(up)py+=5*dt;if(down)py-=5*dt;px=Math.max(-7,Math.min(7,px));py=Math.max(-4,Math.min(4,py));for(Cube q:cubes){q.z-=35*dt;if(q.z<1){q.z=65+rnd.nextInt(40);q.x=rnd.nextFloat()*16-8;q.y=rnd.nextFloat()*10-5;score+=20;}}if(fire){score+=50;fire=false;}}
    }
    void input(float x,boolean on){
        if(active==0){left=on&&x<getWidth()*.22f;right=on&&x>getWidth()*.22f&&x<getWidth()*.45f;boost=on&&x>getWidth()*.72f;}
        else{left=on&&x<getWidth()*.14f;right=on&&x>getWidth()*.14f&&x<getWidth()*.28f;down=on&&x>getWidth()*.28f&&x<getWidth()*.43f;up=on&&x>getWidth()*.43f&&x<getWidth()*.58f;fire=on&&x>getWidth()*.72f;}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            if(page==0){
                if(y<160){int i=(int)((x-30)/124);if(i>=0&&i<3+imported.size()){sel=i;invalidate();return true;}}
                if(y>310&&y<405){if(sel<3){if(x<200)start(sel);else if(x<350)a.pickApk();}else{GameStore.Item g=imported.get(sel-3);if(x<240)a.store.play(g);else a.store.remove(g,this);}return true;}
            }else if(y<65&&x<180){backToHome();return true;}
            input(x,true);
        }else if(page==1&&(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL))input(x,false);
        return true;
    }
}
