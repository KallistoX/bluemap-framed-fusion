package io.github.kallistox.framedfusion.framed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullTest {

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Every face must wind counter-clockwise seen from outside (BlueMap draws front faces only). */
    private static void assertOutwardWinding(List<Hull.Polygon> faces) {
        for (Hull.Polygon f : faces) {
            float[] a = f.points().get(0), b = f.points().get(1), c = f.points().get(2);
            float[] n = cross(new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]}, new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]});
            float d = n[0] * f.normal()[0] + n[1] * f.normal()[1] + n[2] * f.normal()[2];
            assertTrue(d > 0, "face winds inwards");
        }
    }

    @Test
    void cube() {
        Solid cube = Solid.prism(0, false, 0, 0, 0, 16, 16, 0, 16, 16);
        List<Hull.Polygon> faces = Hull.faces(cube.points());
        assertEquals(6, faces.size());
        for (Hull.Polygon f : faces) assertEquals(4, f.points().size());
        assertOutwardWinding(faces);
    }

    @Test
    void slopeWedge() {
        // framed_slope facing north, bottom: full bottom and back (north), sloped face towards south-up
        Solid slope = Solid.prism(0, false, 0, 0, 0, 16, 16, 0);
        List<Hull.Polygon> faces = Hull.faces(slope.points());
        assertEquals(5, faces.size());  // bottom, back, slope, two side triangles
        assertOutwardWinding(faces);
        boolean sloped = faces.stream().anyMatch(f -> f.normal()[1] > 0.5f && f.normal()[2] > 0.5f);
        assertTrue(sloped, "sloped face should point up and south");
    }

    @Test
    void turning() {
        // the back of a north-facing slope (z = 0) lies at x = 16 when facing east
        Solid east = Solid.prism(0, false, 0, 0, 0, 16, 16, 0).rotateY(1);
        assertTrue(east.points().stream().filter(p -> p[1] == 16).allMatch(p -> p[0] == 16));
        // a slope panel pointing right (east) is thin at x = 16
        Solid right = Solid.prism(0, false, 0, 0, 0, 8, 16, 0).orient("east");
        assertTrue(right.points().stream().filter(p -> p[0] == 16).allMatch(p -> p[2] == 0));
    }
}
