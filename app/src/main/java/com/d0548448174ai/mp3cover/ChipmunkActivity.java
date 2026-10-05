package com.d0548448174ai.mp3cover;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.PitchShifter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

public class ChipmunkActivity extends Activity {
    private static final int PICK_AUDIO = 100;
    private static final int SAVE_WAV = 101;

    private Uri inputUri;
    private File preparedFile;
    private TextView selectedLabel, status, pitchLabel;
    private ProgressBar progress;
    private Button makeButton, saveButton;
    private SeekBar pitchBar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView badge = text("M36 • CHIPMUNK VOICE", 12, Color.rgb(109,93,253), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(bg(Color.rgb(238,234,254), 50));
        root.addView(badge, wrap());

        TextView title = text("קול צ'יפמאנק לשיר", 30, Color.rgb(24,24,29), true);
        title.setPadding(0, dp(16), 0, 0);
        root.addView(title);

        TextView sub = text("מעלה את גובה הצליל בלי להפוך את השיר למהיר יותר.", 15, Color.rgb(105,105,117), false);
        sub.setPadding(0, dp(7), 0, dp(16));
        root.addView(sub, wrap());

        LinearLayout card = card();
        card.addView(text("1  •  בחר שיר", 18, Color.DKGRAY, true));
        selectedLabel = text("לא נבחר קובץ", 13, Color.GRAY, false);
        selectedLabel.setPadding(0, dp(6), 0, dp(12));
        card.addView(selectedLabel);
        Button pick = button("🎵  בחירת שיר");
        pick.setOnClickListener(v -> pickAudio());
        card.addView(pick);
        root.addView(card, match(0,0,0,10));

        LinearLayout pitchCard = card();
        pitchCard.addView(text("2  •  גובה צ'יפמאנק", 18, Color.DKGRAY, true));
        pitchLabel = text("", 16, Color.rgb(109,93,253), true);
        pitchLabel.setGravity(Gravity.CENTER);
        pitchLabel.setPadding(0, dp(8), 0, dp(4));
        pitchCard.addView(pitchLabel);

        pitchBar = new SeekBar(this);
        pitchBar.setMax(16);
        pitchBar.setProgress(12);
        pitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { updatePitchLabel(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        pitchCard.addView(pitchBar, match(0,4,0,0));
        pitchCard.addView(text("0 = רגיל   •   12 = אוקטבה מעל   •   16 = גבוה מאוד", 12, Color.GRAY, false));
        root.addView(pitchCard, match(0,0,0,12));
        updatePitchLabel();

        TextView info = text("💡 Pitch Shift מעלה את הצליל אבל שומר על אורך השיר.", 13, Color.rgb(83,83,94), false);
        info.setPadding(dp(13),dp(11),dp(13),dp(11));
        info.setBackground(bg(Color.rgb(247,247,250),18));
        root.addView(info, match(0,0,0,14));

        makeButton = button("🐿️  צור קול צ'יפמאנק");
        makeButton.setTextSize(17);
        makeButton.setEnabled(false);
        makeButton.setAlpha(.45f);
        makeButton.setOnClickListener(v -> makeChipmunk());
        root.addView(makeButton, match(0,0,0,10));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(30),dp(30));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        status = text("", 13, Color.rgb(109,93,253), true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, wrap());

        saveButton = button("💾  שמור את התוצאה");
        saveButton.setVisibility(View.GONE);
        saveButton.setOnClickListener(v -> saveResult());
        root.addView(saveButton, match(0,dp(10),0,0));

        setContentView(root);
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_AUDIO);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req,res,data);
        if(res != RESULT_OK || data == null || data.getData() == null) return;

        if(req == PICK_AUDIO) {
            inputUri = data.getData();
            selectedLabel.setText(fileName(inputUri));
            makeButton.setEnabled(true);
            makeButton.setAlpha(1f);
            saveButton.setVisibility(View.GONE);
            preparedFile = null;
            status.setText("");
        } else if(req == SAVE_WAV) {
            saveResultTo(data.getData());
        }
    }

    private void updatePitchLabel() {
        int semitones = pitchBar == null ? 12 : pitchBar.getProgress();
        double factor = Math.pow(2.0, semitones / 12.0);
        String s = semitones == 0 ? "ללא שינוי" :
                String.format(Locale.US, "+%d חצאי־טונים  •  ×%.2f", semitones, factor);
        if(pitchLabel != null) pitchLabel.setText(s);
    }

    private void makeChipmunk() {
        if(inputUri == null) return;
        busy(true, "מעבד את השיר…");
        int semitones = pitchBar.getProgress();

        executor.execute(() -> {
            try {
                File out = new File(getCacheDir(), "chipmunk_" + System.currentTimeMillis() + ".wav");
                processAudio(inputUri, out, semitones);
                preparedFile = out;
                runOnUiThread(() -> busy(false, "✅ מוכן! הקול גבוה יותר והקצב נשמר."));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> busy(false, "❌ לא הצלחתי לעבד את הקובץ הזה."));
            }
        });
    }

    private void saveResult() {
        if(preparedFile == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("audio/wav");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, "chipmunk_song.wav");
        startActivityForResult(i, SAVE_WAV);
    }

    private void saveResultTo(Uri dest) {
        if(preparedFile == null || dest == null) return;
        File src = preparedFile;
        executor.execute(() -> {
            try (java.io.InputStream in = new java.io.FileInputStream(src);
                 java.io.OutputStream out = getContentResolver().openOutputStream(dest)) {
                if(out == null) throw new IllegalStateException("output");
                byte[] b = new byte[65536];
                int n;
                while((n = in.read(b)) != -1) out.write(b, 0, n);
                out.flush();
                src.delete();
                preparedFile = null;
                runOnUiThread(() -> {
                    status.setText("✅ נשמר בהצלחה.");
                    saveButton.setVisibility(View.GONE);
                });
            } catch(Exception e) {
                runOnUiThread(() -> status.setText("❌ השמירה נכשלה."));
            }
        });
    }

    private void processAudio(Uri uri, File out, int semitones) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, uri, null);

        int track = -1;
        for(int i=0; i<extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if(mime != null && mime.startsWith("audio/")) { track = i; break; }
        }
        if(track < 0) throw new IllegalStateException("No audio track");

        extractor.selectTrack(track);
        MediaFormat fmt = extractor.getTrackFormat(track);
        String mime = fmt.getString(MediaFormat.KEY_MIME);

        int sampleRate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
        channels = Math.max(1, Math.min(2, channels));

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(fmt, null, null, 0);
        codec.start();

        FileOutputStream wav = new FileOutputStream(out);
        writeWavHeader(wav, sampleRate, channels, 0);
        PitchStream stream = new PitchStream(sampleRate, channels, semitones, wav);

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;

        while(!outputDone) {
            if(!inputDone) {
                int in = codec.dequeueInputBuffer(10000);
                if(in >= 0) {
                    ByteBuffer buf = codec.getInputBuffer(in);
                    if(buf == null) throw new IllegalStateException("input buffer");
                    int n = extractor.readSampleData(buf, 0);

                    if(n < 0) {
                        codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        long pts = Math.max(0, extractor.getSampleTime());
                        codec.queueInputBuffer(in, 0, n, pts, 0);
                        extractor.advance();
                    }
                }
            }

            int oi = codec.dequeueOutputBuffer(info, 10000);

            if(oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat actual = codec.getOutputFormat();
                if(actual.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                    sampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if(actual.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                    channels = Math.max(1, Math.min(2, actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)));
                continue;
            }

            if(oi >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(oi);
                if(ob != null && info.size > 0) {
                    int start = Math.max(info.offset, ob.position());
                    int end = Math.min(info.offset + info.size, ob.limit());
                    if(end > start) {
                        ob.position(start);
                        ob.limit(end);
                        stream.accept(ob.slice());
                    }
                }

                codec.releaseOutputBuffer(oi, false);
                if((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    outputDone = true;
            }
        }

        stream.finish();
        codec.stop();
        codec.release();
        extractor.release();
        wav.close();
        patchWavSize(out);
    }

    private static class PitchStream {
        private final int step = 2048;
        private final int window = 4096;
        private final int overlap = 2048;
        private final int channels;
        private final short[][] pending;
        private int pendingCount;
        private final PitchShifter[] shifters;
        private final AudioEvent[] events;
        private final FileOutputStream wav;

        PitchStream(int sampleRate, int channels, int semitones, FileOutputStream wav) {
            this.channels = channels;
            this.wav = wav;
            pending = new short[channels][window];
            shifters = new PitchShifter[channels];
            events = new AudioEvent[channels];

            double factor = Math.pow(2.0, semitones / 12.0);
            TarsosDSPAudioFormat mono =
                    new TarsosDSPAudioFormat((float)sampleRate, 16, 1, true, false);

            for(int c=0; c<channels; c++) {
                shifters[c] = new PitchShifter(factor, sampleRate, window, overlap);
                events[c] = new AudioEvent(mono);
                events[c].setOverlap(overlap);
            }

            // A half-window of padding keeps the first processed block aligned with the start.
            pendingCount = overlap;
        }

        void accept(ByteBuffer data) throws Exception {
            while(data.remaining() >= channels * 2) {
                for(int c=0; c<channels; c++)
                    pending[c][pendingCount] = data.getShort();

                pendingCount++;

                if(pendingCount == window) {
                    processWindow(step);
                    for(int c=0; c<channels; c++)
                        System.arraycopy(pending[c], step, pending[c], 0, overlap);
                    pendingCount = overlap;
                }
            }
        }

        void finish() throws Exception {
            int remaining = pendingCount - overlap;
            if(remaining <= 0) return;

            while(pendingCount < window) {
                for(int c=0; c<channels; c++) pending[c][pendingCount] = 0;
                pendingCount++;
            }
            processWindow(remaining);
        }

        private void processWindow(int frames) throws Exception {
            float[][] outCh = new float[channels][frames];

            for(int c=0; c<channels; c++) {
                float[] buf = new float[window];
                for(int i=0; i<window; i++) buf[i] = pending[c][i] / 32768.0f;

                events[c].setFloatBuffer(buf);
                shifters[c].process(events[c]);

                float[] out = events[c].getFloatBuffer();
                for(int i=0; i<frames; i++) {
                    float v = out[window - step + i];
                    outCh[c][i] = Math.max(-0.999f, Math.min(0.999f, v));
                }
            }

            byte[] bytes = new byte[frames * channels * 2];
            int p = 0;

            for(int i=0; i<frames; i++) {
                for(int c=0; c<channels; c++) {
                    short s = (short)Math.round(outCh[c][i] * 32767f);
                    bytes[p++] = (byte)(s & 255);
                    bytes[p++] = (byte)((s >>> 8) & 255);
                }
            }

            wav.write(bytes);
        }
    }

    private void writeWavHeader(FileOutputStream out, int sampleRate, int channels, long dataLength) throws Exception {
        byte[] h = new byte[44];
        h[0]='R'; h[1]='I'; h[2]='F'; h[3]='F';
        h[8]='W'; h[9]='A'; h[10]='V'; h[11]='E';
        h[12]='f'; h[13]='m'; h[14]='t'; h[15]=' ';
        le32(h,16,16);
        le16(h,20,(short)1);
        le16(h,22,(short)channels);
        le32(h,24,sampleRate);
        le32(h,28,sampleRate * channels * 2L);
        le16(h,32,(short)(channels * 2));
        le16(h,34,(short)16);
        h[36]='d'; h[37]='a'; h[38]='t'; h[39]='a';
        le32(h,40,dataLength);
        le32(h,4,36 + dataLength);
        out.write(h);
    }

    private static void patchWavSize(File f) throws Exception {
        long data = f.length() - 44;
        try(RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(4);
            writeLE32(raf, 36 + data);
            raf.seek(40);
            writeLE32(raf, data);
        }
    }

    private static void le16(byte[] a, int p, short v) {
        a[p]=(byte)(v & 255);
        a[p+1]=(byte)((v >>> 8) & 255);
    }

    private static void le32(byte[] a, int p, long v) {
        for(int i=0; i<4; i++) a[p+i]=(byte)((v >>> (8*i)) & 255);
    }

    private static void writeLE32(RandomAccessFile r, long v) throws Exception {
        for(int i=0; i<4; i++) r.write((int)((v >>> (8*i)) & 255));
    }

    private void busy(boolean b, String s) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        status.setText(s);
        makeButton.setEnabled(!b && inputUri != null);
        makeButton.setAlpha(!b && inputUri != null ? 1f : .45f);
        saveButton.setVisibility(!b && preparedFile != null ? View.VISIBLE : View.GONE);
    }

    private String fileName(Uri u) {
        String p = u.getLastPathSegment();
        return p == null ? "קובץ שמע" : p.replaceFirst("^.*/", "");
    }

    private TextView text(String s,float z,int c,boolean bold) {
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(z);
        t.setTextColor(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15),dp(15),dp(15),dp(15));
        l.setBackground(bg(Color.WHITE,20));
        l.setElevation(dp(2));
        return l;
    }

    private Button button(String s) {
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(44,44,50));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(13),dp(10),dp(13),dp(10));
        b.setBackground(bg(Color.rgb(247,247,250),16));
        return b;
    }

    private GradientDrawable bg(int c,int r) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(dp(r));
        d.setStroke(dp(1),Color.rgb(232,232,238));
        return d;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match(int l,int t,int r,int b) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l),dp(t),dp(r),dp(b));
        return p;
    }

    private int dp(int n) {
        return Math.round(getResources().getDisplayMetrics().density*n);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
