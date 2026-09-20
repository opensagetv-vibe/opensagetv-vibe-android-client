package opensagetv.vibe.miniclient.android.gdx;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.uibridge.Disposable;
import opensagetv.vibe.miniclient.graphics.UnifiedGraphicsCapability;
import opensagetv.vibe.miniclient.graphics.UnifiedYuvImage;

/**
 * Created by seans on 26/09/15.
 */
public class GdxTexture implements Disposable, opensagetv.vibe.miniclient.uibridge.Texture {
    private static final Logger log = LoggerFactory.getLogger(GdxTexture.class);

    int width;
    int height;
    boolean isFrameBuffer = false;
    FrameBuffer frameBuffer;
    boolean isFrameBufferInUse = false;

    Bitmap bitmap;
    Texture texture;
    int imageFormat;
    UnifiedYuvImage unifiedYuv;
    private ByteBuffer unifiedRgbaRow;

    public GdxTexture(Bitmap bitmap) {
        this.bitmap = bitmap;
        if (bitmap != null) {
            width = bitmap.getWidth();
            height = bitmap.getHeight();
        }
    }

    public GdxTexture(int width, int height) {
        this.width = width;
        this.height = height;
        this.isFrameBuffer = true;
    }

    public GdxTexture(int width, int height, int imageFormat) {
        this.width = width;
        this.height = height;
        this.imageFormat = imageFormat;
        this.isFrameBuffer = false;
        if (UnifiedGraphicsCapability.isHiresYuvFormat(imageFormat)) {
            this.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            this.unifiedYuv = new UnifiedYuvImage(width, height);
        }
    }

    public void load() {
        if (isFrameBuffer) {
            createFrameBuffer();
            return;
        }
        if (bitmap == null) return;
        if (texture != null) return;

        Texture tex = new Texture(bitmap.getWidth(), bitmap.getHeight(), Pixmap.Format.RGBA8888);
        Gdx.gl20.glBindTexture(GL20.GL_TEXTURE_2D, tex.getTextureObjectHandle());
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        Gdx.gl20.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        // Unified Y/UV images continue to receive rows after the first frame.
        // Keep their staging bitmap alive so the render-thread upload path can
        // update the existing GL texture without allocating a Bitmap per row.
        if (unifiedYuv == null) {
            bitmap.recycle();
            bitmap = null;
        }

        texture = tex;
    }

    /** Called on the libGDX render thread for a SageTV Y/UV image line. */
    public void loadUnifiedYuvLine(int line, byte[] data, int offset, int length) {
        if (unifiedYuv == null || bitmap == null) return;
        unifiedYuv.loadLine(line, data, offset, length);
        if (line < height) return;
        int[] row = new int[width];
        unifiedYuv.copyArgbRow(line - height, row);
        bitmap.setPixels(row, 0, width, 0, line - height, width, 1);
        if (texture == null && unifiedYuv.isComplete())
            load();
        if (texture != null) {
            if (unifiedRgbaRow == null || unifiedRgbaRow.capacity() < width * 4)
                unifiedRgbaRow = ByteBuffer.allocateDirect(width * 4)
                        .order(ByteOrder.nativeOrder());
            unifiedYuv.copyRgbaRow(line - height, unifiedRgbaRow);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                    texture.getTextureObjectHandle());
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, line - height,
                    width, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                    unifiedRgbaRow);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    private void createFrameBuffer() {
        if (frameBuffer == null) {
            frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false, false);
            frameBuffer.begin();
            Gdx.gl20.glClearColor(0, 0, 0, 0);
            Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT);
            frameBuffer.end();
        }
    }

    public void bindFrameBuffer() {
        if (frameBuffer == null) {
            createFrameBuffer();
        }
        if (isFrameBufferInUse) {
            log.error("Attempting to Bind Framebuffer when it is already in use");
        }
        // this ensures that the we get the updated texture from the framebuffer when texture() is called.
        texture = null;
        isFrameBufferInUse = true;
        frameBuffer.begin();
    }

    public void unbindFrameBuffer() {
        if (frameBuffer != null && isFrameBufferInUse) {
            frameBuffer.end();
        }
        isFrameBufferInUse = false;
    }

    public Texture texture() {
        if (texture != null) {
            return texture;
        }

        load();

        // load the texture from the buffer
        if (isFrameBuffer) {
            if (isFrameBufferInUse) {
                unbindFrameBuffer();
            }
            texture = frameBuffer.getColorBufferTexture();
        }
        return texture;
    }

    /**
     * Release everything about this Texture.
     */
    public void dispose() {
        if (texture != null) {
            try {
                texture.dispose();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (frameBuffer != null) {
            try {
                frameBuffer.dispose();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (bitmap != null) {
            try {
                bitmap.recycle();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        texture = null;
        frameBuffer = null;
        isFrameBufferInUse = false;
        bitmap = null;
    }
}
