package com.d0548448174ai.animeai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class AnimeGan {
    private static final String MODEL = "model/animeganv2_hayao_256x256.tflite";
    private static final int SIZE = 256;

    private final Interpreter interpreter;
    private final AssetFileDescriptor afd;
    private final FileInputStream stream;

    public AnimeGan(Context context) throws Exception {
        afd = context.getAssets().openFd(MODEL);
        stream = new FileInputStream(afd.getFileDescriptor());

        FileChannel channel = stream.getChannel();
        MappedByteBuffer mapped = channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.getStartOffset(),
                afd.getDeclaredLength());

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(2, Math.min(4,
                Runtime.getRuntime().availableProcessors())));
        interpreter = new Interpreter(mapped, options);
    }

    public Bitmap stylize(Bitmap source) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();

        float scale = Math.min(SIZE / (float) srcW, SIZE / (float) srcH);
        int fitW = Math.max(1, Math.round(srcW * scale));
        int fitH = Math.max(1, Math.round(srcH * scale));

        Bitmap fitted = Bitmap.createScaledBitmap(source, fitW, fitH, true);
        Bitmap square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(square);
        canvas.drawColor(Color.WHITE);
        int left = (SIZE - fitW) / 2;
        int top = (SIZE - fitH) / 2;
        canvas.drawBitmap(fitted, left, top, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

        ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder());

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int pixel = square.getPixel(x, y);
                input.putFloat(Color.red(pixel) / 255f);
                input.putFloat(Color.green(pixel) / 255f);
                input.putFloat(Color.blue(pixel) / 255f);
            }
        }
        input.rewind();

        ByteBuffer output = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder());

        interpreter.run(input, output);
        output.rewind();
        FloatBuffer values = output.asFloatBuffer();

        Bitmap outSquare = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        float[] rgb = new float[3];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                rgb[0] = values.get();
                rgb[1] = values.get();
                rgb[2] = values.get();
                int r = clamp((int) (rgb[0] * 255f));
                int g = clamp((int) (rgb[1] * 255f));
                int b = clamp((int) (rgb[2] * 255f));
                outSquare.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        int cropLeft = Math.max(0, left);
        int cropTop = Math.max(0, top);
        int cropRight = Math.min(SIZE, left + fitW);
        int cropBottom = Math.min(SIZE, top + fitH);

        Bitmap cropped = Bitmap.createBitmap(
                outSquare,
                cropLeft,
                cropTop,
                Math.max(1, cropRight - cropLeft),
                Math.max(1, cropBottom - cropTop));

        Bitmap restored = Bitmap.createScaledBitmap(cropped, srcW, srcH, true);

        if (fitted != source) fitted.recycle();
        square.recycle();
        outSquare.recycle();
        cropped.recycle();

        return restored;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public void close() {
        interpreter.close();
        try { stream.close(); } catch (Exception ignored) {}
        try { afd.close(); } catch (Exception ignored) {}
    }
}
