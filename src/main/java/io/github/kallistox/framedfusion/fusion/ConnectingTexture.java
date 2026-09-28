package io.github.kallistox.framedfusion.fusion;


/**
 * A texture with a Fusion {@code "connecting"} section in its {@code .png.mcmeta}.
 * The image size is filled in once BlueMap loads the image, see {@link FusionResources#loadTextures}.
 */
public final class ConnectingTexture {

    final String path;
    final Layout layout;
    /** Predicate from the texture's own {@code "connections"}, used when the model has none for the face. */
    final ConnectionPredicate predicate;
    final boolean animated;
    /** Position of the resource root in BlueMap's load order (0 = highest priority). */
    final int rootIndex;
    /** The png, read while its root is open (BlueMap closes each mod jar after reading it). */
    byte[] png;

    int imageWidth, imageHeight;
    int tileWidth, tileHeight;

    ConnectingTexture(String path, Layout layout, ConnectionPredicate predicate, boolean animated, int rootIndex) {
        this.path = path;
        this.layout = layout;
        this.predicate = predicate;
        this.animated = animated;
        this.rootIndex = rootIndex;
    }

    void setImageSize(int width, int height) {
        int frameWidth = width, frameHeight = height;
        if (layout == Layout.FULL && width == height) {
            frameHeight = height * 6 / 8; // older full-layout textures are square, the last two rows stay empty
        } else if (animated) {
            // frames are stacked vertically, use the first one
            frameHeight = Math.min(height, width / layout.width * layout.height);
        }
        this.imageWidth = width;
        this.imageHeight = height;
        this.tileWidth = frameWidth / layout.width;
        this.tileHeight = frameHeight / layout.height;
    }

    public boolean isUsable() {
        return tileWidth > 0 && tileHeight > 0;
    }

    public Layout layout() {
        return layout;
    }

    /** Texture u of tile {@code tile} at the position {@code u} (0..1) inside the tile. */
    public float u(int tile, float u) {
        return ((tile % layout.width) + u) * tileWidth / imageWidth;
    }

    /** Texture v of tile {@code tile} at the position {@code v} (0..1) inside the tile. */
    public float v(int tile, float v) {
        return ((tile / layout.width) + v) * tileHeight / imageHeight;
    }
}
