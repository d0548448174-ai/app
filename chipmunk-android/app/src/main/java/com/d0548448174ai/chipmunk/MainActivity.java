package com.d0548448174ai.chipmunk;

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

import com.naman14.androidlame.AndroidLame;
import com.naman14.androidlame.LameBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd.Parameters;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.resample.RateTransposer;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 10;
    private static final int SAVE_WAV = 11;
    private static final int SAVE_MP3 = 12;

    private Uri inputUri;
    private File resultWav;
    private File resultMp3;

    private TextView fileLabel;
    private TextView factorLabel;
    private TextView status;
    private ProgressBar progress;
    private Button makeButton;
    private Button wavButton;
    private Button mp3Button;
    private SeekBar factorBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        TextView title = text("קול צ'יפמאנק", 31, Color.rgb(24,24,29), true);
        title.setPadding(0, dp(17), 0, 0);
        root.addView(title);

        TextView subtitle = text(
                "השיר מואט בלי להוריד את גובה הצליל, ואז חוזר למהירות רגילה עם קול גבוה.",
                15, Color.rgb(105,105,117), false);
        subtitle.setPadding(0, dp(7), 0, dp(16));
        root.addView(subtitle, wrap());

        LinearLayout choose = card();
        choose.addView(text("1  •  בחר שיר", 18, Color.DKGRAY, true));
        fileLabel = text("לא נבחר קובץ", 13, Color.GRAY, false);
        fileLabel.setPadding(0, dp(6), 0, dp(12));
        choose.addView(fileLabel);

        Button pick = button("🎵  בחירת שיר");
        pick.setOnClickListener(v -> pickAudio());
        choose.addView(pick);
        root.addView(choose, match(0, 0, 0, 10));

        LinearLayout effect = card();
        effect.addView(text("2  •  עוצמת האפקט", 18, Color.DKGRAY, true));

        factorLabel = text("", 16, Color.rgb(109,93,253), true);
        factorLabel.setGravity(Gravity.CENTER);
        factorLabel.setPadding(0, dp(9), 0, dp(4));
        effect.addView(factorLabel);

        factorBar = new SeekBar(this);
        factorBar.setMax(3);
        factorBar.setProgress(2);
        factorBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                updateFactorLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        effect.addView(factorBar, match(0, 5, 0, 0));
        effect.addView(text(
                "¼ מהירות • ⅓ • ½  • ½ = אוקטבה מעל",
                12, Color.GRAY, false));
        root.addView(effect, match(0, 0, 0, 12));

        TextView recipe = text(
                "🐿️ שלב 1: האטה  ×0.5  →  שלב 2: העלאת מהירות בחזרה  →  הקול נשאר גבוה",
                13, Color.rgb(83,83,94), false);
        recipe.setPadding(dp(13), dp(11), dp(13), dp(11));
        recipe.setBackground(bg(Color.rgb(247,247,250), 18));
        root.addView(recipe, match(0, 0, 0, 14));

        makeButton = button("✨  צור קול צ'יפמאנק");
        makeButton.setTextSize(17);
        makeButton.setEnabled(false);
        makeButton.setAlpha(.45f);
        makeButton.setOnClickListener(v -> makeChipmunk());
        root.addView(makeButton, match(0, 0, 0, 10));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(30), dp(30));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);

        status = text("", 13, Color.rgb(109,93,253), true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, wrap());

        LinearLayout exports = new LinearLayout(this);
        exports.setOrientation(LinearLayout.HORIZONTAL);
        exports.setGravity(Gravity.CENTER);
        exports.setWeightSum(2f);

        wavButton = button("💾  שמור WAV");
        wavButton.setVisibility(View.GONE);
        wavButton.setOnClickListener(v -> saveResult(true));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMargins(0, dp(10), dp(5), 0);
        exports.addView(wavButton, p1);

        mp3Button = button("🎵  שמור MP3");
        mp3Button.setVisibility(View.GONE);
        mp3Button.setOnClickListener(v -> saveResult(false));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.setMargins(dp(5), dp(10), 0, 0);
        exports.addView(mp3Button, p2);

        root.addView(exports, match(0, 0, 0, 0));

        updateFactorLabel();
        setContentView(root);
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == PICK_AUDIO) {
            inputUri = data.getData();
            fileLabel.setText(fileName(inputUri));
            resultWav = null;
            resultMp3 = null;
            wavButton.setVisibility(View.GONE);
            mp3Button.setVisibility(View.GONE);
            status.setText("");
            makeButton.setEnabled(true);
            makeButton.setAlpha(1f);
        } else if (requestCode == SAVE_WAV) {
            copyFileToUri(resultWav, data.getData(), "WAV");
        } else if (requestCode == SAVE_MP3) {
            copyFileToUri(resultMp3, data.getData(), "MP3");
        }
    }

    private double selectedFactor() {
        int p = factorBar == null ? 2 : factorBar.getProgress();
        if (p == 0) return 0.25;
        if (p == 1) return 1.0 / 3.0;
        if (p == 3) return 0.40;
        return 0.50;
    }

    private void updateFactorLabel() {
        double factor = selectedFactor();
        double semitones = -12.0 * (Math.log(factor) / Math.log(2.0));
        String s = factor == 0.5
                ? "½ מהירות → אוקטבה מעל"
                : String.format(Locale.US, "×%.2f → בערך +%.1f חצאי־טונים", factor, semitones);
        if (factorLabel != null) factorLabel.setText(s);
    }

    private void makeChipmunk() {
        if (inputUri == null) return;

        busy(true, "מפענח ומעבד…");

        final double factor = selectedFactor();
        executor.execute(() -> {
            File leftRaw = null;
            File rightRaw = null;
            File leftOut = null;
            File rightOut = null;

            try {
                long stamp = System.currentTimeMillis();
                leftRaw = new File(getCacheDir(), "left_" + stamp + ".pcm");
                rightRaw = new File(getCacheDir(), "right_" + stamp + ".pcm");
                decodeToStereoPcm(inputUri, leftRaw, rightRaw);

                leftOut = new File(getCacheDir(), "left_fx_" + stamp + ".pcm");
                rightOut = new File(getCacheDir(), "right_fx_" + stamp + ".pcm");

                runPitchRecipe(leftRaw, leftOut, factor);
                runPitchRecipe(rightRaw, rightOut, factor);

                resultWav = new File(getCacheDir(), "chipmunk_" + stamp + ".wav");
                resultMp3 = new File(getCacheDir(), "chipmunk_" + stamp + ".mp3");
                combineStereoAndEncode(leftOut, rightOut, resultWav, resultMp3);

                deleteQuiet(leftRaw);
                deleteQuiet(rightRaw);
                deleteQuiet(leftOut);
                deleteQuiet(rightOut);

                runOnUiThread(() -> {
                    busy(false, "✅ מוכן! קודם האטתי, ואז החזרתי למהירות — עם קול גבוה.");
                    wavButton.setVisibility(View.VISIBLE);
                    mp3Button.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                e.printStackTrace();
                deleteQuiet(leftRaw);
                deleteQuiet(rightRaw);
                deleteQuiet(leftOut);
                deleteQuiet(rightOut);
                deleteQuiet(resultWav);
                deleteQuiet(resultMp3);

                runOnUiThread(() -> busy(false, "❌ לא הצלחתי לעבד את הקובץ הזה."));
            }
        });
    }

    private void decodeToStereoPcm(Uri uri, File leftFile, File rightFile) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, uri, null);

        int track = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                track = i;
                break;
            }
        }
        if (track < 0) throw new IOException("No audio track");

        extractor.selectTrack(track);
        MediaFormat format = extractor.getTrackFormat(track);
        String mime = format.getString(MediaFormat.KEY_MIME);

        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

        channels = Math.max(1, Math.min(2, channels));

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        try (BufferedOutputStream left = new BufferedOutputStream(new FileOutputStream(leftFile));
             BufferedOutputStream right = new BufferedOutputStream(new FileOutputStream(rightFile))) {

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer in = codec.getInputBuffer(inIndex);
                        if (in == null) throw new IOException("No input buffer");

                        int size = extractor.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(
                                    inIndex, 0, size, Math.max(0, extractor.getSampleTime()), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;

                if (outIndex >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(outIndex);

                    if (out != null && info.size > 0) {
                        out.position(Math.max(info.offset, out.position()));
                        out.limit(Math.min(info.offset + info.size, out.limit()));
                        out = out.slice().order(ByteOrder.LITTLE_ENDIAN);

                        while (out.remaining() >= channels * 2) {
                            short l = out.getShort();
                            short r = channels == 2 ? out.getShort() : l;

                            left.write(l & 255);
                            left.write((l >>> 8) & 255);

                            right.write(r & 255);
                            right.write((r >>> 8) & 255);
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }
        } finally {
            codec.stop();
            codec.release();
            extractor.release();
        }
    }

    private void runPitchRecipe(File inputRaw, File outputRaw, double factor) throws Exception {
        long bytes = inputRaw.length();
        int sampleRate = 44100;
        int frameSize = 2;
        long frames = bytes / frameSize;

        TarsosDSPAudioFormat format =
                new TarsosDSPAudioFormat((float) sampleRate, 16, 1, true, false);

        try (InputStream raw = new BufferedInputStream(new FileInputStream(inputRaw));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputRaw))) {

            UniversalAudioInputStream stream = new UniversalAudioInputStream(raw, format);

            WaveformSimilarityBasedOverlapAdd wsola =
                    new WaveformSimilarityBasedOverlapAdd(
                            Parameters.musicDefaults(factor, sampleRate));

            RateTransposer rateTransposer = new RateTransposer(factor);

            PcmWriter writer = new PcmWriter(out);

            int inputBuffer = wsola.getInputBufferSize();
            int overlap = wsola.getOverlap();

            AudioDispatcher dispatcher =
                    new AudioDispatcher(stream, inputBuffer, overlap);

            wsola.setDispatcher(dispatcher);

            dispatcher.addAudioProcessor(wsola);
            dispatcher.addAudioProcessor(rateTransposer);
            dispatcher.addAudioProcessor(writer);

            dispatcher.run();
            writer.flush();

            stream.close();
        }

        // The expected amount is close to the original length because
        // WSOLA stretches by 1/factor and rate transposition compresses it by factor.
        if (outputRaw.length() == 0 || frames == 0) {
            throw new IOException("No processed audio");
        }
    }

    private static class PcmWriter implements AudioProcessor {
        private final BufferedOutputStream out;
        private final byte[] scratch = new byte[65536];

        PcmWriter(BufferedOutputStream out) {
            this.out = out;
        }

        @Override
        public boolean process(AudioEvent event) {
            float[] buffer = event.getFloatBuffer();
            int bytes = Math.min(scratch.length, buffer.length * 2);
            int samples = bytes / 2;

            int p = 0;
            for (int i = 0; i < samples; i++) {
                float v = Math.max(-1f, Math.min(1f, buffer[i]));
                short s = (short) Math.round(v * 32767f);
                scratch[p++] = (byte) (s & 255);
                scratch[p++] = (byte) ((s >>> 8) & 255);
            }

            try {
                out.write(scratch, 0, p);
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void flush() throws IOException {
            out.flush();
        }

        @Override public void processingFinished() {}
    }

    private void combineStereoAndEncode(File left, File right, File wav, File mp3) throws Exception {
        long frames = Math.min(left.length(), right.length()) / 2;
        if (frames <= 0) throw new IOException("Empty result");

        try (BufferedInputStream l = new BufferedInputStream(new FileInputStream(left));
             BufferedInputStream r = new BufferedInputStream(new FileInputStream(right));
             BufferedOutputStream w = new BufferedOutputStream(new FileOutputStream(wav));
             BufferedOutputStream m = new BufferedOutputStream(new FileOutputStream(mp3))) {

            writeWavHeader(w, 44100, 2, frames * 4);

            LameBuilder builder = new LameBuilder()
                    .setInSampleRate(44100)
                    .setOutChannels(2)
                    .setOutBitrate(192)
                    .setOutSampleRate(44100)
                    .setQuality(5);

            AndroidLame lame = builder.build();
            byte[] mp3Buf = new byte[65536];

            short[] pcm = new short[16384];
            int position = 0;

            while (position < pcm.length && frames > 0) {
                int sampleFrames = 0;

                while (sampleFrames * 2 < pcm.length && sampleFrames < frames) {
                    int ll = readLittleEndianShort(l);
                    int rr = readLittleEndianShort(r);
                    if (ll < 0 || rr < 0) break;

                    short ls = (short) ll;
                    short rs = (short) rr;

                    w.write(ls & 255);
                    w.write((ls >>> 8) & 255);
                    w.write(rs & 255);
                    w.write((rs >>> 8) & 255);

                    pcm[sampleFrames * 2] = ls;
                    pcm[sampleFrames * 2 + 1] = rs;

                    sampleFrames++;
                }

                if (sampleFrames == 0) break;

                int encoded = lame.encodeBufferInterleaved(pcm, sampleFrames, mp3Buf);
                if (encoded > 0) m.write(mp3Buf, 0, encoded);

                frames -= sampleFrames;
                position = 0;
            }

            int flushed = lame.lameFlush(mp3Buf);
            if (flushed > 0) m.write(mp3Buf, 0, flushed);
        }

        patchWavSize(wav);
    }

    private int readLittleEndianShort(InputStream in) throws IOException {
        int lo = in.read();
        int hi = in.read();
        if (lo < 0 || hi < 0) return -1;
        return (hi << 8) | lo;
    }

    private void writeWavHeader(OutputStream out, int sampleRate, int channels, long dataSize)
            throws IOException {
        byte[] h = new byte[44];
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        putLe32(h, 16, 16);
        putLe16(h, 20, 1);
        putLe16(h, 22, channels);
        putLe32(h, 24, sampleRate);
        putLe32(h, 28, sampleRate * channels * 2L);
        putLe16(h, 32, channels * 2);
        putLe16(h, 34, 16);
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        putLe32(h, 40, dataSize);
        putLe32(h, 4, 36 + dataSize);
        out.write(h);
    }

    private void patchWavSize(File wav) throws IOException {
        long dataSize = wav.length() - 44;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(wav, "rw")) {
            raf.seek(4);
            writeLe32(raf, 36 + dataSize);
            raf.seek(40);
            writeLe32(raf, dataSize);
        }
    }

    private void copyFileToUri(File source, Uri dest, String kind) {
        if (source == null || dest == null) return;

        executor.execute(() -> {
            try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                 OutputStream out = getContentResolver().openOutputStream(dest)) {

                if (out == null) throw new IOException("No output stream");

                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                out.flush();

                runOnUiThread(() -> status.setText("✅ " + kind + " נשמר בהצלחה."));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> status.setText("❌ השמירה נכשלה."));
            }
        });
    }

    private void saveResult(boolean wav) {
        File source = wav ? resultWav : resultMp3;
        if (source == null) return;

        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);

        if (wav) {
            i.setType("audio/wav");
            i.putExtra(Intent.EXTRA_TITLE, "chipmunk_song.wav");
            startActivityForResult(i, SAVE_WAV);
        } else {
            i.setType("audio/mpeg");
            i.putExtra(Intent.EXTRA_TITLE, "chipmunk_song.mp3");
            startActivityForResult(i, SAVE_MP3);
        }
    }

    private void busy(boolean b, String message) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        status.setText(message);

        makeButton.setEnabled(!b && inputUri != null);
        makeButton.setAlpha(!b && inputUri != null ? 1f : .45f);

        int visibility = !b && resultWav != null ? View.VISIBLE : View.GONE;
        wavButton.setVisibility(visibility);
        mp3Button.setVisibility(visibility);
    }

    private String fileName(Uri uri) {
        String p = uri.getLastPathSegment();
        return p == null ? "שיר" : p.replaceFirst("^.*/", "");
    }

    private static void putLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 255);
        data[offset + 1] = (byte) ((value >>> 8) & 255);
    }

    private static void putLe32(byte[] data, int offset, long value) {
        for (int i = 0; i < 4; i++) {
            data[offset + i] = (byte) ((value >>> (8 * i)) & 255);
        }
    }

    private static void writeLe32(java.io.RandomAccessFile raf, long value) throws IOException {
        for (int i = 0; i < 4; i++) {
            raf.write((int) ((value >>> (8 * i)) & 255));
        }
    }

    private void deleteQuiet(File f) {
        if (f != null) {
            try { f.delete(); } catch (Exception ignored) {}
        }
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15), dp(15), dp(15), dp(15));
        l.setBackground(bg(Color.WHITE, 20));
        l.setElevation(dp(2));
        return l;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(44,44,50));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(13), dp(10), dp(13), dp(10));
        b.setBackground(bg(Color.rgb(247,247,250), 16));
        return b;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.rgb(232,232,238));
        return d;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
