package io.github.kallistox.framedfusion.render;

import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererFactory;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;

import java.lang.reflect.Field;

/**
 * Routes BlueMap's default block renderer through {@link ExtendedModelRenderer}. BlueMap has no public way to
 * change the renderer of blocks it already knows, so this swaps the factory behind
 * {@link BlockRendererType#DEFAULT} (one private field). If that fails, nothing changes and the map renders as
 * without the addon.
 */
public final class RendererHook {

    private RendererHook() {}

    public static boolean install() {
        try {
            Field field = BlockRendererType.Impl.class.getDeclaredField("rendererFactory");
            field.setAccessible(true);
            BlockRendererFactory original = (BlockRendererFactory) field.get(BlockRendererType.DEFAULT);
            BlockRendererFactory extended = (resourcePack, textureGallery, renderSettings) ->
                    new ExtendedModelRenderer(resourcePack, textureGallery, renderSettings,
                            original.create(resourcePack, textureGallery, renderSettings));
            field.set(BlockRendererType.DEFAULT, extended);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Logger.global.logWarning("[framed-fusion] Could not hook into BlueMap's block renderer, "
                    + "connected textures stay off: " + ex);
            return false;
        }
    }
}
