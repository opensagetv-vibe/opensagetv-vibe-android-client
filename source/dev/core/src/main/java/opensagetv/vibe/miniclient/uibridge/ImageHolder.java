package opensagetv.vibe.miniclient.uibridge;

public class ImageHolder<T extends Texture> extends Holder<T> {
    private int handle=-1;
    private int width;
    private int height;
    /** GFX image format; zero is the ordinary Android/RGBA path. */
    private int imageFormat;

    public ImageHolder() {
    }

    public ImageHolder(T img, int width, int height) {
        super(img);
        this.width = width;
        this.height = height;
        this.handle = -1;
        this.imageFormat = 0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getHandle() {
        return handle;
    }

    // release resources for this image
    public void dispose() {
        if (get() instanceof  Disposable) {
            try {
                ((Disposable) get()).dispose();
            } catch (Throwable t) {
            }
        }
        set(null);
        this.handle=-1;
        this.width=0;
        this.height=0;
        this.imageFormat=0;
    }

    public void setHandle(int handle) {
        this.handle = handle;
    }

    public int getImageFormat() {
        return imageFormat;
    }

    public void setImageFormat(int imageFormat) {
        this.imageFormat = imageFormat;
    }
}
