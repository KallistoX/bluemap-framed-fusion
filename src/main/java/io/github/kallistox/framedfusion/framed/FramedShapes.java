package io.github.kallistox.framedfusion.framed;

import de.bluecolored.bluemap.core.world.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Framed Blocks' sloped shapes as convex solids. Framed Blocks builds them by cutting and tilting the faces of its
 * camouflage (Framed*Geometry classes); the solids below have the same corners. Returns {@code null} for states
 * that are not covered - those show the empty frame.
 */
public final class FramedShapes {

    private static final Map<String, Function<Map<String, String>, List<Solid>>> SHAPES = new HashMap<>();
    private static final Map<BlockState, List<Solid>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<Solid> NONE = List.of();

    static {
        // FramedSlopeGeometry: wedge with full bottom and back (facing), the front face tilted back by 45 degrees;
        // "horizontal" stands upright with the full side on the left (counter-clockwise of facing)
        SHAPES.put("framedblocks:framed_slope", p -> List.of(
                slope(0, facing(p), p.getOrDefault("type", "bottom"), bool(p, "yslope"))));
        // FramedDoubleSlopeBlock: slope + the opposite slope
        SHAPES.put("framedblocks:framed_double_slope", p -> {
            String type = p.getOrDefault("type", "bottom");
            return List.of(slope(0, facing(p), type, bool(p, "yslope")),
                    slope(1, opposite(facing(p)), oppositeType(type), bool(p, "yslope")));
        });
        // FramedSlopeEdgeGeometry: a 45 degree wedge of half size in the back bottom quarter; alt_type puts it in the
        // front top quarter
        SHAPES.put("framedblocks:framed_slope_edge", p -> List.of(
                slopeEdge(0, facing(p), p.getOrDefault("type", "bottom"), bool(p, "alt_type"), bool(p, "yslope"))));
        // FramedElevatedSlopeEdgeGeometry: bottom half full, a slope edge on top of it
        SHAPES.put("framedblocks:framed_elevated_slope_edge", p -> List.of(
                elevatedSlopeEdge(0, facing(p), p.getOrDefault("type", "bottom"), bool(p, "yslope"))));
        // FramedElevatedDoubleSlopeEdgeBlock: elevated slope edge + the slope edge that fills the rest
        SHAPES.put("framedblocks:framed_elevated_double_slope_edge", p -> {
            String type = p.getOrDefault("type", "bottom");
            return List.of(elevatedSlopeEdge(0, facing(p), type, bool(p, "yslope")),
                    slopeEdge(1, opposite(facing(p)), oppositeType(type), false, bool(p, "yslope")));
        });
        // FramedCompoundSlopePanelGeometry: a panel tilted like two slope panels, back half on top, front half below
        SHAPES.put("framedblocks:framed_compound_slope_panel", p -> List.of(
                Solid.prism(0, bool(p, "yslope"), 16, 0, 16, 8, 0, 16, 0, 8)
                        .orient(orientation(facing(p), p.getOrDefault("rotation", "up"))).rotateY(quarter(p))));
        // FramedPrismGeometry: triangular prism, the wide side opposite to the facing, the tip half a block towards it
        SHAPES.put("framedblocks:framed_prism", p -> {
            String[] fa = p.getOrDefault("facing_axis", "up_x").split("_");
            if (!fa[0].equals("up") && !fa[0].equals("down")) return null;
            Solid s = Solid.prism(0, bool(p, "yslope"), 16, 0, 16, 16, 8, 8);  // facing down, along x
            if (fa[0].equals("up")) s = s.mirrorY();
            return List.of(fa[1].equals("z") ? s.rotateY(1) : s);
        });
        // FramedSlopePanelGeometry: the same wedge at half the depth, in the back half of the block (front = front half)
        SHAPES.put("framedblocks:framed_slope_panel", p -> List.of(
                slopePanel(0, facing(p), p.getOrDefault("rotation", "up"), bool(p, "front"), bool(p, "yslope"))));
        // FramedExtendedSlopePanelGeometry: a panel in the back half plus a slope panel in front of it
        SHAPES.put("framedblocks:framed_extended_slope_panel", p -> List.of(
                extendedSlopePanel(0, facing(p), p.getOrDefault("rotation", "up"), bool(p, "yslope"))));
        // FramedExtendedDoubleSlopePanelBlock: extended slope panel + the slope panel that fills the rest
        SHAPES.put("framedblocks:framed_extended_double_slope_panel", p -> {
            String facing = facing(p), rotation = p.getOrDefault("rotation", "up");
            boolean ySlope = bool(p, "yslope");
            String rotationTwo = switch (rotation) { case "up" -> "down"; case "down" -> "up"; default -> rotation; };
            return List.of(extendedSlopePanel(0, facing, rotation, ySlope),
                    slopePanel(1, opposite(facing), rotationTwo, false, ySlope));
        });
    }

    private FramedShapes() {}

    public static boolean covers(String blockId) {
        return SHAPES.containsKey(blockId);
    }

    /** Solids for a block state, {@code null} if the block has no coded shape or the state is not covered. */
    public static List<Solid> of(BlockState state) {
        var shape = SHAPES.get(state.getFormatted());
        if (shape == null) return null;
        List<Solid> solids = CACHE.computeIfAbsent(state, s -> {
            List<Solid> r = shape.apply(s.getProperties());
            return r != null ? r : NONE;
        });
        return solids == NONE ? null : solids;
    }

    private static Solid slope(int part, String facing, String type, boolean ySlope) {
        return upright(Solid.prism(part, ySlope, 0, 0, 0, 16, 16, 0), facing, type, ySlope);
    }

    private static Solid slopeEdge(int part, String facing, String type, boolean alt, boolean ySlope) {
        Solid s = alt ? Solid.prism(part, ySlope, 8, 8, 8, 16, 16, 8) : Solid.prism(part, ySlope, 0, 0, 0, 8, 8, 0);
        return upright(s, facing, type, ySlope);
    }

    private static Solid elevatedSlopeEdge(int part, String facing, String type, boolean ySlope) {
        return upright(Solid.prism(part, ySlope, 0, 0, 0, 16, 8, 16, 16, 8, 16, 0), facing, type, ySlope);
    }

    /**
     * Places a shape built as bottom type for facing north: top = upside down, horizontal = standing upright with the
     * bottom turned to the left side (there ySlope means the right side's texture, as in Framed).
     */
    private static Solid upright(Solid s, String facing, String type, boolean ySlope) {
        switch (type) {
            case "top" -> s = s.mirrorY();
            case "horizontal" -> {
                s = s.horizontal();
                if (ySlope) s = s.withSlopeSource(clockwise(facing));
            }
            default -> {}
        }
        return s.rotateY(quarter(facing));
    }

    private static String oppositeType(String type) {
        return switch (type) { case "top" -> "bottom"; case "bottom" -> "top"; default -> type; };
    }

    private static String clockwise(String facing) {
        return switch (facing) { case "east" -> "south"; case "south" -> "west"; case "west" -> "north"; default -> "east"; };
    }

    private static Solid slopePanel(int part, String facing, String rotation, boolean front, boolean ySlope) {
        Solid s = Solid.prism(part, ySlope, 0, 0, 0, 8, 16, 0).orient(orientation(facing, rotation));
        if (front) s = s.translate(0, 0, 8);
        return s.rotateY(quarter(facing));
    }

    private static Solid extendedSlopePanel(int part, String facing, String rotation, boolean ySlope) {
        return Solid.prism(part, ySlope, 0, 0, 0, 16, 16, 0, 16, 8)
                .orient(orientation(facing, rotation)).rotateY(quarter(facing));
    }

    /** Framed's HorizontalRotation as seen in the canonical (north) frame: right = clockwise of north = east. */
    private static String orientation(String facing, String rotation) {
        return switch (rotation) {
            case "down" -> "down";
            case "right" -> "east";
            case "left" -> "west";
            default -> "up";
        };
    }

    private static int quarter(Map<String, String> p) {
        return quarter(facing(p));
    }

    private static int quarter(String facing) {
        return switch (facing) { case "east" -> 1; case "south" -> 2; case "west" -> 3; default -> 0; };
    }

    private static String facing(Map<String, String> p) {
        return p.getOrDefault("facing", "north");
    }

    private static String opposite(String facing) {
        return switch (facing) { case "east" -> "west"; case "south" -> "north"; case "west" -> "east"; default -> "south"; };
    }

    private static boolean bool(Map<String, String> p, String key) {
        return "true".equals(p.get(key));
    }
}
