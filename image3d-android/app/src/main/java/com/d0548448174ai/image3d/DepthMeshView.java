package com.d0548448174ai.image3d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class DepthMeshView extends GLSurfaceView {
    private static final int GRID_W = 96;
    private static final int GRID_H = 72;
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVP;" +
            "attribute vec3 aPosition;" +
            "attribute vec2 aTexCoord;" +
            "varying vec2 vTexCoord;" +
            "void main(){ gl_Position=uMVP*vec4(aPosition,1.0); vTexCoord=aTexCoord; }";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform sampler2D uTexture;" +
            "varying vec2 vTexCoord;" +
            "void main(){ gl_FragColor=texture2D(uTexture,vTexCoord); }";

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] rotation = new float[16];
    private final float[] mvp = new float[16];
    private final float[] temp = new float[16];

    private Renderer3D renderer;
    private Bitmap textureBitmap;
    private float[] depth;
    private boolean flipDepth = false;
    private float depthStrength = 0.9f;
    private float yaw = -12f;
    private float pitch = 8f;
    private float lastX;
    private float lastY;

    public DepthMeshView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new Renderer3D();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
    }

    public void setModel(Bitmap source, float[] normalizedDepth) {
        int maxSide = 640;
        float scale = Math.min(1f, maxSide / (float)Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(2, Math.round(source.getWidth() * scale));
        int h = Math.max(2, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, w, h, true);

        Bitmap old = textureBitmap;
        textureBitmap = scaled;
        if (old != null && old != source) old.recycle();

        depth = normalizedDepth;
        queueEvent(() -> { renderer.rebuild(); renderer.markTextureDirty(); });
        requestRender();
    }

    public void setDepthStrength(float strength) {
        depthStrength = strength;
        queueEvent(() -> renderer.rebuild());
        requestRender();
    }

    public void toggleDepth() {
        flipDepth = !flipDepth;
        queueEvent(() -> renderer.rebuild());
        requestRender();
    }

    public boolean isFlipped() {
        return flipDepth;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                yaw += dx * 0.55f;
                pitch += dy * 0.35f;
                pitch = Math.max(-70f, Math.min(70f, pitch));
                lastX = event.getX();
                lastY = event.getY();
                requestRender();
                return true;
            default:
                return true;
        }
    }

    public void saveAsPly(OutputStream out) throws Exception {
        if (textureBitmap == null || depth == null) {
            throw new IllegalStateException("אין מודל מוכן");
        }

        int vertexCount = GRID_W * GRID_H;
        int faceCount = (GRID_W - 1) * (GRID_H - 1) * 2;
        StringBuilder header = new StringBuilder();
        header.append("ply\n");
        header.append("format ascii 1.0\n");
        header.append("comment AI depth mesh generated from a single image\n");
        header.append("element vertex ").append(vertexCount).append("\n");
        header.append("property float x\nproperty float y\nproperty float z\n");
        header.append("property uchar red\nproperty uchar green\nproperty uchar blue\n");
        header.append("element face ").append(faceCount).append("\n");
        header.append("property list uchar int vertex_indices\n");
        header.append("end_header\n");
        out.write(header.toString().getBytes("UTF-8"));

        float aspect = textureBitmap.getWidth() / (float)textureBitmap.getHeight();

        for (int gy = 0; gy < GRID_H; gy++) {
            for (int gx = 0; gx < GRID_W; gx++) {
                float u = gx / (float)(GRID_W - 1);
                float v = gy / (float)(GRID_H - 1);
                float d = sampleDepth(u, v);
                if (flipDepth) d = 1f - d;

                float x = (u - 0.5f) * 2f * aspect;
                float y = (0.5f - v) * 2f;
                float z = (d - 0.5f) * depthStrength;

                int px = Math.min(textureBitmap.getWidth() - 1, Math.max(0, Math.round(u * (textureBitmap.getWidth() - 1))));
                int py = Math.min(textureBitmap.getHeight() - 1, Math.max(0, Math.round(v * (textureBitmap.getHeight() - 1))));
                int color = textureBitmap.getPixel(px, py);

                String line = String.format(Locale.US, "%.5f %.5f %.5f %d %d %d\n",
                        x, y, z, Color.red(color), Color.green(color), Color.blue(color));
                out.write(line.getBytes("UTF-8"));
            }
        }

        for (int gy = 0; gy < GRID_H - 1; gy++) {
            for (int gx = 0; gx < GRID_W - 1; gx++) {
                int a = gy * GRID_W + gx;
                int b = a + 1;
                int c = a + GRID_W;
                int d = c + 1;
                out.write((String.format(Locale.US, "3 %d %d %d\n", a, c, b)).getBytes("UTF-8"));
                out.write((String.format(Locale.US, "3 %d %d %d\n", b, c, d)).getBytes("UTF-8"));
            }
        }
        out.flush();
    }

    private float sampleDepth(float u, float v) {
        float fx = u * 685f;
        float fy = v * 517f;
        int x0 = Math.max(0, Math.min(685, (int)Math.floor(fx)));
        int y0 = Math.max(0, Math.min(517, (int)Math.floor(fy)));
        int x1 = Math.min(685, x0 + 1);
        int y1 = Math.min(517, y0 + 1);
        float ax = fx - x0;
        float ay = fy - y0;

        float d00 = depth[y0 * 686 + x0];
        float d10 = depth[y0 * 686 + x1];
        float d01 = depth[y1 * 686 + x0];
        float d11 = depth[y1 * 686 + x1];
        float top = d00 + (d10 - d00) * ax;
        float bottom = d01 + (d11 - d01) * ax;
        return top + (bottom - top) * ay;
    }

    private class Renderer3D implements GLSurfaceView.Renderer {
        private int program;
        private int textureId;
        private boolean textureDirty = true;
        private int positionHandle;
        private int texCoordHandle;
        private int mvpHandle;
        private int textureHandle;
        private FloatBuffer vertices;
        private ShortBuffer indices;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            textureId = 0;
            textureDirty = true;
            GLES20.glClearColor(0.96f, 0.94f, 0.99f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVP");
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
            rebuild();
        }

        void markTextureDirty() { textureDirty = true; }

        void rebuild() {
            if (depth == null) return;
            float[] data = new float[GRID_W * GRID_H * 5];
            int k = 0;
            float aspect = textureBitmap == null ? 1f :
                    textureBitmap.getWidth() / (float)textureBitmap.getHeight();

            for (int gy = 0; gy < GRID_H; gy++) {
                for (int gx = 0; gx < GRID_W; gx++) {
                    float u = gx / (float)(GRID_W - 1);
                    float v = gy / (float)(GRID_H - 1);
                    float d = sampleDepth(u, v);
                    if (flipDepth) d = 1f - d;

                    data[k++] = (u - 0.5f) * 2f * aspect;
                    data[k++] = (0.5f - v) * 2f;
                    data[k++] = (d - 0.5f) * depthStrength;
                    data[k++] = u;
                    data[k++] = 1f - v;
                }
            }
            vertices = ByteBuffer.allocateDirect(data.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(data).position(0);

            short[] idx = new short[(GRID_W - 1) * (GRID_H - 1) * 6];
            int n = 0;
            for (int gy = 0; gy < GRID_H - 1; gy++) {
                for (int gx = 0; gx < GRID_W - 1; gx++) {
                    short a = (short)(gy * GRID_W + gx);
                    short b = (short)(a + 1);
                    short c = (short)(a + GRID_W);
                    short d = (short)(c + 1);
                    idx[n++] = a; idx[n++] = c; idx[n++] = b;
                    idx[n++] = b; idx[n++] = c; idx[n++] = d;
                }
            }
            indices = ByteBuffer.allocateDirect(idx.length * 2)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            indices.put(idx).position(0);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = width / (float)Math.max(1, height);
            Matrix.perspectiveM(projection, 0, 42f, aspect, 0.1f, 100f);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (vertices == null || indices == null || textureBitmap == null) return;

            Matrix.setLookAtM(view, 0,
                    0f, 0f, 4.2f,
                    0f, 0f, 0f,
                    0f, 1f, 0f);
            Matrix.setIdentityM(rotation, 0);
            Matrix.rotateM(rotation, 0, pitch, 1f, 0f, 0f);
            Matrix.rotateM(rotation, 0, yaw, 0f, 1f, 0f);
            Matrix.multiplyMM(temp, 0, view, 0, rotation, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0);

            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);

            if (textureId == 0) {
                textureId = createTexture(textureBitmap);
                textureDirty = false;
            } else if (textureDirty) {
                uploadTexture(textureId, textureBitmap);
                textureDirty = false;
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(textureHandle, 0);

            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertices);

            vertices.position(3);
            GLES20.glEnableVertexAttribArray(texCoordHandle);
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertices);

            indices.position(0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.remaining(), GLES20.GL_UNSIGNED_SHORT, indices);

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(texCoordHandle);
        }

        private int createProgram(String vertex, String fragment) {
            int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs);
            GLES20.glAttachShader(p, fs);
            GLES20.glLinkProgram(p);
            return p;
        }

        private int compileShader(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }

        private int createTexture(Bitmap bitmap) {
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            uploadTexture(ids[0], bitmap);
            return ids[0];
        }

        private void uploadTexture(int id, Bitmap bitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        }
    }
}
