package io.github.kallistox.framedfusion.fusion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.TextureVariable;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.Key;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Reads Fusion's connecting textures and connecting models while BlueMap loads a resource pack. Like BlueMap, the
 * first root that has a resource wins (roots come in priority order).
 */
public class FusionResources implements ResourcePackExtension {

    public static final ResourcePackExtensionType<FusionResources> TYPE = new ResourcePackExtensionType<>() {
        private final Key key = new Key("framedfusion", "fusion");
        @Override public Key getKey() { return key; }
        @Override public FusionResources create() { return new FusionResources(); }
    };

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Texture path ("ns:block/x") -> connecting texture. */
    private final Map<String, ConnectingTexture> textures = new ConcurrentHashMap<>();
    private final Set<String> seenTextureMeta = ConcurrentHashMap.newKeySet();
    /** Model path ("ns:block/x") -> Fusion connecting model. */
    private final Map<String, FusionModel> models = new ConcurrentHashMap<>();
    private final Set<String> seenModels = ConcurrentHashMap.newKeySet();

    private final Map<String, ModelInfo> modelInfos = new ConcurrentHashMap<>();
    private volatile boolean texturesPlaced;
    private int rootCount;

    @Override
    public void loadResources(Path root) throws IOException {
        int rootIndex = rootCount++;
        for (Path namespace : list(root.resolve("assets"))) {
            String ns = namespace.getFileName().toString().replace("/", "");
            scanModels(ns, namespace.resolve("models"));
            scanTextureMeta(ns, namespace.resolve("textures"), rootIndex);
        }
    }

    private void scanModels(String ns, Path models) throws IOException {
        if (!Files.isDirectory(models)) return;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(models)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        }
        files.parallelStream().forEach(file -> {
            String rel = models.relativize(file).toString().replace('\\', '/');
            if (rel.startsWith("item/")) return;
            String key = ns + ":" + rel.substring(0, rel.length() - ".json".length());
            if (!seenModels.add(key)) return;
            try {
                byte[] bytes = Files.readAllBytes(file);
                if (!contains(bytes, "fusion")) return;
                JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                FusionModel model = FusionModel.parse(json);
                if (model != null) this.models.put(key, model);
            } catch (Exception ex) {
                Logger.global.logDebug("[framed-fusion] Failed to read model " + file + ": " + ex);
            }
        });
    }

    private void scanTextureMeta(String ns, Path textures, int rootIndex) throws IOException {
        if (!Files.isDirectory(textures)) return;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(textures)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".png.mcmeta")).toList();
        }
        for (Path file : files) {
            String rel = textures.relativize(file).toString().replace('\\', '/');
            String key = ns + ":" + rel.substring(0, rel.length() - ".png.mcmeta".length());
            if (!seenTextureMeta.add(key)) continue;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!(json instanceof JsonObject o) || !(o.get("fusion") instanceof JsonObject fusion)) continue;
                String type = fusion.has("type") ? fusion.get("type").getAsString() : "";
                if (!type.equals("connecting") && !type.equals("fusion:connecting")) continue;
                String layoutName = fusion.has("layout") ? fusion.get("layout").getAsString() : "full";
                Layout layout = Layout.byName(layoutName);
                if (layout == null) {
                    Logger.global.logDebug("[framed-fusion] Unsupported layout '" + layoutName + "' in " + key);
                    continue;
                }
                ConnectionPredicate predicate = fusion.has("connections") ? Predicates.parse(fusion.get("connections")) : null;
                ConnectingTexture texture = new ConnectingTexture(key, layout, predicate, o.has("animation"), rootIndex);
                Path png = file.resolveSibling(file.getFileName().toString().substring(0, file.getFileName().toString().length() - ".mcmeta".length()));
                if (Files.isRegularFile(png)) texture.png = Files.readAllBytes(png);
                this.textures.put(key, texture);
            } catch (Exception ex) {
                Logger.global.logDebug("[framed-fusion] Failed to read " + file + ": " + ex);
            }
        }
    }

    /**
     * Puts plain copies of all connecting textures into the pack. BlueMap would read the Fusion section of the
     * {@code .mcmeta} as an animation (the webapp then scrolls through the tiles). BlueMap reads the roots' metadata
     * first and the images afterwards, root by root, and skips textures that are already present - so the copies go
     * in with the first root's images, before BlueMap reaches the roots that hold the originals. Only textures in
     * the very first root cannot be replaced this way; they keep BlueMap's default look.
     */
    @Override
    public Iterable<Texture> loadTextures(Path root) throws IOException {
        if (texturesPlaced) return List.of();
        texturesPlaced = true;
        List<Texture> out = new ArrayList<>();
        for (ConnectingTexture texture : textures.values()) {
            byte[] png = texture.png;
            texture.png = null;
            // BlueMap has already loaded the first root's images; those keep their default look
            if (png == null || texture.rootIndex == 0) continue;
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                if (image == null) continue;
                texture.setImageSize(image.getWidth(), image.getHeight());
                ResourcePath<Texture> path = new ResourcePath<>(texture.path);
                Texture plain = Texture.from(path, image);
                path.setResource(plain);
                out.add(plain);
            } catch (IOException ex) {
                Logger.global.logDebug("[framed-fusion] Failed to read texture " + texture.path + ": " + ex);
            }
        }
        return out;
    }

    @Override
    public void bake() {
        textures.values().removeIf(t -> !t.isUsable());
        Logger.global.logInfo("[framed-fusion] Fusion: " + textures.size() + " connecting textures, " + models.size() + " connecting models");
    }

    public boolean isEmpty() {
        return textures.isEmpty();
    }

    /** Connecting faces of a model, or {@link ModelInfo#NONE}. */
    public ModelInfo info(ResourcePath<Model> path, Model model) {
        return modelInfos.computeIfAbsent(path.getFormatted(), p -> computeInfo(p, model));
    }

    private ModelInfo computeInfo(String path, Model model) {
        Element[] elements = model.getElements();
        if (elements == null) return ModelInfo.NONE;
        Map<String, Object> connections = connections(path);
        FaceInfo[][] faces = null;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] == null) continue;
            for (Direction dir : DIRECTIONS) {
                Face face = elements[i].getFaces().get(dir);
                if (face == null) continue;
                ResourcePath<Texture> texturePath = face.getTexture().getTexturePath(model.getTextures()::get);
                if (texturePath == null) continue;
                ConnectingTexture texture = textures.get(texturePath.getFormatted());
                if (texture == null) continue;

                String key = face.getTexture().getReferenceName();
                if (key == null) key = texturePath.getFormatted();
                ConnectionPredicate predicate = resolve(key, connections, model.getTextures());
                if (predicate == null) predicate = texture.predicate;
                if (predicate == null) predicate = ConnectionPredicate.SAME_STATE;

                if (faces == null) faces = new FaceInfo[elements.length][];
                if (faces[i] == null) faces[i] = new FaceInfo[DIRECTIONS.length];
                faces[i][dir.ordinal()] = new FaceInfo(texture, predicate);
            }
        }
        return faces == null ? ModelInfo.NONE : new ModelInfo(faces);
    }

    /** The connections of a Fusion model and its Fusion parents; the child's entries win. */
    private Map<String, Object> connections(String path) {
        Map<String, Object> merged = new HashMap<>();
        Set<String> visited = new HashSet<>();
        String current = path;
        while (current != null && visited.add(current)) {
            FusionModel model = models.get(current);
            if (model == null) break;
            model.connections().forEach(merged::putIfAbsent);
            current = model.parent();
        }
        return merged;
    }

    /**
     * Follows a face's texture key through the model's connections ("#side" aliases) and texture variables, the
     * way Fusion does, ending at "default".
     */
    private static ConnectionPredicate resolve(String key, Map<String, Object> connections, Map<String, TextureVariable> variables) {
        Set<String> visited = new HashSet<>();
        while (key != null && visited.add(key)) {
            Object value = connections.get(key);
            if (value instanceof ConnectionPredicate predicate) return predicate;
            if (value instanceof String alias) {
                key = alias;
                continue;
            }
            TextureVariable variable = variables.get(key);
            if (variable != null) {
                if (variable.getReferenceName() != null) {
                    key = variable.getReferenceName();
                } else {
                    ResourcePath<Texture> texture = variable.getTexturePath(variables::get);
                    key = texture != null ? texture.getFormatted() : FusionModel.DEFAULT_KEY;
                }
            } else {
                key = key.equals(FusionModel.DEFAULT_KEY) ? null : FusionModel.DEFAULT_KEY;
            }
        }
        return null;
    }

    private static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.toList();
        }
    }

    private static boolean contains(byte[] bytes, String ascii) {
        outer:
        for (int i = 0; i <= bytes.length - ascii.length(); i++) {
            for (int j = 0; j < ascii.length(); j++) {
                if (bytes[i + j] != ascii.charAt(j)) continue outer;
            }
            return true;
        }
        return false;
    }

    public record FaceInfo(ConnectingTexture texture, ConnectionPredicate predicate) {}

    /** Per element and face direction: the connecting face, or {@code null}. */
    public record ModelInfo(FaceInfo[][] faces) {
        public static final ModelInfo NONE = new ModelInfo(null);

        public FaceInfo face(int element, Direction direction) {
            if (faces == null || faces[element] == null) return null;
            return faces[element][direction.ordinal()];
        }
    }
}
