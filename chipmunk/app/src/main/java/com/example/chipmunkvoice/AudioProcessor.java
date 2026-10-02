package com.example.chipmunkvoice;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class AudioProcessor {

    public interface ProgressListener {
        void onProgress(int progress, String message);
    }

    private AudioProcessor() {
    }

    public static File convert(
            Context context,
            Uri input,
            float factor,
            ProgressListener listener
    ) throws Exception {

        if (factor < 1.10f || factor > 2.00f) {
            throw new Exception("גובה האפקט אינו תקין.");
        }

        DecodeResult decoded = decode(context, input, listener);

        File output = new File(
                context.getCacheDir(),
                "chipmunk_" + System.currentTimeMillis() + ".wav"
        );

        try {
            resample(
                    decoded.pcm,
                    output,
                    decoded.sampleRate,
                    decoded.channels,
                    factor,
                    listener
            );
        } finally {
            if (decoded.pcm.exists()) {
                //noinspection ResultOfMethodCallIgnored
                decoded.pcm.delete();
            }
        }

        listener.onProgress(100, "✅ סיימנו! קול הציפמאנק מוכן.");
        return output;
    }

    private static DecodeResult decode(
            Context context,
            Uri source,
            ProgressListener listener
    ) throws Exception {

        listener.onProgress(3, "פותח את קובץ השיר...");

        MediaExtractor extractor = new MediaExtractor();
        ParcelFileDescriptor descriptor = null;
        MediaCodec decoder = null;
        File pcm = File.createTempFile("chipmunk_", ".pcm", context.getCacheDir());

        try {
            descriptor = context.getContentResolver().openFileDescriptor(source, "r");
            if (descriptor == null) {
                throw new Exception("לא הצלחתי לפתוח את קובץ השיר.");
            }

            extractor.setDataSource(descriptor.getFileDescriptor());

            int audioTrack = -1;
            MediaFormat sourceFormat = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    sourceFormat = format;
                    break;
                }
            }

            if (audioTrack < 0 || sourceFormat == null) {
                throw new Exception("לא נמצא ערוץ אודיו בקובץ.");
            }

            extractor.selectTrack(audioTrack);

            String mime = sourceFormat.getString(MediaFormat.KEY_MIME);
            int sampleRate = sourceFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    : 44100;
            int channels = sourceFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    : 2;

            long durationUs = sourceFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? sourceFormat.getLong(MediaFormat.KEY_DURATION)
                    : -1L;

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(sourceFormat, null, null, 0);
            decoder.start();

            try (FileOutputStream output = new FileOutputStream(pcm)) {

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputDone = false;
                boolean outputDone = false;

                while (!outputDone) {

                    if (!inputDone) {
                        int inputIndex = decoder.dequeueInputBuffer(10000);

                        if (inputIndex >= 0) {
                            ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);

                            if (inputBuffer == null) {
                                throw new Exception("לא ניתן לגשת למאגר הקלט.");
                            }

                            int size = extractor.readSampleData(inputBuffer, 0);
                            long presentationTime = extractor.getSampleTime();

                            if (size < 0) {
                                decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                );
                                inputDone = true;
                            } else {
                                decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        size,
                                        Math.max(0L, presentationTime),
                                        0
                                );
                                extractor.advance();
                            }
                        }
                    }

                    int outputIndex = decoder.dequeueOutputBuffer(info, 10000);

                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat outputFormat = decoder.getOutputFormat();

                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        }

                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        }
                    } else if (outputIndex >= 0) {

                        ByteBuffer buffer = decoder.getOutputBuffer(outputIndex);

                        if (buffer != null &&
                                info.size > 0 &&
                                (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {

                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);

                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);
                            output.write(data);
                        }

                        decoder.releaseOutputBuffer(outputIndex, false);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true;
                        }

                        if (durationUs > 0 && info.presentationTimeUs > 0) {
                            int progress = 5 + (int) Math.min(
                                    50L,
                                    (info.presentationTimeUs * 50L) / durationUs
                            );
                            listener.onProgress(progress, "מפענח את השיר...");
                        }
                    }
                }

                output.flush();
            }

            if (pcm.length() < channels * 4L) {
                throw new Exception("לא נמצא אודיו שניתן לעבד.");
            }

            listener.onProgress(58, "הפענוח הסתיים.");
            return new DecodeResult(pcm, sampleRate, channels);

        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            pcm.delete();
            throw e;
        } finally {
            try {
                if (decoder != null) {
                    decoder.stop();
                }
            } catch (Exception ignored) {
            }

            try {
                if (decoder != null) {
                    decoder.release();
                }
            } catch (Exception ignored) {
            }

            try {
                extractor.release();
            } catch (Exception ignored) {
            }

            try {
                if (descriptor != null) {
                    descriptor.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void resample(
            File input,
            File output,
            int sampleRate,
            int channels,
            float factor,
            ProgressListener listener
    ) throws Exception {

        int frameBytes = channels * 2;
        long frameCount = input.length() / frameBytes;

        if (frameCount < 2) {
            throw new Exception("השיר קצר מדי לעיבוד.");
        }

        long outputFrames = Math.max(
                1L,
                (long) Math.ceil((frameCount - 1L) / factor)
        );

        long dataBytes = outputFrames * frameBytes;

        try (InputStream in = new BufferedInputStream(new FileInputStream(input), 128 * 1024);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 128 * 1024)) {

            writeHeader(out, sampleRate, channels, dataBytes);

            byte[] current = new byte[frameBytes];
            byte[] next = new byte[frameBytes];
            readFully(in, current);
            readFully(in, next);

            long currentIndex = 0;
            byte[] outputBuffer = new byte[Math.max(64 * 1024, frameBytes)];
            int outputPosition = 0;

            for (long outFrame = 0; outFrame < outputFrames; outFrame++) {

                double inputPosition = outFrame * (double) factor;
                long wantedIndex = (long) Math.floor(inputPosition);
                double fraction = inputPosition - wantedIndex;

                while (currentIndex < wantedIndex) {
                    System.arraycopy(next, 0, current, 0, frameBytes);

                    currentIndex++;

                    int read = readUpTo(in, next);
                    if (read < frameBytes) {
                        Arrays.fill(next, (byte) 0);
                    }
                }

                for (int channel = 0; channel < channels; channel++) {
                    int offset = channel * 2;

                    short a = (short) ((current[offset] & 0xFF)
                            | ((current[offset + 1] & 0xFF) << 8));

                    short b = (short) ((next[offset] & 0xFF)
                            | ((next[offset + 1] & 0xFF) << 8));

                    int value = (int) Math.round(a + ((b - a) * fraction));

                    if (value > 32767) value = 32767;
                    if (value < -32768) value = -32768;

                    outputBuffer[outputPosition++] = (byte) (value & 0xFF);
                    outputBuffer[outputPosition++] = (byte) ((value >>> 8) & 0xFF);
                }

                if (outputPosition > outputBuffer.length - frameBytes) {
                    out.write(outputBuffer, 0, outputPosition);
                    outputPosition = 0;
                }

                if (outFrame % Math.max(1L, outputFrames / 100L) == 0) {
                    int progress = 60 + (int) Math.min(
                            39L,
                            (outFrame * 39L) / outputFrames
                    );
                    listener.onProgress(progress, "מעלה את גובה הקול...");
                }
            }

            if (outputPosition > 0) {
                out.write(outputBuffer, 0, outputPosition);
            }

            out.flush();
        }
    }

    private static void writeHeader(
            OutputStream out,
            int sampleRate,
            int channels,
            long dataSize
    ) throws IOException {

        int byteRate = sampleRate * channels * 2;
        int blockAlign = channels * 2;

        out.write('R');
        out.write('I');
        out.write('F');
        out.write('F');
        writeIntLE(out, 36L + dataSize);

        out.write('W');
        out.write('A');
        out.write('V');
        out.write('E');

        out.write('f');
        out.write('m');
        out.write('t');
        out.write(' ');
        writeIntLE(out, 16);
        writeShortLE(out, 1);
        writeShortLE(out, channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, blockAlign);
        writeShortLE(out, 16);

        out.write('d');
        out.write('a');
        out.write('t');
        out.write('a');
        writeIntLE(out, dataSize);
    }

    private static void writeIntLE(OutputStream out, long value) throws IOException {
        int v = (int) value;
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeShortLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;

        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);

            if (read < 0) {
                throw new IOException("קובץ האודיו הסתיים מוקדם מדי.");
            }

            offset += read;
        }
    }

    private static int readUpTo(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;

        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);

            if (read < 0) {
                break;
            }

            offset += read;
        }

        return offset;
    }

    private static final class DecodeResult {
        final File pcm;
        final int sampleRate;
        final int channels;

        DecodeResult(File pcm, int sampleRate, int channels) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}
