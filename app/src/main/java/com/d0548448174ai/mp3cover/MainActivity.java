package com.d0548448174ai.mp3cover;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
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

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v23Tag;
import com.mpatric.mp3agic.Mp3File;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE=10, PICK_MP3=11, SAVE_MP3=12;
    private Uri imageUri, mp3Uri, savedUri;
    private File preparedFile;
    private String mp3Name="";
    private ImageView cover;
    private TextView imageLabel, mp3Label, status;
    private Button makeButton, shareButton;
    private ProgressBar progress;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle b){super.onCreate(b); buildUi();}

    private void buildUi(){
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView badge=text("M36 • MP3 TOOL",12,Color.rgb(109,93,253),true);
        badge.setGravity(Gravity.CENTER); badge.setPadding(dp(12),dp(7),dp(12),dp(7));
        badge.setBackground(bg(Color.rgb(238,234,254),50)); root.addView(badge,wrap());

        TextView title=text("עטיפת MP3",31,Color.rgb(24,24,29),true);
        title.setPadding(0,dp(17),0,0); root.addView(title);

        TextView sub=text("בחר תמונה ושיר — וקבל MP3 חדש עם עטיפת אלבום.",15,Color.rgb(105,105,117),false);
        sub.setPadding(0,dp(7),0,dp(16)); root.addView(sub);
        Button cb=button("🐿️  קול צ'יפמאנק לשיר"); cb.setOnClickListener(v->startActivity(new Intent(this, ChipmunkActivity.class))); root.addView(cb,match(0,0,0,12));

        LinearLayout preview=card();
        cover=new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageDrawable(bg(Color.rgb(246,246,249),24));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(150),dp(150)); cp.gravity=Gravity.CENTER;
        preview.addView(cover,cp);
        TextView ph=text("תצוגה מקדימה",12,Color.rgb(120,120,130),false); ph.setGravity(Gravity.CENTER); ph.setPadding(0,dp(9),0,0);
        preview.addView(ph); root.addView(preview,match(0,0,0,12));

        LinearLayout ic=card(); ic.addView(text("1  •  תמונה",18,Color.DKGRAY,true));
        imageLabel=text("לא נבחרה תמונה",13,Color.GRAY,false); imageLabel.setPadding(0,dp(5),0,dp(11)); ic.addView(imageLabel);
        Button ib=button("📷  בחר תמונה"); ib.setOnClickListener(v->pickImage()); ic.addView(ib); root.addView(ic,match(0,0,0,10));

        LinearLayout mc=card(); mc.addView(text("2  •  שיר MP3",18,Color.DKGRAY,true));
        mp3Label=text("לא נבחר שיר",13,Color.GRAY,false); mp3Label.setPadding(0,dp(5),0,dp(11)); mc.addView(mp3Label);
        Button mb=button("🎵  בחר MP3"); mb.setOnClickListener(v->pickMp3()); mc.addView(mb); root.addView(mc,match(0,0,0,10));

        TextView info=text("✓ בנוי בלי Kotlin ובלי AndroidX כדי להישאר קטן ופשוט יותר.",13,Color.rgb(83,83,94),false);
        info.setPadding(dp(13),dp(11),dp(13),dp(11)); info.setBackground(bg(Color.rgb(247,247,250),18)); root.addView(info,match(0,0,0,14));

        makeButton=button("✨  צור MP3 עם עטיפה"); makeButton.setTextSize(17); makeButton.setEnabled(false); makeButton.setAlpha(.45f);
        makeButton.setOnClickListener(v->makeCoveredMp3()); root.addView(makeButton,match(0,0,0,10));

        progress=new ProgressBar(this); progress.setVisibility(View.GONE); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(30),dp(30)); pp.gravity=Gravity.CENTER; root.addView(progress,pp);
        status=text("",13,Color.rgb(109,93,253),true); status.setGravity(Gravity.CENTER); root.addView(status,wrap());

        shareButton=button("↗  שתף את הקובץ"); shareButton.setVisibility(View.GONE); shareButton.setOnClickListener(v->shareFile()); root.addView(shareButton,match(0,dp(10),0,0));
        setContentView(scroll);
    }

    private void pickImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_IMAGE);}
    private void pickMp3(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/mpeg");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_MP3);}

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null||data.getData()==null)return;
        Uri u=data.getData();
        if(req==PICK_IMAGE){imageUri=u;imageLabel.setText(fileName(u));showPreview(u);}
        else if(req==PICK_MP3){mp3Uri=u;mp3Name=fileName(u);mp3Label.setText(mp3Name);}
        else if(req==SAVE_MP3)savePrepared(u);
        refresh();
    }

    private void showPreview(Uri u){executor.execute(()->{try{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        InputStream a=getContentResolver().openInputStream(u);if(a==null)return;BitmapFactory.decodeStream(a,null,o);a.close();
        o.inSampleSize=sample(o.outWidth,o.outHeight,700);o.inJustDecodeBounds=false;
        InputStream b=getContentResolver().openInputStream(u);if(b==null)return;Bitmap bm=BitmapFactory.decodeStream(b,null,o);b.close();
        runOnUiThread(()->{if(bm!=null)cover.setImageBitmap(bm);});
    }catch(Exception ignored){}});}

    private void refresh(){boolean ready=imageUri!=null&&mp3Uri!=null&&preparedFile==null;makeButton.setEnabled(ready);makeButton.setAlpha(ready?1f:.45f);}

    private void makeCoveredMp3(){
        busy(true,"יוצר MP3…"); executor.execute(()->{
            File source=null;
            try{
                source=new File(getCacheDir(),"source.mp3");copyToFile(mp3Uri,source);
                byte[] image=jpegBytes(imageUri);
                Mp3File mp3=new Mp3File(source.getAbsolutePath());
                ID3v2 old=mp3.hasId3v2Tag()?mp3.getId3v2Tag():null;
                String title=old==null?null:old.getTitle(),artist=old==null?null:old.getArtist(),album=old==null?null:old.getAlbum();
                String year=old==null?null:old.getYear(),comment=old==null?null:old.getComment(),track=old==null?null:old.getTrack(),albumArtist=old==null?null:old.getAlbumArtist();
                if(mp3.hasId3v2Tag())mp3.removeId3v2Tag();
                ID3v23Tag tag=new ID3v23Tag();
                if(title!=null)tag.setTitle(title);if(artist!=null)tag.setArtist(artist);if(album!=null)tag.setAlbum(album);
                if(year!=null)tag.setYear(year);if(comment!=null)tag.setComment(comment);if(track!=null)tag.setTrack(track);if(albumArtist!=null)tag.setAlbumArtist(albumArtist);
                tag.setAlbumImage(image,"image/jpeg");tag.setEncoder("M36 MP3 Cover");mp3.setId3v2Tag(tag);
                File out=new File(getCacheDir(),baseName(mp3Name)+"_cover.mp3");if(out.exists())out.delete();mp3.save(out.getAbsolutePath());
                preparedFile=out;final File ready=out;
                runOnUiThread(()->{busy(false,"הקובץ מוכן. בחר איפה לשמור אותו.");Intent s=new Intent(Intent.ACTION_CREATE_DOCUMENT);s.setType("audio/mpeg");s.addCategory(Intent.CATEGORY_OPENABLE);s.putExtra(Intent.EXTRA_TITLE,ready.getName());startActivityForResult(s,SAVE_MP3);});
            }catch(Exception e){if(source!=null)source.delete();runOnUiThread(()->busy(false,"לא הצלחתי לעבד את ה-MP3."));}
        });
    }

    private void savePrepared(Uri dest){if(preparedFile==null)return;final File src=preparedFile;busy(true,"שומר…");executor.execute(()->{try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(dest)){
        if(out==null)throw new Exception("output");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);out.flush();savedUri=dest;src.delete();preparedFile=null;
        runOnUiThread(()->{busy(false,"✅ נשמר בהצלחה.");status.setText("✅ העטיפה משולבת בתוך ה-MP3.");shareButton.setVisibility(View.VISIBLE);refresh();});
    }catch(Exception e){runOnUiThread(()->busy(false,"השמירה נכשלה."));}});}

    private void shareFile(){if(savedUri==null)return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("audio/mpeg");i.putExtra(Intent.EXTRA_STREAM,savedUri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"שתף MP3"));}

    private byte[] jpegBytes(Uri u)throws Exception{
        int max=1400;BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;InputStream a=getContentResolver().openInputStream(u);if(a==null)throw new Exception("image");BitmapFactory.decodeStream(a,null,o);a.close();
        o.inSampleSize=sample(o.outWidth,o.outHeight,max);o.inJustDecodeBounds=false;InputStream b=getContentResolver().openInputStream(u);if(b==null)throw new Exception("image");Bitmap bm=BitmapFactory.decodeStream(b,null,o);b.close();if(bm==null)throw new Exception("image");
        int w=bm.getWidth(),h=bm.getHeight();if(w>max||h>max){float s=Math.min((float)max/w,(float)max/h);Bitmap sc=Bitmap.createScaledBitmap(bm,Math.round(w*s),Math.round(h*s),true);if(sc!=bm)bm.recycle();bm=sc;}
        ByteArrayOutputStream out=new ByteArrayOutputStream();bm.compress(Bitmap.CompressFormat.JPEG,88,out);bm.recycle();return out.toByteArray();
    }

    private int sample(int w,int h,int max){int s=1;while(w/s>max*2||h/s>max*2)s*=2;return Math.max(1,s);}
    private void copyToFile(Uri u,File f)throws Exception{try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){if(in==null)throw new Exception("input");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    private String fileName(Uri u){String n=null;Cursor c=null;try{c=getContentResolver().query(u,null,null,null,null);if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)n=c.getString(i);}}catch(Exception ignored){}finally{if(c!=null)c.close();}return n==null?(u.getLastPathSegment()==null?"file":u.getLastPathSegment()):n;}
    private String baseName(String n){if(n==null||n.trim().isEmpty())return"song";int p=n.toLowerCase().lastIndexOf(".mp3");if(p>0)n=n.substring(0,p);return n.replaceAll("[\\/:*?<>|]","_").trim();}
    private void busy(boolean b,String s){progress.setVisibility(b?View.VISIBLE:View.GONE);status.setText(s);boolean ready=!b&&imageUri!=null&&mp3Uri!=null&&preparedFile==null;makeButton.setEnabled(ready);makeButton.setAlpha(ready?1f:.45f);}
    private TextView text(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(15),dp(15),dp(15),dp(15));l.setBackground(bg(Color.WHITE,20));l.setElevation(dp(2));return l;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(Color.rgb(44,44,50));b.setGravity(Gravity.CENTER);b.setPadding(dp(13),dp(10),dp(13),dp(10));b.setBackground(bg(Color.rgb(247,247,250),16));return b;}
    private GradientDrawable bg(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));d.setStroke(dp(1),Color.rgb(232,232,238));return d;}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams match(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
