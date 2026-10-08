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
import java.util.ArrayList;
import java.util.Collections;
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
        try{
            m.setDataSource(f.getAbsolutePath());
            String x=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return x==null?180:Double.parseDouble(x)/1000.0;
        }catch(Exception e){
            return 180;
        } finally {
            try{ m.release(); }catch(Exception ignored){}
        }
    }

    private static class Analysis {
        double bpm, offset, beat;
        double[] beats, bars, barEnergy;
        Analysis(double bpm,double offset,double beat,double[] beats,double[] bars,double[] barEnergy){
            this.bpm=bpm; this.offset=offset; this.beat=beat; this.beats=beats; this.bars=bars; this.barEnergy=barEnergy;
        }
    }

    private Analysis analyzeAudio(File input,double dur)throws Exception{
        File pcm=new File(getCacheDir(),"analysis_pcm.raw");
        String decode="-y -i "+q(input.getAbsolutePath())+" -vn -ac 1 -ar 22050 -f s16le "+q(pcm.getAbsolutePath());
        FFmpegKit.execute(decode);
        if(!pcm.exists()||pcm.length()<22050*2)throw new IOException("לא ניתן לנתח את האודיו");
        final int sr=22050, win=1024, hop=512;
        ArrayList<Double> env=new ArrayList<>();
        double prev=0;
        try(InputStream is=new BufferedInputStream(new FileInputStream(pcm),1024*1024)){
            byte[] block=new byte[win*2], half=new byte[hop*2];
            int filled=0;
            while(true){
                int n=is.read(half); if(n<2)break;
                int copy=Math.min(n,block.length-filled);
                System.arraycopy(half,0,block,filled,copy); filled+=copy;
                if(filled<block.length)continue;
                double sum=0;
                for(int i=0;i<win;i++){int lo=block[i*2]&255,hi=block[i*2+1];short s=(short)((hi<<8)|lo);double v=s/32768.0;sum+=v*v;}
                double rms=Math.sqrt(sum/win);
                env.add(Math.max(0,rms-prev)); prev=rms;
                System.arraycopy(block,hop*2,block,0,hop*2); filled=hop*2;
            }
        }
        int n=env.size(); if(n<100)throw new IOException("השיר קצר מדי לניתוח");
        double[] e=new double[n]; double mean=0;
        for(int i=0;i<n;i++){e[i]=env.get(i);mean+=e[i];}
        mean/=n;
        for(int i=0;i<n;i++)e[i]=Math.max(0,e[i]-mean*.35);
        double step=hop/(double)sr;
        int minLag=(int)(60.0/180.0/step), maxLag=(int)(60.0/70.0/step);
        double best=-1; int bestLag=minLag;
        for(int lag=minLag;lag<=maxLag;lag++){
            double ab=0,aa=0,bb=0;
            for(int i=lag;i<n;i++){aa+=e[i]*e[i];bb+=e[i-lag]*e[i-lag];ab+=e[i]*e[i-lag];}
            double corr=ab/Math.sqrt(Math.max(1e-12,aa*bb));
            double testBpm=60.0/(lag*step);
            if(testBpm>=100&&testBpm<=150)corr*=1.08;
            if(corr>best){best=corr;bestLag=lag;}
        }
        double bpm=Math.max(70,Math.min(180,60.0/(bestLag*step)));
        int search=Math.min(n,(int)(8.0/step)); int first=0; double peak=-1;
        for(int i=0;i<search;i++)if(e[i]>peak){peak=e[i];first=i;}
        double beat=60.0/bpm, offset=first*step;
        double[] beats=makeBeats(offset,beat,dur);
        double[] bars=makeBars(offset,beat,dur);
        double[] barEnergy=new double[Math.max(1,bars.length-1)];
        double max=0;
        for(int i=0;i<barEnergy.length;i++){
            int a=(int)Math.max(0,bars[i]/step), z=(int)Math.min(n,bars[i+1<bars.length?i+1:n-1]/step);
            double s=0;int count=0;for(int j=a;j<z;j++){s+=e[j];count++;}
            barEnergy[i]=count==0?0:s/count;max=Math.max(max,barEnergy[i]);
        }
        if(max>0)for(int i=0;i<barEnergy.length;i++)barEnergy[i]/=max;
        return new Analysis(bpm,offset,beat,beats,bars,barEnergy);
    }

    private String sectionName(int i,int total,double[] en){
        if(i==0)return "INTRO";
        double cur=en[Math.min(i,en.length-1)], prev=en[Math.max(0,i-1)];
        if(cur<.25)return "BREAK";
        if(cur>prev*1.30)return "BUILDUP";
        if(cur>.62)return "CHORUS";
        if(i>=total-2&&cur>.55)return "DROP";
        return "VERSE";
    }

    private String effectGate(Analysis a,double dur,double level){
        String x="0"; int total=a.bars.length-1;
        for(int i=total-1;i>=0;i--){
            double s=a.bars[i], e=a.bars[i+1]; String type=sectionName(i,total,a.barEnergy);
            double g=type.equals("INTRO")?.10:type.equals("BREAK")?.04:type.equals("BUILDUP")?.45:type.equals("CHORUS")?.85:type.equals("DROP")?1.0:.55;
            if(type.equals("BUILDUP")) x="if(between(t,"+fmt(s)+","+fmt(e)+"),"+fmt(g*level)+"*(t-"+fmt(s)+")/"+fmt(Math.max(.01,e-s))+","+x+")";
            else x="if(between(t,"+fmt(s)+","+fmt(e)+"),"+fmt(g*level)+","+x+")";
        }
        return x;
    }

    private String riserGate(Analysis a,double level){
        String x="0"; int total=a.bars.length-1;
        for(int i=total-1;i>=0;i--)if(sectionName(i,total,a.barEnergy).equals("BUILDUP")){
            double s=a.bars[i],e=a.bars[i+1];
            x="if(between(t,"+fmt(s)+","+fmt(e)+"),"+fmt(level*.035)+"*(t-"+fmt(s)+")/"+fmt(Math.max(.01,e-s))+",0)";
        }
        return x;
    }

    private double[] makeBeats(double offset,double beat,double dur){
        ArrayList<Double>x=new ArrayList<>();double t=offset;while(t<dur){if(t>=0)x.add(t);t+=beat;}
        double[]a=new double[x.size()];for(int i=0;i<a.length;i++)a[i]=x.get(i);return a;
    }

    private double[] makeBars(double offset,double beat,double dur){
        ArrayList<Double>x=new ArrayList<>();double bar=beat*4,t=offset;while(t>0)t-=bar;x.add(0.0);
        while(t<dur){if(t>0)x.add(t);t+=bar;}x.add(dur);Collections.sort(x);
        ArrayList<Double>y=new ArrayList<>();double last=-1;for(double v:x)if(v-last>.05){y.add(Math.max(0,Math.min(dur,v)));last=v;}
        double[]a=new double[y.size()];for(int i=0;i<a.length;i++)a[i]=y.get(i);return a;
    }

    private void startRemix(ProgressBar pb){
        if(selectedUri==null){Toast.makeText(this,"בחר קודם שיר",Toast.LENGTH_SHORT).show();return;}
        chooseBtn.setEnabled(false);remixBtn.setEnabled(false);pb.setVisibility(View.VISIBLE);
        status.setText("🔬 מזהה BPM, ביטים ו-Onsets…");
        new Thread(()->{
            try{
                File input=copyInput(); double dur=Math.max(8,Math.min(duration(input),900));
                double level=.55+energy.getProgress()/250.0;
                Analysis a=analyzeAudio(input,dur);
                double finalBpm=bpm<=0?a.bpm:bpm; double beat=60.0/finalBpm;
                Analysis analysis=new Analysis(finalBpm,a.offset,beat,makeBeats(a.offset,beat,dur),makeBars(a.offset,beat,dur),a.barEnergy);
                runOnUiThread(()->status.setText(String.format(Locale.US,"✓ BPM %.1f | %d ביטים | %d תיבות",finalBpm,analysis.beats.length,analysis.bars.length-1)));
                a=analysis;
                File out=new File(getCacheDir(),"DJ_Turbo_Remix.mp3");
                String in=q(input.getAbsolutePath()),o=q(out.getAbsolutePath()),gate=effectGate(a,dur,level),rise=riserGate(a,level);
                String phase="mod(t-"+fmt(a.offset)+","+fmt(beat)+")";
                String kick="aevalsrc="+fmt(level*.72)+"*sin(2*PI*58*t)*exp(-32*"+phase+"):s=44100:d="+fmt(dur);
                String sub="aevalsrc="+fmt(level*.20)+"*sin(2*PI*92*t)*exp(-11*"+phase+"):s=44100:d="+fmt(dur);
                String hats="anoisesrc=color=white:amplitude="+fmt(level*.045)+":sample_rate=44100:d="+fmt(dur)+",highpass=f=6500,volume='0.35+0.65*lt(mod(t-"+fmt(a.offset)+","+fmt(beat/2)+"),0.08)'";
                String riser="anoisesrc=color=white:amplitude=1:sample_rate=44100:d="+fmt(dur)+",highpass=f=3500,volume='"+rise+"'";
                String filter="[0:a]aresample=44100,highpass=f=32,bass=g=6:f=92,treble=g=4:f=9000,acompressor=threshold=-18dB:ratio=3:attack=8:release=90,stereotools=mlev=1.18[m];"
                    +"["+kick+"] [k0];[k0]volume='"+gate+"'[k];["+sub+"] [s0];[s0]volume='"+gate+"'[s];["+hats+"] [h];["+riser+"] [r];"
                    +"[m][k][s][h][r]amix=inputs=5:duration=first:dropout_transition=0,alimiter=limit=0.96[out]";
                String cmd="-y -i "+in+" -filter_complex "+q(filter)+" -map [out] -c:a libmp3lame -b:a 256k -ar 44100 -ac 2 "+o;
                FFmpegKit.executeAsync(cmd,session->{
                    boolean ok=ReturnCode.isSuccess(session.getReturnCode());
                    runOnUiThread(()->{pb.setVisibility(View.GONE);chooseBtn.setEnabled(true);remixBtn.setEnabled(true);
                        if(ok){status.setText(String.format(Locale.US,"🔥 מוכן! BPM %.1f — Build/Drop נעולים לתיבות",finalBpm));saveAndShare(out);}
                        else{status.setText("העיבוד נכשל. נסה שיר אחר.");Toast.makeText(this,"FFmpeg: "+session.getFailStackTrace(),Toast.LENGTH_LONG).show();}
                    });
                });
            }catch(Exception ex){runOnUiThread(()->{pb.setVisibility(View.GONE);chooseBtn.setEnabled(true);remixBtn.setEnabled(true);status.setText("אירעה שגיאה בעיבוד");Toast.makeText(this,ex.toString(),Toast.LENGTH_LONG).show();});}
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
