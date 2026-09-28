package io.github.kallistox.framedfusion.render;

import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.ResourceModelRenderer;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import io.github.kallistox.framedfusion.framed.FramedBlockEntity;
import io.github.kallistox.framedfusion.framed.FramedShapes;
import io.github.kallistox.framedfusion.framed.Hull;
import io.github.kallistox.framedfusion.framed.Solid;
import io.github.kallistox.framedfusion.fusion.FusionResources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a Framed Block's shape (the addon's box models) with the textures of its camouflage: every face takes the
 * texture that the camouflage block shows in the same world direction. Faces whose texture variable is
 * {@code #camo_two} use the second camouflage of double blocks. Without camouflage the empty frame is drawn.
 */
public class FramedModelRenderer extends ExtendedModelRenderer {

    public static final BlockRendererType TYPE = new BlockRendererType.Impl(new Key("framedfusion", "framed"),
            (pack, gallery, settings) -> new FramedModelRenderer(pack, gallery, settings));

    private static final ResourcePath<Texture> FRAME = new ResourcePath<>("framedblocks:block/framed_block");
    private static final String SECOND_CAMO = "camo_two";
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockState[] camo = new BlockState[2];
    /** Per camouflage state: the face it shows in each world direction (index = Direction ordinal). */
    private final Map<BlockState, CamoFace[]> camoFaces = new HashMap<>();

    public FramedModelRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        super(resourcePack, textureGallery, renderSettings, new ResourceModelRenderer(resourcePack, textureGallery, renderSettings));
    }

    @Override
    protected boolean rendersItself(FusionResources.ModelInfo info) {
        return true;
    }

    @Override
    protected void beginBlock(BlockNeighborhood block) {
        super.beginBlock(block);
        FramedBlockEntity entity = block.getBlockEntity() instanceof FramedBlockEntity f ? f : null;
        camo[0] = entity != null ? entity.camo(0) : null;
        camo[1] = entity != null ? entity.camo(1) : null;
    }

    @Override
    protected FaceSource faceSource(Face face, int elementIndex, Direction faceDir) {
        FaceSource s = super.faceSource(face, elementIndex, faceDir);
        int part = SECOND_CAMO.equals(face.getTexture().getReferenceName()) ? 1 : 0;
        BlockState state = camo[part];
        Direction world = worldFaceDirection();
        CamoFace camoFace = state != null && world != null ? camoFaces(state)[world.ordinal()] : null;
        if (camoFace == null) {
            s.texturePath = FRAME;
            s.tintIndex = -1;
            s.fusion = null;
            return s;
        }
        s.texturePath = camoFace.texturePath;
        s.tintIndex = camoFace.tintIndex;
        s.tintPart = part;
        s.fusion = camoFace.fusion;
        s.self = state;
        return s;
    }

    private static final java.util.concurrent.ConcurrentHashMap<BlockState, ShapeFace[]> SHAPE_FACES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<Solid> FRAME_CUBE = List.of(Solid.prism(-1, false, 0, 0, 0, 16, 16, 0, 16, 16));

    /** A face of a coded shape, ready to draw: corners, uv, and where its texture and light come from. */
    private record ShapeFace(float[][] points, float[][] uvs, int part, Direction source, Direction light, Direction cull,
                             boolean up, String footprint) {}

    private ShapeFace currentFace;

    /**
     * A coded face on the block boundary is hidden if the neighbour has the same shape and that shape has a face of
     * the same outline on the opposite side (they lie on top of each other).
     */
    @Override
    protected boolean coveredBy(ExtendedBlock neighbour, Direction dir) {
        if (currentFace == null || !neighbour.getBlockState().equals(block().getBlockState())) return false;
        Direction opposite = dir.opposite();
        for (ShapeFace other : SHAPE_FACES.get(block().getBlockState())) {
            if (other.cull == opposite && other.footprint.equals(currentFace.footprint)) return true;
        }
        return false;
    }

    /** Full cubes (e.g. framed glass cubes) hide the faces towards an identical neighbour with the same camouflage. */
    @Override
    protected boolean cullsBoxFace(Face face, ExtendedBlock neighbour) {
        if (!modelIsFullCube() || !neighbour.getBlockState().equals(block().getBlockState())) return false;
        if (!(neighbour.getBlockEntity() instanceof FramedBlockEntity other)) return false;
        int part = SECOND_CAMO.equals(face.getTexture().getReferenceName()) ? 1 : 0;
        return camo[part] != null && camo[part].equals(other.camo(part));
    }

    @Override
    protected void renderExtra() {
        BlockState state = block().getBlockState();
        if (!FramedShapes.covers(state.getFormatted())) return;
        ShapeFace[] faces = SHAPE_FACES.computeIfAbsent(state, FramedModelRenderer::shapeFaces);
        for (ShapeFace face : faces) {
            BlockState camoState = face.part >= 0 ? camo[face.part] : null;
            CamoFace camoFace = camoState != null ? camoFaces(camoState)[face.source.ordinal()] : null;
            float[][] uvs = face.uvs;
            currentFace = face;
            if (camoFace == null) {
                emitPolygon(face.points, uvs, face.light, face.cull, face.up, FRAME, null);
                continue;
            }
            if (camoFace.fusion() != null) uvs = connectedTile(camoFace.fusion().texture(), uvs);
            emitPolygon(face.points, uvs, face.light, face.cull, face.up, camoFace.texturePath(),
                    camoFace.tintIndex() >= 0 ? tintColor(face.part) : null);
        }
        currentFace = null;
    }

    /**
     * Coded (sloped) shapes show the tile of a connecting texture that is connected on all sides: they are mostly
     * parts of larger areas (glass roofs), and for borderless textures that is the tile without a frame.
     */
    private static float[][] connectedTile(io.github.kallistox.framedfusion.fusion.ConnectingTexture texture, float[][] uvs) {
        int tile = texture.layout() == io.github.kallistox.framedfusion.fusion.Layout.PIECED ? 1 : texture.layout().tile(0xFF);
        float[][] out = new float[uvs.length][];
        for (int i = 0; i < uvs.length; i++) out[i] = new float[]{texture.u(tile, uvs[i][0]), texture.v(tile, uvs[i][1])};
        return out;
    }

    private static ShapeFace[] shapeFaces(BlockState state) {
        List<Solid> solids = FramedShapes.of(state);
        if (solids == null) solids = FRAME_CUBE;
        List<ShapeFace> out = new java.util.ArrayList<>();
        for (Solid solid : solids) {
            for (Hull.Polygon polygon : Hull.faces(solid.points())) {
                float[] n = polygon.normal();
                Direction axis = axisOf(n);
                Direction source;
                if (axis != null) source = axis;
                else if (solid.slopeSource() != null) source = Direction.fromString(solid.slopeSource());
                else if (solid.ySlope() && Math.abs(n[1]) > 0.01f) source = n[1] > 0 ? Direction.UP : Direction.DOWN;
                else if (Math.abs(n[0]) > Math.abs(n[2])) source = n[0] > 0 ? Direction.EAST : Direction.WEST;
                else source = n[2] > 0 ? Direction.SOUTH : Direction.NORTH;
                Direction light = axis != null ? axis : dominant(n);
                Direction cull = axis != null && onBoundary(polygon.points(), axis) ? axis : null;
                float[][] pts = polygon.points().toArray(new float[0][]);
                float[][] uvs = new float[pts.length][];
                for (int i = 0; i < pts.length; i++) uvs[i] = defaultUv(source, pts[i]);
                out.add(new ShapeFace(pts, uvs, solid.part(), source, light, cull, n[1] > 0.01f,
                        cull != null ? footprint(pts, cull) : ""));
            }
        }
        return out.toArray(new ShapeFace[0]);
    }

    /** The outline of a boundary face in its plane, independent of the side it is on. */
    private static String footprint(float[][] pts, Direction side) {
        Vector3i v = side.toVector();
        int drop = v.getX() != 0 ? 0 : v.getY() != 0 ? 1 : 2;
        java.util.List<String> corners = new java.util.ArrayList<>();
        for (float[] p : pts) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) if (i != drop) sb.append(Math.round(p[i] * 8)).append(',');
            corners.add(sb.toString());
        }
        java.util.Collections.sort(corners);
        return String.join(";", corners);
    }

    /** Vanilla's default uv of a face pointing in {@code dir}, for a point in block pixels (0..1 result). */
    private static float[] defaultUv(Direction dir, float[] p) {
        float x = p[0], y = p[1], z = p[2];
        float u, v;
        switch (dir) {
            case DOWN -> { u = x; v = 16 - z; }
            case UP -> { u = x; v = z; }
            case NORTH -> { u = 16 - x; v = 16 - y; }
            case SOUTH -> { u = x; v = 16 - y; }
            case WEST -> { u = z; v = 16 - y; }
            default -> { u = 16 - z; v = 16 - y; }
        }
        return new float[]{u / 16f, v / 16f};
    }

    private static Direction axisOf(float[] n) {
        for (Direction d : DIRECTIONS) {
            Vector3i v = d.toVector();
            if (n[0] * v.getX() + n[1] * v.getY() + n[2] * v.getZ() > 0.999f) return d;
        }
        return null;
    }

    private static Direction dominant(float[] n) {
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return n[0] > 0 ? Direction.EAST : Direction.WEST;
        return n[2] > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean onBoundary(List<float[]> points, Direction axis) {
        Vector3i v = axis.toVector();
        int i = v.getX() != 0 ? 0 : v.getY() != 0 ? 1 : 2;
        float target = (v.getX() + v.getY() + v.getZ()) > 0 ? 16 : 0;
        for (float[] p : points) if (Math.abs(p[i] - target) > 1e-3f) return false;
        return true;
    }

    @Override
    protected BlockState tintState(int part) {
        return camo[part] != null ? camo[part] : block().getBlockState();
    }

    private CamoFace[] camoFaces(BlockState state) {
        return camoFaces.computeIfAbsent(state, this::lookUp);
    }

    /** For each world direction, the face of the camouflage's model that points there. */
    private CamoFace[] lookUp(BlockState state) {
        CamoFace[] faces = new CamoFace[DIRECTIONS.length];
        var resource = resourcePack().getBlockState(state);
        if (resource == null) return faces;
        Variant[] first = {null};
        resource.forEach(state, 0, 0, 0, v -> { if (first[0] == null) first[0] = v; });
        Variant variant = first[0];
        if (variant == null) return faces;
        Model model = variant.getModel().getResource(resourcePack()::getModel);
        if (model == null || model.getElements() == null) return faces;
        FusionResources fusion = fusion();
        FusionResources.ModelInfo info = fusion != null ? fusion.info(variant.getModel(), model) : FusionResources.ModelInfo.NONE;

        for (Direction modelDir : DIRECTIONS) {
            Direction world = rotate(modelDir, variant);
            if (world == null) continue;
            Element[] elements = model.getElements();
            int best = -1;
            float bestArea = 0;
            for (int i = 0; i < elements.length; i++) {
                if (elements[i] == null || elements[i].getFaces().get(modelDir) == null) continue;
                float area = area(elements[i], modelDir);
                if (best < 0 || area > bestArea) {
                    best = i;
                    bestArea = area;
                }
            }
            if (best < 0) continue;
            Face face = elements[best].getFaces().get(modelDir);
            ResourcePath<Texture> texture = face.getTexture().getTexturePath(model.getTextures()::get);
            if (texture == null) continue;
            faces[world.ordinal()] = new CamoFace(texture, face.getTintindex(), info.face(best, modelDir));
        }
        return faces;
    }

    private static Direction rotate(Direction dir, Variant variant) {
        Vector3i v = dir.toVector();
        VectorM3f r = new VectorM3f(v.getX(), v.getY(), v.getZ());
        if (variant.isTransformed()) r.rotateAndScale(variant.getTransformMatrix());
        int x = Math.round(r.x), y = Math.round(r.y), z = Math.round(r.z);
        for (Direction d : DIRECTIONS) {
            Vector3i dv = d.toVector();
            if (dv.getX() == x && dv.getY() == y && dv.getZ() == z) return d;
        }
        return null;
    }

    private static float area(Element element, Direction dir) {
        Vector3f size = element.getTo().sub(element.getFrom()).abs();
        return switch (dir.getAxis()) {
            case X -> size.getY() * size.getZ();
            case Y -> size.getX() * size.getZ();
            case Z -> size.getX() * size.getY();
        };
    }

    private record CamoFace(ResourcePath<Texture> texturePath, int tintIndex, FusionResources.FaceInfo fusion) {}
}
