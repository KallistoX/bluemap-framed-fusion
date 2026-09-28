/*
 * Based on BlueMap's ResourceModelRenderer (BlueMap 5.7), licensed under the MIT License (MIT):
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * Changes: faces with a Fusion connecting texture pick their tile from the neighbours (connectedFace); every
 * other block is handed to BlueMap's own renderer.
 */
package io.github.kallistox.framedfusion.render;

import com.flowpowered.math.TrigMath;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import com.flowpowered.math.vector.Vector4f;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.resources.BlockColorCalculatorFactory;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM2f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.BlockProperties;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import io.github.kallistox.framedfusion.framed.FramedBlockEntity;
import io.github.kallistox.framedfusion.fusion.ConnectingTexture;
import io.github.kallistox.framedfusion.fusion.ConnectionDirection;
import io.github.kallistox.framedfusion.fusion.ConnectionPredicate;
import io.github.kallistox.framedfusion.fusion.FusionResources;
import io.github.kallistox.framedfusion.fusion.Layout;

import java.util.Arrays;

/**
 * BlueMap's default block renderer, extended by Fusion connecting textures. Blocks without such faces go to
 * {@code fallback} (BlueMap's own renderer), so they render exactly as before.
 */
@SuppressWarnings("DuplicatedCode")
public class ExtendedModelRenderer implements BlockRenderer {
    private static final float BLOCK_SCALE = 1f / 16f;
    private static final float EPSILON = 1e-4f;
    private static final int[] DEBUG = parseDebug(System.getProperty("framedfusion.debug"));

    private static int[] parseDebug(String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())};
    }

    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final RenderSettings renderSettings;
    private final BlockColorCalculatorFactory.BlockColorCalculator blockColorCalculator;
    private final BlockRenderer fallback;
    private final FusionResources fusion;

    private final VectorM3f[] corners = new VectorM3f[8];
    private final VectorM2f[] rawUvs = new VectorM2f[4];
    private final VectorM2f[] uvs = new VectorM2f[4];
    private final Color tintColor = new Color();
    private final Color mapColor = new Color();

    private BlockNeighborhood block;
    private Variant variant;
    private Model modelResource;
    private FusionResources.ModelInfo fusionInfo;
    private TileModelView blockModel;
    private Color blockColor;
    private float blockColorOpacity;

    public ExtendedModelRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings, BlockRenderer fallback) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.renderSettings = renderSettings;
        this.blockColorCalculator = resourcePack.getColorCalculatorFactory().createCalculator();
        this.fallback = fallback;
        FusionResources fusion = resourcePack.getResourcePackExtension(FusionResources.TYPE);
        this.fusion = fusion != null && !fusion.isEmpty() ? fusion : null;

        for (int i = 0; i < corners.length; i++) corners[i] = new VectorM3f(0, 0, 0);
        for (int i = 0; i < rawUvs.length; i++) rawUvs[i] = new VectorM2f(0, 0);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView blockModel, Color color) {
        Model model = variant.getModel().getResource(resourcePack::getModel);
        FusionResources.ModelInfo info = model != null && fusion != null
                ? fusion.info(variant.getModel(), model) : FusionResources.ModelInfo.NONE;
        if (model == null || !rendersItself(info)) {
            fallback.render(block, variant, blockModel, color);
            return;
        }
        beginBlock(block);

        this.block = block;
        this.blockModel = blockModel;
        this.blockColor = color;
        this.blockColorOpacity = 0f;
        this.variant = variant;
        this.modelResource = model;
        this.fusionInfo = info;

        this.tintColor.set(0, 0, 0, -1, true);

        // render model
        int modelStart = blockModel.getStart();

        Element[] elements = modelResource.getElements();
        if (elements != null) {
            for (int i = 0; i < elements.length; i++) {
                if (elements[i] == null) continue;
                buildModelElementResource(elements[i], i, blockModel.initialize());
            }
        }

        // extra geometry of subclasses (block pixels, not rotated by the variant)
        this.blockModel = blockModel;
        int extraStart = blockModel.initialize().getStart();
        renderExtra();
        blockModel.initialize(extraStart);
        if (blockModel.getSize() > 0) blockModel.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);

        if (color.a > 0) {
            color.flatten().straight();
            color.a = blockColorOpacity;
        }

        blockModel.initialize(modelStart);

        // apply model-transform
        if (variant.isTransformed())
            blockModel.transform(variant.getTransformMatrix());

        //random offset
        if (block.getProperties().isRandomOffset()){
            float dx = (hashToFloat(block.getX(), block.getZ(), 123984) - 0.5f) * 0.75f;
            float dz = (hashToFloat(block.getX(), block.getZ(), 345542) - 0.5f) * 0.75f;
            blockModel.translate(dx, 0, dz);
        }

    }

    private final MatrixM4f modelElementTransform = new MatrixM4f();
    private void buildModelElementResource(Element element, int elementIndex, TileModelView blockModel) {

        //create faces
        Vector3f from = element.getFrom();
        Vector3f to = element.getTo();

        float
                minX = Math.min(from.getX(), to.getX()),
                minY = Math.min(from.getY(), to.getY()),
                minZ = Math.min(from.getZ(), to.getZ()),
                maxX = Math.max(from.getX(), to.getX()),
                maxY = Math.max(from.getY(), to.getY()),
                maxZ = Math.max(from.getZ(), to.getZ());

        VectorM3f[] c = corners;
        c[0].x = minX; c[0].y = minY; c[0].z = minZ;
        c[1].x = minX; c[1].y = minY; c[1].z = maxZ;
        c[2].x = maxX; c[2].y = minY; c[2].z = minZ;
        c[3].x = maxX; c[3].y = minY; c[3].z = maxZ;
        c[4].x = minX; c[4].y = maxY; c[4].z = minZ;
        c[5].x = minX; c[5].y = maxY; c[5].z = maxZ;
        c[6].x = maxX; c[6].y = maxY; c[6].z = minZ;
        c[7].x = maxX; c[7].y = maxY; c[7].z = maxZ;

        this.blockModel = blockModel;
        int modelStart = blockModel.getStart();
        createElementFace(element, elementIndex, Direction.DOWN, c[0], c[2], c[3], c[1]);
        createElementFace(element, elementIndex, Direction.UP, c[5], c[7], c[6], c[4]);
        createElementFace(element, elementIndex, Direction.NORTH, c[2], c[0], c[4], c[6]);
        createElementFace(element, elementIndex, Direction.SOUTH, c[1], c[3], c[7], c[5]);
        createElementFace(element, elementIndex, Direction.WEST, c[0], c[1], c[5], c[4]);
        createElementFace(element, elementIndex, Direction.EAST, c[3], c[2], c[6], c[7]);
        blockModel.initialize(modelStart);

        //rotate and scale down
        blockModel.transform(modelElementTransform
                .copy(element.getRotation().getMatrix())
                .scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE)
        );
    }

    private final VectorM3f faceRotationVector = new VectorM3f(0, 0, 0);
    private void createElementFace(Element element, int elementIndex, Direction faceDir, VectorM3f c0, VectorM3f c1, VectorM3f c2, VectorM3f c3) {
        Face face = element.getFaces().get(faceDir);
        if (face == null) return;

        Vector3i faceDirVector = faceDir.toVector();

        // light calculation
        ExtendedBlock facedBlockNeighbor = getRotationRelativeBlock(faceDir);
        LightData blockLightData = block.getLightData();
        LightData facedLightData = facedBlockNeighbor.getLightData();

        int sunLight = Math.max(blockLightData.getSkyLight(), facedLightData.getSkyLight());
        int blockLight = Math.max(blockLightData.getBlockLight(), facedLightData.getBlockLight());

        // filter out faces that are in a "cave" that should not be rendered
        if (
                block.isRemoveIfCave() &&
                (renderSettings.isCaveDetectionUsesBlockLight() ? Math.max(blockLight, sunLight) : sunLight) == 0
        ) return;

        // calculate faceRotationVector
        faceRotationVector.set(
                faceDirVector.getX(),
                faceDirVector.getY(),
                faceDirVector.getZ()
        );
        faceRotationVector.rotateAndScale(element.getRotation().getMatrix());
        makeRotationRelative(faceRotationVector);

        // face culling
        if (renderSettings.isRenderTopOnly() && faceRotationVector.y < 0.01) return;
        if (face.getCullface() != null) {
            ExtendedBlock b = getRotationRelativeBlock(face.getCullface());
            BlockProperties p = b.getProperties();
            if (p.isCulling()) return;
            if (p.getCullingIdentical() && b.getBlockState().equals(block.getBlockState())) return;
            if (cullsBoxFace(face, b)) return;
        }

        // ####### texture (from the model, or from a camouflage)
        FaceSource source = faceSource(face, elementIndex, faceDir);
        ResourcePath<Texture> texturePath = source.texturePath;
        int textureId = textureGallery.get(texturePath);

        // ####### UV
        Vector4f uvRaw = face.getUv();
        float
                uvx = uvRaw.getX() / 16f,
                uvy = uvRaw.getY() / 16f,
                uvz = uvRaw.getZ() / 16f,
                uvw = uvRaw.getW() / 16f;

        rawUvs[0].set(uvx, uvw);
        rawUvs[1].set(uvz, uvw);
        rawUvs[2].set(uvz, uvy);
        rawUvs[3].set(uvx, uvy);

        // face-rotation
        int rotationSteps = Math.floorDiv(face.getRotation(), 90) % 4;
        if (rotationSteps < 0) rotationSteps += 4;
        for (int i = 0; i < 4; i++)
            uvs[i] = rawUvs[(rotationSteps + i) % 4];

        // UV-Lock counter-rotation
        float uvRotation = 0f;
        if (variant.isUvlock() && variant.isTransformed()) {
            float xRotSin = TrigMath.sin(variant.getX() * TrigMath.DEG_TO_RAD);
            float xRotCos = TrigMath.cos(variant.getX() * TrigMath.DEG_TO_RAD);

            uvRotation =
                    variant.getY() * (faceDirVector.getY() * xRotCos + faceDirVector.getZ() * xRotSin) +
                    variant.getX() * (1 - faceDirVector.getY());
        }

        // rotate uv's
        if (uvRotation != 0){
            uvRotation = (float)(uvRotation * TrigMath.DEG_TO_RAD);
            float cx = TrigMath.cos(uvRotation), cy = TrigMath.sin(uvRotation);
            for (VectorM2f uv : uvs) {
                uv.translate(-0.5f, -0.5f);
                uv.rotate(cx, cy);
                uv.translate(0.5f, 0.5f);
            }
        }

        // ####### face-tint
        float tintR = 1f, tintG = 1f, tintB = 1f;
        Color tint = null;
        if (source.tintIndex >= 0) {
            tint = tintColor(source.tintPart);
            tintR = tint.r; tintG = tint.g; tintB = tint.b;
        }

        // ####### blocklight
        int emissiveBlockLight = Math.max(blockLight, element.getLightEmission());

        // ######## AO
        float ao0 = 1f, ao1 = 1f, ao2 = 1f, ao3 = 1f;
        if (modelResource.isAmbientocclusion()){
            ao0 = testAo(c0, faceDir);
            ao1 = testAo(c1, faceDir);
            ao2 = testAo(c2, faceDir);
            ao3 = testAo(c3, faceDir);
        }

        FusionResources.FaceInfo connecting = source.fusion;
        Quad quad = this.quad.set(c0, c1, c2, c3, uvs, ao0, ao1, ao2, ao3,
                textureId, tintR, tintG, tintB, emissiveBlockLight, sunLight);
        if (connecting == null || !connectedFace(connecting, element, quad, source.self)) {
            emit(quad);
        }

        //if is top face set model-color
        float a = faceRotationVector.y;
        if (a > 0.01 && texturePath != null) {
            Texture texture = texturePath.getResource(resourcePack::getTexture);
            if (texture != null) {
                mapColor.set(texture.getColorPremultiplied());
                if (tint != null) {
                    mapColor.multiply(tint);
                }

                // apply light
                float combinedLight = Math.max(sunLight / 15f, blockLight / 15f);
                combinedLight = (1 - renderSettings.getAmbientLight()) * combinedLight + renderSettings.getAmbientLight();
                mapColor.r *= combinedLight;
                mapColor.g *= combinedLight;
                mapColor.b *= combinedLight;

                if (mapColor.a > blockColorOpacity)
                    blockColorOpacity = mapColor.a;

                blockColor.add(mapColor);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Fusion connecting textures

    private final Quad quad = new Quad();
    private final Quad part = new Quad();
    private final VectorM3f texRight = new VectorM3f(0, 0, 0), texUp = new VectorM3f(0, 0, 0);
    private final int[] right = new int[3], up = new int[3], normal = new int[3], defRight = new int[3], defUp = new int[3];

    /**
     * Emits the face with the tile(s) its neighbours call for. Returns false if the face's texture axes cannot be
     * worked out; the caller then emits it unchanged.
     */
    private boolean connectedFace(FusionResources.FaceInfo info, Element element, Quad q, BlockState self) {
        ConnectingTexture texture = info.texture();

        // Directions (model space) in which the texture's u grows and v shrinks: the tile's right and up.
        float e1x = q.p[1].x - q.p[0].x, e1y = q.p[1].y - q.p[0].y, e1z = q.p[1].z - q.p[0].z;
        float e2x = q.p[3].x - q.p[0].x, e2y = q.p[3].y - q.p[0].y, e2z = q.p[3].z - q.p[0].z;
        float d1u = q.u[1] - q.u[0], d1v = q.v[1] - q.v[0];
        float d2u = q.u[3] - q.u[0], d2v = q.v[3] - q.v[0];
        float det = d1u * d2v - d2u * d1v;
        if (Math.abs(det) < EPSILON) return false;
        texRight.set((e1x * d2v - e2x * d1v) / det, (e1y * d2v - e2y * d1v) / det, (e1z * d2v - e2z * d1v) / det);
        texUp.set(-(e2x * d1u - e1x * d2u) / det, -(e2y * d1u - e1y * d2u) / det, -(e2z * d1u - e1z * d2u) / det);
        texRight.rotateAndScale(element.getRotation().getMatrix());
        texUp.rotateAndScale(element.getRotation().getMatrix());
        makeRotationRelative(texRight);
        makeRotationRelative(texUp);
        if (!toAxis(texRight, right) || !toAxis(texUp, up) || !toAxis(faceRotationVector, normal)) return false;

        // Fusion's rules name neighbours relative to the face's default texture orientation
        defaultFrame(normal);

        int mask = 0;
        for (ConnectionDirection d : ConnectionDirection.values()) {
            int dx = right[0] * d.right + up[0] * d.up;
            int dy = right[1] * d.right + up[1] * d.up;
            int dz = right[2] * d.right + up[2] * d.up;
            BlockState other = appearance(block.getNeighborBlock(dx, dy, dz), self);
            BlockState inFront = block.getNeighborBlock(dx + normal[0], dy + normal[1], dz + normal[2]).getBlockState();
            int r = dx * defRight[0] + dy * defRight[1] + dz * defRight[2];
            int u = dx * defUp[0] + dy * defUp[1] + dz * defUp[2];
            ConnectionDirection named = (r == 0 && u == 0) ? d : ConnectionDirection.of(r, u);
            if (info.predicate().test(new ConnectionPredicate.Context(self, other, inFront, named))) mask |= d.bit();
        }

        if (DEBUG != null && block.getX() == DEBUG[0] && block.getY() == DEBUG[1] && block.getZ() == DEBUG[2]) {
            StringBuilder sb = new StringBuilder("[framed-fusion] debug " + block.getBlockState() + " face n=" + Arrays.toString(normal)
                    + " right=" + Arrays.toString(right) + " up=" + Arrays.toString(up) + " mask=" + Integer.toBinaryString(mask));
            for (ConnectionDirection d : ConnectionDirection.values()) {
                int dx = right[0] * d.right + up[0] * d.up, dy = right[1] * d.right + up[1] * d.up, dz = right[2] * d.right + up[2] * d.up;
                sb.append("\n   ").append(d).append(" (").append(dx).append(',').append(dy).append(',').append(dz).append(") ")
                        .append(block.getNeighborBlock(dx, dy, dz).getBlockState());
            }
            de.bluecolored.bluemap.core.logger.Logger.global.logInfo(sb.toString());
        }

        if (texture.layout() != Layout.PIECED) {
            int tile = texture.layout().tile(mask);
            for (int i = 0; i < 4; i++) {
                float u = q.u[i], v = q.v[i];
                q.u[i] = texture.u(tile, u);
                q.v[i] = texture.v(tile, v);
            }
            emit(q);
            return true;
        }

        piecedFace(texture, q, mask);
        return true;
    }

    /** Splits the face at the middle of its texture and gives every quarter its own tile. */
    private void piecedFace(ConnectingTexture texture, Quad q, int mask) {
        // the face as P(a, b) = p0 + a (p1 - p0) + b (p3 - p0), same for the uv
        float[] as = splits(q.u[1] - q.u[0], q.u[3] - q.u[0], q.u[0], q.v[1] - q.v[0], q.v[3] - q.v[0], q.v[0], true);
        float[] bs = splits(q.u[1] - q.u[0], q.u[3] - q.u[0], q.u[0], q.v[1] - q.v[0], q.v[3] - q.v[0], q.v[0], false);
        for (int i = 0; i + 1 < as.length; i++) {
            for (int j = 0; j + 1 < bs.length; j++) {
                q.sub(part, as[i], bs[j], as[i + 1], bs[j + 1]);
                float cu = (part.u[0] + part.u[2]) / 2, cv = (part.v[0] + part.v[2]) / 2;
                int tile = Layout.piecedTile(cv < 0.5f, cu < 0.5f, mask);
                for (int k = 0; k < 4; k++) {
                    float u = part.u[k], v = part.v[k];
                    part.u[k] = texture.u(tile, u);
                    part.v[k] = texture.v(tile, v);
                }
                emit(part);
            }
        }
    }

    /** Parameters along a (or b) where u or v crosses 0.5, plus 0 and 1. */
    private static float[] splits(float duA, float duB, float u0, float dvA, float dvB, float v0, boolean alongA) {
        float[] out = new float[4];
        int n = 0;
        out[n++] = 0;
        float du = alongA ? duA : duB, dv = alongA ? dvA : dvB;
        if (Math.abs(du) > EPSILON) {
            float t = (0.5f - u0) / du;
            if (t > EPSILON && t < 1 - EPSILON && Math.abs(alongA ? duB : duA) < EPSILON) out[n++] = t;
        }
        if (Math.abs(dv) > EPSILON) {
            float t = (0.5f - v0) / dv;
            if (t > EPSILON && t < 1 - EPSILON && Math.abs(alongA ? dvB : dvA) < EPSILON) out[n++] = t;
        }
        out[n++] = 1;
        float[] result = Arrays.copyOf(out, n);
        Arrays.sort(result);
        return result;
    }

    /** What a neighbour looks like for Fusion (Framed Blocks show their camouflage). */
    protected BlockState appearance(ExtendedBlock block, BlockState self) {
        if (block.getBlockEntity() instanceof FramedBlockEntity framed) {
            // Framed shows the camouflage of the part on the touching side; the part that looks like us is a
            // good guess for double blocks
            BlockState second = framed.camo(1);
            if (second != null && second.getFormatted().equals(self.getFormatted())) return second;
            BlockState camo = framed.camo(0);
            if (camo != null) return camo;
        }
        return block.getBlockState();
    }

    /** Whether the neighbour in {@code dir} has a face that exactly covers the current face on that side. */
    protected boolean coveredBy(ExtendedBlock neighbour, Direction dir) {
        return false;
    }

    private void defaultFrame(int[] n) {
        // up and right of an unrotated texture on a face pointing in n (as in vanilla block models)
        set(defRight, 0, 0, 0);
        set(defUp, 0, 0, 0);
        if (n[1] > 0) { set(defUp, 0, 0, -1); set(defRight, 1, 0, 0); }
        else if (n[1] < 0) { set(defUp, 0, 0, 1); set(defRight, 1, 0, 0); }
        else if (n[2] < 0) { set(defUp, 0, 1, 0); set(defRight, -1, 0, 0); }
        else if (n[2] > 0) { set(defUp, 0, 1, 0); set(defRight, 1, 0, 0); }
        else if (n[0] < 0) { set(defUp, 0, 1, 0); set(defRight, 0, 0, 1); }
        else if (n[0] > 0) { set(defUp, 0, 1, 0); set(defRight, 0, 0, -1); }
    }

    private static void set(int[] v, int x, int y, int z) {
        v[0] = x; v[1] = y; v[2] = z;
    }

    /** Rounds a direction to the nearest axis; false if it has no length. */
    private static boolean toAxis(VectorM3f v, int[] out) {
        float ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax < EPSILON && ay < EPSILON && az < EPSILON) return false;
        set(out, 0, 0, 0);
        if (ax >= ay && ax >= az) out[0] = v.x > 0 ? 1 : -1;
        else if (ay >= az) out[1] = v.y > 0 ? 1 : -1;
        else out[2] = v.z > 0 ? 1 : -1;
        return true;
    }

    private void emit(Quad q) {
        blockModel.initialize();
        blockModel.add(2);

        TileModel tileModel = blockModel.getTileModel();
        int face1 = blockModel.getStart();
        int face2 = face1 + 1;

        VectorM3f[] p = q.p;
        tileModel.setPositions(face1, p[0].x, p[0].y, p[0].z, p[1].x, p[1].y, p[1].z, p[2].x, p[2].y, p[2].z);
        tileModel.setPositions(face2, p[0].x, p[0].y, p[0].z, p[2].x, p[2].y, p[2].z, p[3].x, p[3].y, p[3].z);

        tileModel.setMaterialIndex(face1, q.textureId);
        tileModel.setMaterialIndex(face2, q.textureId);

        tileModel.setUvs(face1, q.u[0], q.v[0], q.u[1], q.v[1], q.u[2], q.v[2]);
        tileModel.setUvs(face2, q.u[0], q.v[0], q.u[2], q.v[2], q.u[3], q.v[3]);

        tileModel.setColor(face1, q.r, q.g, q.b);
        tileModel.setColor(face2, q.r, q.g, q.b);

        tileModel.setBlocklight(face1, q.blockLight);
        tileModel.setBlocklight(face2, q.blockLight);

        tileModel.setSunlight(face1, q.sunLight);
        tileModel.setSunlight(face2, q.sunLight);

        tileModel.setAOs(face1, q.ao[0], q.ao[1], q.ao[2]);
        tileModel.setAOs(face2, q.ao[0], q.ao[2], q.ao[3]);
    }

    /** One face: corners p0..p3 (p2 opposite p0) with their uv and ambient occlusion, and what they share. */
    private static final class Quad {
        final VectorM3f[] p = {new VectorM3f(0, 0, 0), new VectorM3f(0, 0, 0), new VectorM3f(0, 0, 0), new VectorM3f(0, 0, 0)};
        final float[] u = new float[4], v = new float[4], ao = new float[4];
        int textureId, blockLight, sunLight;
        float r, g, b;

        Quad set(VectorM3f c0, VectorM3f c1, VectorM3f c2, VectorM3f c3, VectorM2f[] uvs,
                 float ao0, float ao1, float ao2, float ao3,
                 int textureId, float r, float g, float b, int blockLight, int sunLight) {
            p[0].set(c0.x, c0.y, c0.z); p[1].set(c1.x, c1.y, c1.z); p[2].set(c2.x, c2.y, c2.z); p[3].set(c3.x, c3.y, c3.z);
            for (int i = 0; i < 4; i++) { u[i] = uvs[i].x; v[i] = uvs[i].y; }
            ao[0] = ao0; ao[1] = ao1; ao[2] = ao2; ao[3] = ao3;
            this.textureId = textureId; this.r = r; this.g = g; this.b = b;
            this.blockLight = blockLight; this.sunLight = sunLight;
            return this;
        }

        /** The part of this face between (a0, b0) and (a1, b1), written into {@code out}. */
        void sub(Quad out, float a0, float b0, float a1, float b1) {
            out.textureId = textureId; out.r = r; out.g = g; out.b = b;
            out.blockLight = blockLight; out.sunLight = sunLight;
            point(out, 0, a0, b0);
            point(out, 1, a1, b0);
            point(out, 2, a1, b1);
            point(out, 3, a0, b1);
        }

        private void point(Quad out, int i, float a, float b) {
            out.p[i].set(
                    p[0].x + a * (p[1].x - p[0].x) + b * (p[3].x - p[0].x),
                    p[0].y + a * (p[1].y - p[0].y) + b * (p[3].y - p[0].y),
                    p[0].z + a * (p[1].z - p[0].z) + b * (p[3].z - p[0].z));
            out.u[i] = u[0] + a * (u[1] - u[0]) + b * (u[3] - u[0]);
            out.v[i] = v[0] + a * (v[1] - v[0]) + b * (v[3] - v[0]);
            out.ao[i] = (1 - a) * (1 - b) * ao[0] + a * (1 - b) * ao[1] + a * b * ao[2] + (1 - a) * b * ao[3];
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hooks for subclasses (Framed Blocks)

    /** Where a face gets its texture from; reused for every face. */
    protected static final class FaceSource {
        public ResourcePath<Texture> texturePath;
        public int tintIndex;
        /** Whose tint to use, see {@link #tintColor(int)}. */
        public int tintPart;
        public FusionResources.FaceInfo fusion;
        /** What the face's block looks like to Fusion. */
        public BlockState self;
    }

    private final FaceSource faceSource = new FaceSource();
    private final Color[] partTints = {new Color(), new Color()};

    /** Whether this renderer draws the model itself; otherwise BlueMap's renderer does. */
    protected boolean rendersItself(FusionResources.ModelInfo info) {
        return info != FusionResources.ModelInfo.NONE;
    }

    /** Called before a block is drawn by this renderer. */
    protected void beginBlock(BlockNeighborhood block) {
        partTints[0].set(0, 0, 0, -1, true);
        partTints[1].set(0, 0, 0, -1, true);
    }

    /** Texture, tint and connecting data of a face, by default straight from the model. */
    protected FaceSource faceSource(Face face, int elementIndex, Direction faceDir) {
        FaceSource s = faceSource;
        s.texturePath = face.getTexture().getTexturePath(modelResource.getTextures()::get);
        s.tintIndex = face.getTintindex();
        s.tintPart = -1;
        s.fusion = fusionInfo.face(elementIndex, faceDir);
        s.self = block.getBlockState();
        return s;
    }

    /** Extra culling of a model face with a cullface against the neighbour there. */
    protected boolean cullsBoxFace(Face face, ExtendedBlock neighbour) {
        return false;
    }

    /** Whether the model drawn right now fills the whole block. */
    protected boolean modelIsFullCube() {
        return modelResource != null && modelResource.isOccluding();
    }

    /** Draws geometry that is not part of the model; called after the model's elements. */
    protected void renderExtra() {}

    /**
     * Draws one flat polygon (block pixels, corners counter-clockwise seen from outside) for {@link #renderExtra}:
     * light from the neighbour in {@code lightDir}, skipped if the neighbour in {@code cullDir} covers it.
     */
    protected void emitPolygon(float[][] points, float[][] uvs, Direction lightDir, Direction cullDir, boolean up,
                               ResourcePath<Texture> texturePath, Color tint) {
        ExtendedBlock facing = block.getNeighborBlock(lightDir.toVector().getX(), lightDir.toVector().getY(), lightDir.toVector().getZ());
        LightData own = block.getLightData(), other = facing.getLightData();
        int sunLight = Math.max(own.getSkyLight(), other.getSkyLight());
        int blockLight = Math.max(own.getBlockLight(), other.getBlockLight());
        if (block.isRemoveIfCave() &&
                (renderSettings.isCaveDetectionUsesBlockLight() ? Math.max(blockLight, sunLight) : sunLight) == 0) return;
        if (renderSettings.isRenderTopOnly() && !up) return;
        if (cullDir != null) {
            Vector3i v = cullDir.toVector();
            ExtendedBlock neighbour = block.getNeighborBlock(v.getX(), v.getY(), v.getZ());
            if (neighbour.getProperties().isCulling()) return;
            if (coveredBy(neighbour, cullDir)) return;
        }

        int textureId = textureGallery.get(texturePath);
        float r = tint != null ? tint.r : 1f, g = tint != null ? tint.g : 1f, b = tint != null ? tint.b : 1f;
        TileModel tileModel = blockModel.getTileModel();
        for (int i = 1; i + 1 < points.length; i++) {
            blockModel.initialize();
            blockModel.add(1);
            int f = blockModel.getStart();
            float[] p0 = points[0], p1 = points[i], p2 = points[i + 1];
            tileModel.setPositions(f, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
            tileModel.setUvs(f, uvs[0][0], uvs[0][1], uvs[i][0], uvs[i][1], uvs[i + 1][0], uvs[i + 1][1]);
            tileModel.setMaterialIndex(f, textureId);
            tileModel.setColor(f, r, g, b);
            tileModel.setBlocklight(f, blockLight);
            tileModel.setSunlight(f, sunLight);
            tileModel.setAOs(f, 1f, 1f, 1f);
        }

        if (up && texturePath != null) {
            Texture texture = texturePath.getResource(resourcePack::getTexture);
            if (texture != null) {
                mapColor.set(texture.getColorPremultiplied());
                if (tint != null) mapColor.multiply(tint);
                float combinedLight = Math.max(sunLight / 15f, blockLight / 15f);
                combinedLight = (1 - renderSettings.getAmbientLight()) * combinedLight + renderSettings.getAmbientLight();
                mapColor.r *= combinedLight;
                mapColor.g *= combinedLight;
                mapColor.b *= combinedLight;
                if (mapColor.a > blockColorOpacity) blockColorOpacity = mapColor.a;
                blockColor.add(mapColor);
            }
        }
    }

    /** Tint of the block itself (-1) or of a camouflage part (0, 1), see {@link #tintState(int)}. */
    protected Color tintColor(int part) {
        if (part < 0) {
            if (tintColor.a < 0) blockColorCalculator.getBlockColor(block, tintColor);
            return tintColor;
        }
        Color color = partTints[part];
        if (color.a < 0) blockColorCalculator.getBlockColor(new StateOverride(block, tintState(part)), color);
        return color;
    }

    /** The block state whose tint a camouflage part uses. */
    protected BlockState tintState(int part) {
        return block.getBlockState();
    }

    /** The current block. */
    protected BlockNeighborhood block() {
        return block;
    }

    /** The face's direction in the world, from the last face set up (valid inside {@link #faceSource}). */
    protected Direction worldFaceDirection() {
        int[] n = new int[3];
        if (!toAxis(faceRotationVector, n)) return null;
        for (Direction d : Direction.values()) {
            Vector3i v = d.toVector();
            if (v.getX() == n[0] && v.getY() == n[1] && v.getZ() == n[2]) return d;
        }
        return null;
    }

    protected ResourcePack resourcePack() {
        return resourcePack;
    }

    protected FusionResources fusion() {
        return fusion;
    }

    /** A block that reports another block state, to compute the tint of a camouflage. */
    private static final class StateOverride extends BlockNeighborhood {
        private final BlockState state;

        StateOverride(BlockNeighborhood source, BlockState state) {
            super(source, source.getResourcePack(), source.getRenderSettings(), source.getDimensionType());
            this.state = state;
            copyFrom(source);
        }

        @Override
        public BlockState getBlockState() {
            return state;
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private ExtendedBlock getRotationRelativeBlock(Direction direction){
        return getRotationRelativeBlock(direction.toVector());
    }

    private ExtendedBlock getRotationRelativeBlock(Vector3i direction){
        return getRotationRelativeBlock(
                direction.getX(),
                direction.getY(),
                direction.getZ()
        );
    }

    private final VectorM3f rotationRelativeBlockDirection = new VectorM3f(0, 0, 0);
    private ExtendedBlock getRotationRelativeBlock(int dx, int dy, int dz){
        rotationRelativeBlockDirection.set(dx, dy, dz);
        makeRotationRelative(rotationRelativeBlockDirection);

        return block.getNeighborBlock(
                Math.round(rotationRelativeBlockDirection.x),
                Math.round(rotationRelativeBlockDirection.y),
                Math.round(rotationRelativeBlockDirection.z)
        );
    }

    private void makeRotationRelative(VectorM3f direction){
        if (variant.isTransformed())
            direction.rotateAndScale(variant.getTransformMatrix());
    }

    private float testAo(VectorM3f vertex, Direction dir){
        Vector3i dirVec = dir.toVector();
        int occluding = 0;

        int x = 0;
        if (vertex.x == 16){
            x = 1;
        } else if (vertex.x == 0){
            x = -1;
        }

        int y = 0;
        if (vertex.y == 16){
            y = 1;
        } else if (vertex.y == 0){
            y = -1;
        }

        int z = 0;
        if (vertex.z == 16){
            z = 1;
        } else if (vertex.z == 0){
            z = -1;
        }


        if (x * dirVec.getX() + y * dirVec.getY() > 0){
            if (getRotationRelativeBlock(x, y, 0).getProperties().isOccluding()) occluding++;
        }

        if (x * dirVec.getX() + z * dirVec.getZ() > 0){
            if (getRotationRelativeBlock(x, 0, z).getProperties().isOccluding()) occluding++;
        }

        if (y * dirVec.getY() + z * dirVec.getZ() > 0){
            if (getRotationRelativeBlock(0, y, z).getProperties().isOccluding()) occluding++;
        }

        if (x * dirVec.getX() + y * dirVec.getY() + z * dirVec.getZ() > 0){
            if (getRotationRelativeBlock(x, y, z).getProperties().isOccluding()) occluding++;
        }

        if (occluding > 3) occluding = 3;
        return  Math.max(0f, Math.min(1f - occluding * 0.25f, 1f));
    }

    private static float hashToFloat(int x, int z, long seed) {
        final long hash = x * 73428767L ^ z * 4382893L ^ seed * 457;
        return (hash * (hash + 456149) & 0x00ffffff) / (float) 0x01000000;
    }

}
