package io.github.kallistox.framedfusion.framed;

import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.mca.blockentity.BlockEntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes BlueMap keep the camouflage of Framed Blocks: BlueMap drops the data of block entities it does not know.
 * Framed Blocks names its block entities after one of their blocks (plus "framed_tile" and "framed_double_tile"),
 * so every block id of the mod is registered with {@link FramedBlockEntity}.
 */
public class FramedResources implements ResourcePackExtension {

    public static final String NAMESPACE = "framedblocks";

    public static final ResourcePackExtensionType<FramedResources> TYPE = new ResourcePackExtensionType<>() {
        private final Key key = new Key("framedfusion", "framed");
        @Override public Key getKey() { return key; }
        @Override public FramedResources create() { return new FramedResources(); }
    };

    private final Set<String> registered = new HashSet<>();

    public static void registerBlockEntity(String name) {
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key(NAMESPACE, name), FramedBlockEntity.class));
    }

    @Override
    public void loadResources(Path root) throws IOException {
        Path blockstates = root.resolve("assets").resolve(NAMESPACE).resolve("blockstates");
        if (!Files.isDirectory(blockstates)) return;
        try (Stream<Path> files = Files.list(blockstates)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .forEach(n -> {
                        String name = n.substring(0, n.length() - ".json".length());
                        if (registered.add(name)) registerBlockEntity(name);
                    });
        }
    }

    @Override
    public void bake() {
        if (!registered.isEmpty()) Logger.global.logInfo("[framed-fusion] Framed Blocks: block data of " + registered.size() + " blocks");
    }
}
