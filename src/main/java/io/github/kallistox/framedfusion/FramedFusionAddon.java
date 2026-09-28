package io.github.kallistox.framedfusion;

import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import io.github.kallistox.framedfusion.fusion.FusionResources;
import io.github.kallistox.framedfusion.render.RendererHook;

/**
 * BlueMap addon entrypoint. BlueMap calls {@link #run()} once while loading addons, before it
 * loads any resource pack, so everything registered here is in place for the first render.
 */
public class FramedFusionAddon implements Runnable {

    @Override
    public void run() {
        if (!RendererHook.install()) return;
        ResourcePackExtensionType.REGISTRY.register(FusionResources.TYPE);
        Logger.global.logInfo("[framed-fusion] loaded: Fusion connected textures");
    }

}
