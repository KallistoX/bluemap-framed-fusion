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

    /** Solids of one part stay inside the block, and double blocks fill it completely without overlap. */
    static void assertFills(String block, Map<String, String> props, double expected) {
        double total = 0;
        for (Solid s : shape(block, props)) {
            for (float[] p : s.points()) for (float c : p) assertTrue(c > -1e-3 && c < 16 + 1e-3, block + " leaves the block");
            total += volume(s);
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
            }
        }
    }
}
