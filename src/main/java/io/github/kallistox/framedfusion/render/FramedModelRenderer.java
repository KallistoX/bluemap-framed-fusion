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
import io.github.kallistox.framedfusion.framed.FramedBlockEntity;
import io.github.kallistox.framedfusion.fusion.FusionResources;

import java.util.HashMap;
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
