package io.github.kallistox.framedfusion;

import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import io.github.kallistox.framedfusion.framed.FramedResources;
import io.github.kallistox.framedfusion.fusion.FusionResources;
import io.github.kallistox.framedfusion.render.FramedModelRenderer;
import io.github.kallistox.framedfusion.render.RendererHook;

/**
 * BlueMap addon entrypoint. BlueMap calls {@link #run()} once while loading addons, before it
 * loads any resource pack, so everything registered here is in place for the first render.
 */
public class FramedFusionAddon implements Runnable {

    @Override
    public void run() {
        // Framed Blocks: own renderer for the addon's blockstates, and keep the block data (camouflage)
        BlockRendererType.REGISTRY.register(FramedModelRenderer.TYPE);
        FramedResources.registerBlockEntity("framed_tile");
        FramedResources.registerBlockEntity("framed_double_tile");
        ResourcePackExtensionType.REGISTRY.register(FramedResources.TYPE);

        // Fusion: needs the hook into BlueMap's default renderer
        if (RendererHook.install()) {
            ResourcePackExtensionType.REGISTRY.register(FusionResources.TYPE);
        }
        Logger.global.logInfo("[framed-fusion] loaded");
    }

}
