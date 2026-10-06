package com.d0548448174ai.image3d;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class DepthEstimator {
    private static final String MODEL_NAME = "depth_anything_v2_small_wi8_afp32.tflite";
    private static final int IN_W = 686;
    private static final int IN_H = 518;
    private static final int OUT_W = 686;
    private static final int OUT_H = 518;

    private final Interpreter interpreter;
    private final AssetFileDescriptor afd;
    private final FileInputStream modelStream;

    public DepthEstimator(Context context) throws Exception {
        afd = context.getAssets().openFd(MODEL_NAME);
        modelStream = new FileInputStream(afd.getFileDescriptor());
        FileChannel channel = modelStream.getChannel();
        MappedByteBuffer model = channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.getStartOffset(),
                afd.getDeclaredLength());

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        interpreter = new Interpreter(model, options);
    }

    public float[] estimate(Bitmap source) {
        Bitmap resized = Bitmap.createScaledBitmap(source, IN_W, IN_H, true);

        int pixelCount = IN_W * IN_H;
        ByteBuffer input = ByteBuffer
                .allocateDirect(pixelCount * 3 * 4)
                .order(ByteOrder.nativeOrder());

        final float[] mean = {0.485f, 0.456f, 0.406f};
        final float[] std = {0.229f, 0.224f, 0.225f};

        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < IN_H; y++) {
                for (int x = 0; x < IN_W; x++) {
                    int p = resized.getPixel(x, y);
                    int channel = c == 0 ? Color.red(p) : (c == 1 ? Color.green(p) : Color.blue(p));
                    float value = (channel / 255.0f - mean[c]) / std[c];
                    input.putFloat(value);
                }
            }
        }
        input.rewind();

        ByteBuffer output = ByteBuffer
                .allocateDirect(pixelCount * 4)
                .order(ByteOrder.nativeOrder());

        interpreter.run(input, output);
        output.rewind();

        float[] raw = new float[pixelCount];
        FloatBuffer fb = output.asFloatBuffer();
        fb.get(raw);

        resized.recycle();
        return raw;
    }

    public static float[] normalize(float[] raw) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float v : raw) {
            if (Float.isFinite(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!Float.isFinite(min) || !Float.isFinite(max) || Math.abs(max - min) < 1e-6f) {
            float[] flat = new float[raw.length];
            for (int i = 0; i < flat.length; i++) flat[i] = 0.5f;
            return flat;
        }

        float[] normalized = new float[raw.length];
        float range = max - min;
        for (int i = 0; i < raw.length; i++) {
            float v = raw[i];
            if (!Float.isFinite(v)) v = min;
            normalized[i] = (v - min) / range;
        }
        return normalized;
    }

    public void close() {
        interpreter.close();
        try { modelStream.close(); } catch (Exception ignored) {}
        try { afd.close(); } catch (Exception ignored) {}
    }
}
