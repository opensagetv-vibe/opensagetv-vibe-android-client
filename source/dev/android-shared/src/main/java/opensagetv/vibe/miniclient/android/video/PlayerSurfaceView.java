package opensagetv.vibe.miniclient.android.video;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;


/**
 * Created by seans on 23/01/16.
 */
public class PlayerSurfaceView extends SurfaceView {
    private int gpuNativeRefreshFormat=android.graphics.PixelFormat.RGB_565;
    private Object gpuBufferOwner;
    private int gpuBufferWidth,gpuBufferHeight;

    /** GPU output need not shade a display-sized window for an SD source.
     * Keep all source pixels; SurfaceView composition still scales the buffer
     * to its existing aspect/viewport. Main-thread API only, no view-layout,
     * decoder-input, audio-clock or native Auto/Off change. Android contract:
     * https://developer.android.com/reference/android/view/SurfaceHolder#setFixedSize(int,int)
     */
    public void useGpuSourceBuffers(Object owner,int width,int height) {
        if(owner==null || width<1 || height<4 || width>4096 || height>4096)
            throw new IllegalArgumentException("Invalid GPU source buffer geometry");
        if(gpuBufferOwner==owner && gpuBufferWidth==width && gpuBufferHeight==height)return;
        gpuBufferOwner=owner;gpuBufferWidth=width;gpuBufferHeight=height;
        getHolder().setFixedSize(width,height);
    }
    /** A late old-output cleanup must not resize a newer GPU owner's buffer. */
    public void restoreAfterGpuBuffers(Object owner) {
        if(gpuBufferOwner!=owner)return;
        gpuBufferOwner=null;gpuBufferWidth=gpuBufferHeight=0;
        getHolder().setSizeFromLayout();
    }

    /** A GPU stage changes the native window format without changing
     * SurfaceView's cached request. Alternate two opaque requests so every
     * observed MTK/Tube GPU->native handoff recreates the display layer, including
     * a second decoder reload in the SAME Activity. Not a preference or a
     * normal native-output format change; only the scoped GPU owner calls it.
     */
    public void refreshAfterGpuOutput() {
        gpuNativeRefreshFormat=gpuNativeRefreshFormat==android.graphics.PixelFormat.RGBX_8888
                ? android.graphics.PixelFormat.RGB_565 : android.graphics.PixelFormat.RGBX_8888;
        getHolder().setFormat(gpuNativeRefreshFormat);
    }

    public PlayerSurfaceView(Context context) {
        super(context);
    }

    public PlayerSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlayerSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
