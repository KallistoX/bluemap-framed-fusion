package io.github.kallistox.framedfusion.framed;

import de.bluecolored.bluemap.core.world.BlockState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramedShapesTest {

    /** Volume of a convex solid in cubic pixels (divergence theorem over its hull faces). */
    static double volume(Solid solid) {
        double v = 0;
        for (Hull.Polygon f : Hull.faces(solid.points())) {
            float[] a = f.points().get(0);
            for (int i = 1; i + 1 < f.points().size(); i++) {
                float[] b = f.points().get(i), c = f.points().get(i + 1);
                v += (a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6.0;
            }
        }
        return v;
    }

    static List<Solid> shape(String block, Map<String, String> props) {
        List<Solid> solids = FramedShapes.of(new BlockState("framedblocks:" + block, props));
        assertNotNull(solids, block + " " + props);
        return solids;
    }

    /** Volume of the common part of two convex solids (0 if they only touch in a face, edge or point). */
    static double overlap(Solid a, Solid b) {
        List<float[]> pts = a.intersect(b).points();
        if (pts.size() < 4 || flat(pts)) return 0;
        return volume(new Solid(pts, a.part(), false));
    }

    static boolean flat(List<float[]> pts) {
        float[] o = pts.get(0);
        for (float[] p : pts) for (float[] q : pts) {
            float[] n = Hull.cross(Hull.sub(p, o), Hull.sub(q, o));
            if (Hull.dot(n, n) < 1e-4f) continue;
            for (float[] r : pts) if (Math.abs(Hull.dot(n, Hull.sub(r, o))) > 1e-2f * Math.sqrt(Hull.dot(n, n))) return false;
            return true;
        }
        return true;
    }

    /**
     * Solids stay inside the block, a part made of two solids counts their overlap once, and the parts of a double
     * block do not overlap each other - so double blocks fill the block exactly.
     */
    static void assertFills(String block, Map<String, String> props, double expected) {
        List<Solid> solids = shape(block, props);
        double total = 0;
        for (int i = 0; i < solids.size(); i++) {
            Solid s = solids.get(i);
            for (float[] p : s.points()) for (float c : p) assertTrue(c > -1e-3 && c < 16 + 1e-3, block + " leaves the block");
            total += volume(s);
            for (int j = i + 1; j < solids.size(); j++) {
                double o = overlap(s, solids.get(j));
                if (s.part() == solids.get(j).part()) total -= o;  // union of two solids of one part
                else assertEquals(0, o, 1e-2, block + " " + props + ": parts overlap");
            }
        }
        assertEquals(expected, total, 1e-2, block + " " + props);
    }

    @Test
    void singleVolumes() {
        assertFills("framed_slope", Map.of("facing", "east", "type", "top", "yslope", "false"), 2048);
        assertFills("framed_slope", Map.of("facing", "south", "type", "horizontal", "yslope", "true"), 2048);
        assertFills("framed_slope_edge", Map.of("facing", "west", "type", "bottom", "alt_type", "true", "yslope", "false"), 512);
        assertFills("framed_elevated_slope_edge", Map.of("facing", "north", "type", "top", "yslope", "false"), 4096 - 512);
        assertFills("framed_slope_panel", Map.of("facing", "north", "rotation", "right", "front", "true", "yslope", "false"), 1024);
        assertFills("framed_compound_slope_panel", Map.of("facing", "east", "rotation", "up", "yslope", "false"), 2048);
        assertFills("framed_prism", Map.of("facing_axis", "down_x", "yslope", "true"), 1024);
        // outer corner: slab (2048) + the upper part where both elevated edges overlap
        double corner = volume(shape("framed_elevated_corner_slope_edge", Map.of("facing", "north", "type", "bottom", "yslope", "false")).get(0));
        assertTrue(corner > 2048 && corner < 4096 - 512, "elevated corner " + corner);
        // the corner lies towards north-west for facing north: its top reaches x, z < 8 only
        for (float[] pt : shape("framed_elevated_corner_slope_edge", Map.of("facing", "north", "type", "bottom", "yslope", "false")).get(0).points())
            if (pt[1] > 15.9f) assertTrue(pt[0] < 8.01f && pt[2] < 8.01f, "top of the corner not in the north-west");
    }

    @Test
    void doubleBlocksFillTheBlock() {
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            for (String type : new String[]{"bottom", "top", "horizontal"}) {
                assertFills("framed_double_slope", Map.of("facing", facing, "type", type, "yslope", "false"), 4096);
                assertFills("framed_elevated_double_slope_edge", Map.of("facing", facing, "type", type, "yslope", "false"), 4096);
            }
            for (String rotation : new String[]{"up", "down", "left", "right"}) {
                assertFills("framed_extended_double_slope_panel", Map.of("facing", facing, "rotation", rotation, "yslope", "false"), 4096);
                assertFills("framed_flat_ext_double_slope_panel_corner", Map.of("facing", facing, "rotation", rotation, "yslope", "false"), 4096);
            }
        }
    }
}
