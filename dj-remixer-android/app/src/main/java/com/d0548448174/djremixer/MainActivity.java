package com.d0548448174.djremixer;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;
import com.arthenica.ffmpegkit.*;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO=41;
    private Uri selectedUri;
    private TextView songLabel,status;
    private SeekBar energy;
    private Spinner bpmSpinner;
    private Button remixBtn,chooseBtn;
    private int bpm=132;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
    }

    private TextView tv(String s,int size){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size);
        t.setPadding(0,10,0,10); return t;
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,24,28,24); root.setBackgroundColor(Color.rgb(9,7,18));
        TextView title=tv("DJ TURBO REMIX",30); title.setGravity(Gravity.CENTER);
        title.setTypeface(null,1); root.addView(title,new LinearLayout.LayoutParams(-1,70));
        TextView sub=tv("הופך את השיר שלך לגרסת מסיבה קופצת",16); sub.setGravity(Gravity.CENTER);
        root.addView(sub,new LinearLayout.LayoutParams(-1,55));

        chooseBtn=new Button(this); chooseBtn.setText("🎵  בחר שיר מהטלפון"); root.addView(chooseBtn,new LinearLayout.LayoutParams(-1,60));
        songLabel=tv("עדיין לא נבחר שיר",17); songLabel.setGravity(Gravity.CENTER);
        root.addView(songLabel,new LinearLayout.LayoutParams(-1,60));

        root.addView(tv("מהירות טראנס",16));
        bpmSpinner=new Spinner(this);
        String[] bp={"128 BPM — קלאסי","132 BPM — אנרגטי","136 BPM — חזק","140 BPM — מקסימום"};
        bpmSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,bp));
        root.addView(bpmSpinner,new LinearLayout.LayoutParams(-1,55));

        root.addView(tv("עוצמת הטירוף",16));
        energy=new SeekBar(this); energy.setMax(100); energy.setProgress(78);
        root.addView(energy,new LinearLayout.LayoutParams(-1,55));

        remixBtn=new Button(this); remixBtn.setText("⚡ צור DJ REMIX"); remixBtn.setTextSize(19);
        root.addView(remixBtn,new LinearLayout.LayoutParams(-1,72));

        status=tv("העיבוד מתבצע כולו במכשיר.",15); status.setGravity(Gravity.CENTER);
        root.addView(status,new LinearLayout.LayoutParams(-1,80));

        ProgressBar pb=new ProgressBar(this); pb.setVisibility(View.GONE);
        root.addView(pb,new LinearLayout.LayoutParams(-1,50));

        chooseBtn.setOnClickListener(v->pickAudio());
        remixBtn.setOnClickListener(v->startRemix(pb));
        bpmSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ bpm=128+pos*4; }
        });
        setContentView(root);
    }

    private void pickAudio(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_AUDIO);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==PICK_AUDIO && c==RESULT_OK && d!=null){
            selectedUri=d.getData();
            try{
                String name=getDisplayName(selectedUri);
                songLabel.setText("✓ "+name);
                getContentResolver().takePersistableUriPermission(selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }catch(Exception e){ songLabel.setText("✓ השיר נבחר"); }
        }
    }

    private String getDisplayName(Uri uri){
        Cursor cur=getContentResolver().query(uri,null,null,null,null);
        if(cur!=null){ try{
            int n=cur.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if(cur.moveToFirst() && n>=0) return cur.getString(n);
        }finally{cur.close();}}
        return "השיר שנבחר";
    }

    private File copyInput() throws Exception{
        File in=new File(getCacheDir(),"source_audio");
        try(InputStream is=getContentResolver().openInputStream(selectedUri);
            OutputStream os=new FileOutputStream(in)){
            byte[] b=new byte[1024*1024]; int n;
            while((n=is.read(b))>0) os.write(b,0,n);
        }
        return in;
    }

    private double duration(File f){
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try{m.setDataSource(f.getAbsolutePath());
            String x=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return x==null?180:Double.parseDouble(x)/1000.0;
        }catch(Exception e){return 180;} finally{m.release();}
    }

    private void startRemix(ProgressBar pb){
        if(selectedUri==null){ Toast.makeText(this,"בחר קודם שיר",Toast.LENGTH_SHORT).show(); return; }
        chooseBtn.setEnabled(false); remixBtn.setEnabled(false); pb.setVisibility(View.VISIBLE);
        status.setText("מנתח את השיר ובונה דרופ…");
        new Thread(()->{
            try{
                File input=copyInput();
                double dur=Math.max(8,Math.min(duration(input),900));
                int e=energy.getProgress();
                double level=0.55 + e/250.0;
                File out=new File(getCacheDir(),"DJ_Turbo_Remix.mp3");
                String in=q(input.getAbsolutePath()), o=q(out.getAbsolutePath());
                double beat=60.0/bpm;
                // Smart DJ engine: preserves the original track while adding a four-on-the-floor
                // kick, sub pulse, hi-hat texture, stereo widening and controlled loudness.
                String kick="aevalsrc="+fmt(level*0.72)+"*sin(2*PI*58*t)*exp(-32*mod(t,"+fmt(beat)+")):s=44100:d="+fmt(dur);
                String sub="aevalsrc="+fmt(level*0.20)+"*sin(2*PI*92*t)*exp(-11*mod(t,"+fmt(beat)+")):s=44100:d="+fmt(dur);
                String hats="anoisesrc=color=white:amplitude="+fmt(level*0.045)+":sample_rate=44100:d="+fmt(dur)+",highpass=f=6500,volume='0.35+0.65*lt(mod(t,"+fmt(beat/2)+"),0.08)'";
                String filter="[0:a]aresample=44100,highpass=f=32,bass=g=6:f=92,treble=g=4:f=9000,acompressor=threshold=-18dB:ratio=3:attack=8:release=90,stereotools=mlev=1.18[m];"
                    +"["+kick+"] [k];["+sub+"] [s];["+hats+"] [h];"
                    +"[m][k][s][h]amix=inputs=4:duration=first:dropout_transition=0,alimiter=limit=0.96";
                String cmd="-y -i "+in+" -filter_complex "+q(filter)+" -map 0:a? -c:a libmp3lame -b:a 256k -ar 44100 -ac 2 "+o;
                FFmpegKit.executeAsync(cmd,session->{
                    boolean ok=ReturnCode.isSuccess(session.getReturnCode());
                    runOnUiThread(()->{
                        pb.setVisibility(View.GONE); chooseBtn.setEnabled(true); remixBtn.setEnabled(true);
                        if(ok){
                            status.setText("🔥 ה־DJ Remix מוכן! 256kbps MP3");
                            saveAndShare(out);
                        }else{
                            status.setText("העיבוד נכשל. נסה שיר אחר.");
                            Toast.makeText(this,"FFmpeg: "+session.getFailStackTrace(),Toast.LENGTH_LONG).show();
                        }
                    });
                });
            }catch(Exception ex){
                runOnUiThread(()->{pb.setVisibility(View.GONE);chooseBtn.setEnabled(true);remixBtn.setEnabled(true);
                    status.setText("אירעה שגיאה בעיבוד"); Toast.makeText(this,ex.toString(),Toast.LENGTH_LONG).show();});
            }
        }).start();
    }

    private String fmt(double x){ return String.format(Locale.US,"%.6f",x); }
    private String q(String s){ return "'"+s.replace("'","'\\''")+"'"; }

    private void saveAndShare(File file){
        try{
            Uri uri;
            if(Build.VERSION.SDK_INT>=29){
                ContentValues v=new ContentValues();
                v.put(MediaStore.Audio.Media.DISPLAY_NAME,"DJ_Turbo_Remix_"+System.currentTimeMillis()+".mp3");
                v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mpeg");
                v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/DJ Turbo Remix");
                v.put(MediaStore.Audio.Media.IS_PENDING,1);
                uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);
                try(OutputStream os=getContentResolver().openOutputStream(uri);InputStream is=new FileInputStream(file)){
                    byte[] b=new byte[1024*1024];int n;while((n=is.read(b))>0)os.write(b,0,n);
                }
                v.clear();v.put(MediaStore.Audio.Media.IS_PENDING,0);getContentResolver().update(uri,v,null,null);
            }else{
                File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"DJ Turbo Remix");
                dir.mkdirs(); File dest=new File(dir,"DJ_Turbo_Remix_"+System.currentTimeMillis()+".mp3");
                copy(file,dest); uri=Uri.fromFile(dest);
            }
            Intent share=new Intent(Intent.ACTION_SEND); share.setType("audio/mpeg"); share.putExtra(Intent.EXTRA_STREAM,uri);
            startActivity(Intent.createChooser(share,"השמע / שתף את ה־DJ Remix"));
        }catch(Exception e){Toast.makeText(this,"נוצר הקובץ אבל השמירה נכשלה: "+e,Toast.LENGTH_LONG).show();}
    }

    private void copy(File a,File b)throws Exception{
        try(InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b)){
            byte[] x=new byte[1024*1024];int n;while((n=i.read(x))>0)o.write(x,0,n);
        }
    }
}
