package io.github.kallistox.framedfusion.framed;

import java.util.ArrayList;
import java.util.List;

/**
 * A convex solid in block pixels (0..16) for Framed Blocks' sloped shapes, which BlueMap's box models cannot express.
 * Built in a canonical orientation (facing north) and then turned into place.
 *
 * @param points corners of the solid
 * @param part   camouflage part it shows (0 = camo, 1 = camo_two, -1 = empty frame)
 * @param ySlope      sloped faces take the camouflage's top/bottom texture instead of its front texture
 * @param slopeSource world direction whose camouflage texture the sloped faces show, {@code null} = derived from the
 *                    face (front, or top/bottom with {@code ySlope})
 */
public record Solid(List<float[]> points, int part, boolean ySlope, String slopeSource) {

    public Solid(List<float[]> points, int part, boolean ySlope) {
        this(points, part, ySlope, null);
    }

    public Solid withSlopeSource(String direction) {
        return new Solid(points, part, ySlope, direction);
    }

    /** Both x-ends (0 and 16) of a profile in the y/z plane given as (y, z) pairs. */
    public static Solid prism(int part, boolean ySlope, float... yz) {
        List<float[]> points = new ArrayList<>();
        for (float x : new float[]{0, 16}) {
            for (int i = 0; i + 1 < yz.length; i += 2) points.add(new float[]{x, yz[i], yz[i + 1]});
        }
        return new Solid(points, part, ySlope);
    }

    private Solid map(Mapper m) {
        List<float[]> out = new ArrayList<>(points.size());
        for (float[] p : points) out.add(m.map(p[0], p[1], p[2]));
        return new Solid(out, part, ySlope, slopeSource);
    }

    private interface Mapper {
        float[] map(float x, float y, float z);
    }

    /** Turns clockwise (seen from above) by {@code steps} quarter turns: north -> east -> south -> west. */
    public Solid rotateY(int steps) {
        Solid s = this;
        for (int i = 0; i < Math.floorMod(steps, 4); i++) s = s.map((x, y, z) -> new float[]{16 - z, y, x});
        return s;
    }

    /** Upside down. */
    public Solid mirrorY() {
        return map((x, y, z) -> new float[]{x, 16 - y, z});
    }

    /** Swaps x and y: a profile in the y/z plane (extruded along x) becomes a profile in the x/z plane (along y). */
    public Solid horizontal() {
        return map((x, y, z) -> new float[]{y, x, z});
    }

    /** Moves by (dx, dy, dz) pixels. */
    public Solid translate(float dx, float dy, float dz) {
        return map((x, y, z) -> new float[]{x + dx, y + dy, z + dz});
    }

    /**
     * For shapes built for facing north with their "orientation" pointing up: turns the solid around the north-south
     * axis so that up points to {@code orientation} (up, down, east = right of north, west = left of north).
     */
    public Solid orient(String orientation) {
        return switch (orientation) {
            case "down" -> map((x, y, z) -> new float[]{16 - x, 16 - y, z});
            case "east" -> map((x, y, z) -> new float[]{y, 16 - x, z});
            case "west" -> map((x, y, z) -> new float[]{16 - y, x, z});
            default -> this;
        };
    }
}
