package com.example.chipmunkvoice;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AudioProcessor {
    public interface ProgressListener {
        void onProgress(int progress, String message);
    }

    private AudioProcessor() {}

    public static File convert(Context context, Uri source, float pitchFactor, ProgressListener listener) throws Exception {
        if (pitchFactor < 1.05f || pitchFactor > 2.0f) {
            throw new Exception("גובה הציפמאנק חייב להיות בין 105% ל-200%.");
        }

        listener.onProgress(2, "פותח את השיר...");
        DecodeInfo info = decodeToPcm(context, source, listener);

        File output = new File(
            context.getCacheDir(),
            "chipmunk_" + System.currentTimeMillis() + ".wav"
        );

        listener.onProgress(62, "מייצר קול ציפמאנק...");
        resampleToWav(info.pcmFile, output, info.sampleRate, info.channels, pitchFactor, listener);

        try { info.pcmFile.delete(); } catch (Exception ignored) {}
        listener.onProgress(100, "סיימנו! הקול קיבל אפקט ציפמאנק 🐿️");
        return output;
    }

    private static DecodeInfo decodeToPcm(Context context, Uri source, ProgressListener listener) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        ParcelFileDescriptor pfd = null;
        MediaCodec decoder = null;
        File raw = File.createTempFile("chipmunk_decode_", ".pcm", context.getCacheDir());

        try {
            pfd = context.getContentResolver().openFileDescriptor(source, "r");
            if (pfd == null) throw new Exception("לא הצלחתי לפתוח את קובץ השיר.");

            long length = pfd.getStatSize();
            if (length <= 0) length = Long.MAX_VALUE;
            extractor.setDataSource(pfd.getFileDescriptor(), 0, length);

            int trackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    format = f;
                    break;
                }
            }

            if (trackIndex < 0 || format == null) {
                throw new Exception("לא נמצא ערוץ אודיו בקובץ.");
            }

            extractor.selectTrack(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION) : -1;

            try {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            } catch (Exception ignored) {}

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            FileOutputStream fos = new FileOutputStream(raw);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new Exception("לא ניתן לגשת למאגר הקלט.");

                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        long presentationTimeUs = extractor.getSampleTime();

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex, 0, sampleSize, Math.max(0, presentationTimeUs), 0
                            );
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = decoder.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                } else if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);

                    if (outputBuffer != null &&
                        bufferInfo.size > 0 &&
                        (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {

                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        byte[] pcm = new byte[outputBuffer.remaining()];
                        outputBuffer.get(pcm);
                        fos.write(pcm);
                    }

                    decoder.releaseOutputBuffer(outputIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }

                    if (durationUs > 0 && bufferInfo.presentationTimeUs > 0) {
                        int percent = 5 + (int) Math.min(55,
                            (bufferInfo.presentationTimeUs * 55L) / durationUs
                        );
                        listener.onProgress(percent, "ממיר את השיר...");
                    }
                }
            }

            fos.flush();
            fos.close();

            if (raw.length() < channels * 2L * 2L) {
                throw new Exception("הקובץ לא הכיל אודיו PCM שאפשר לעבד.");
            }

            listener.onProgress(60, "הפענוח הושלם.");
            return new DecodeInfo(raw, sampleRate, channels);
        } catch (Exception e) {
            try { raw.delete(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { if (decoder != null) decoder.stop(); } catch (Exception ignored) {}
            try { if (decoder != null) decoder.release(); } catch (Exception ignored) {}
            try { extractor.release(); } catch (Exception ignored) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        }
    }

    private static void resampleToWav(
        File pcmFile,
        File wavFile,
        int sampleRate,
        int channels,
        float factor,
        ProgressListener listener
    ) throws Exception {

        int bytesPerSample = 2;
        int frameBytes = channels * bytesPerSample;
        long totalBytes = pcmFile.length();
        long totalFrames = totalBytes / frameBytes;

        if (totalFrames < 2) throw new Exception("השיר קצר מדי לעיבוד.");

        long outputFrames = Math.max(1, (long) Math.ceil((totalFrames - 1) / factor));
        long outputBytes = outputFrames * frameBytes;

        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(pcmFile), 128 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(wavFile), 128 * 1024)) {

            writeWavHeader(out, sampleRate, channels, outputBytes);

            byte[] current = new byte[frameBytes];
            byte[] next = new byte[frameBytes];

            readFully(in, current);
            readFully(in, next);

            long currentIndex = 0;
            byte[] outBuffer = new byte[Math.max(frameBytes, 64 * 1024)];
            int outPos = 0;

            for (long o = 0; o < outputFrames; o++) {
                double inputPosition = o * (double) factor;
                long wantedIndex = (long) Math.floor(inputPosition);
                double fraction = inputPosition - wantedIndex;

                while (currentIndex < wantedIndex) {
                    System.arraycopy(next, 0, current, 0, frameBytes);
                    currentIndex++;
                    int read = readFullyOrEnd(in, next);
                    if (read < frameBytes) {
                        java.util.Arrays.fill(next, (byte) 0);
                    }
                }

                for (int ch = 0; ch < channels; ch++) {
                    int off = ch * 2;
                    short a = (short) ((current[off] & 0xFF) | (current[off + 1] << 8));
                    short b = (short) ((next[off] & 0xFF) | (next[off + 1] << 8));

                    int value = (int) Math.round(a + (b - a) * fraction);
                    if (value > 32767) value = 32767;
                    if (value < -32768) value = -32768;

                    outBuffer[outPos++] = (byte) (value & 0xFF);
                    outBuffer[outPos++] = (byte) ((value >> 8) & 0xFF);
                }

                if (outPos >= outBuffer.length - frameBytes) {
                    out.write(outBuffer, 0, outPos);
                    outPos = 0;
                }

                if (o % Math.max(1, outputFrames / 100) == 0) {
                    int progress = 62 + (int) Math.min(37, (o * 37L) / outputFrames);
                    listener.onProgress(progress, "מעלה את גובה הקול...");
                }
            }

            if (outPos > 0) out.write(outBuffer, 0, outPos);
            out.flush();
        }
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int done = 0;
        while (done < buffer.length) {
            int n = in.read(buffer, done, buffer.length - done);
            if (n < 0) throw new IOException("קובץ ה-PCM הסתיים מוקדם מדי.");
            done += n;
        }
    }

    private static int readFullyOrEnd(InputStream in, byte[] buffer) throws IOException {
        int done = 0;
        while (done < buffer.length) {
            int n = in.read(buffer, done, buffer.length - done);
            if (n < 0) return done;
            done += n;
        }
        return done;
    }

    private static void writeWavHeader(OutputStream out, int sampleRate, int channels, long dataSize) throws IOException {
        int byteRate = sampleRate * channels * 2;
        int blockAlign = channels * 2;

        out.write(new byte[] {'R','I','F','F'});
        writeIntLE(out, 36L + dataSize);
        out.write(new byte[] {'W','A','V','E'});
        out.write(new byte[] {'f','m','t',' '});
        writeIntLE(out, 16);
        writeShortLE(out, 1);
        writeShortLE(out, channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, blockAlign);
        writeShortLE(out, 16);
        out.write(new byte[] {'d','a','t','a'});
        writeIntLE(out, dataSize);
    }

    private static void writeIntLE(OutputStream out, long value) throws IOException {
        int v = (int) value;
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShortLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static final class DecodeInfo {
        final File pcmFile;
        final int sampleRate;
        final int channels;

        DecodeInfo(File pcmFile, int sampleRate, int channels) {
            this.pcmFile = pcmFile;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}
