package io.github.kallistox.framedfusion;

import de.bluecolored.bluemap.core.logger.Logger;

/**
 * BlueMap addon entrypoint. BlueMap calls {@link #run()} once while loading addons, before it
 * loads any resource pack, so everything registered here is in place for the first render.
 */
public class FramedFusionAddon implements Runnable {

    @Override
    public void run() {
        Logger.global.logInfo("[framed-fusion] loaded");
    }

}
