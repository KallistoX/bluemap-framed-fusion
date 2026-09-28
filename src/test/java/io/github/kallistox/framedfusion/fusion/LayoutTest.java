package io.github.kallistox.framedfusion.fusion;

import org.junit.jupiter.api.Test;

import static io.github.kallistox.framedfusion.fusion.ConnectionDirection.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutTest {

    private static int mask(ConnectionDirection... dirs) {
        int m = 0;
        for (ConnectionDirection d : dirs) m |= d.bit();
        return m;
    }

    private static final int ALL = 0xFF;

    private static void full(int x, int y, ConnectionDirection... dirs) {
        assertEquals(x + y * 8, Layout.FULL.tile(mask(dirs)), () -> "full " + java.util.Arrays.toString(dirs));
    }

    @Test
    void fullLayoutSidesAndCorners() {
        full(0, 0);                                  // alone
        full(0, 0, TOP_LEFT, BOTTOM_RIGHT);          // corners alone do nothing
        full(3, 0, LEFT);
        full(1, 0, RIGHT);
        full(0, 3, TOP);
        full(0, 1, BOTTOM);
        full(2, 0, LEFT, RIGHT);
        full(0, 2, TOP, BOTTOM);
        full(3, 3, LEFT, TOP, TOP_LEFT);
        full(5, 1, LEFT, TOP);                       // inner corner missing
        full(1, 1, RIGHT, BOTTOM, BOTTOM_RIGHT);
        full(1, 2, TOP, RIGHT, BOTTOM, TOP_RIGHT, BOTTOM_RIGHT);
        full(6, 0, TOP, RIGHT, BOTTOM);
        assertEquals(2 + 2 * 8, Layout.FULL.tile(ALL));  // middle of a large area
        full(1, 4, TOP, RIGHT, BOTTOM, LEFT);        // cross, no corners
        full(7, 5, TOP, RIGHT, BOTTOM, LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT);
        full(0, 4, TOP, RIGHT, BOTTOM, LEFT, TOP_RIGHT, BOTTOM_LEFT);
        full(5, 5, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT);
    }

    @Test
    void lineLayouts() {
        assertEquals(0, Layout.HORIZONTAL.tile(0));
        assertEquals(1, Layout.HORIZONTAL.tile(mask(RIGHT)));
        assertEquals(2, Layout.HORIZONTAL.tile(mask(LEFT, RIGHT, TOP)));  // top is ignored
        assertEquals(3, Layout.HORIZONTAL.tile(mask(LEFT)));
        assertEquals(1, Layout.VERTICAL.tile(mask(BOTTOM)));
        assertEquals(2, Layout.VERTICAL.tile(mask(TOP, BOTTOM)));
        assertEquals(3, Layout.VERTICAL.tile(mask(TOP, LEFT)));
    }

    @Test
    void simpleLayout() {
        assertEquals(0, Layout.SIMPLE.tile(0));
        assertEquals(1, Layout.SIMPLE.tile(ALL));             // (1,0)
        assertEquals(3 + 4, Layout.SIMPLE.tile(mask(TOP)));   // (3,1)
        assertEquals(2 + 3 * 4, Layout.SIMPLE.tile(mask(TOP, RIGHT))); // (2,3)
        assertEquals(1 + 2 * 4, Layout.SIMPLE.tile(mask(LEFT, RIGHT, BOTTOM))); // (1,2), top missing
    }

    @Test
    void piecedQuarters() {
        // alone: every quarter shows the single tile
        assertEquals(0, Layout.piecedTile(true, true, 0));
        // connected to the right only: the right quarters continue horizontally, the left ones keep the border
        assertEquals(3, Layout.piecedTile(true, false, mask(RIGHT)));
        assertEquals(0, Layout.piecedTile(false, true, mask(RIGHT)));
        // inside a large area
        assertEquals(1, Layout.piecedTile(false, false, ALL));
        // left and top connect, but the corner between them does not: inner corner
        assertEquals(4, Layout.piecedTile(true, true, mask(LEFT, TOP)));
        assertEquals(2, Layout.piecedTile(false, true, mask(TOP, BOTTOM)));
    }

    @Test
    void tileCoordinates() {
        ConnectingTexture legacyFull = new ConnectingTexture("x:y", Layout.FULL, null, false, 1);
        legacyFull.setImageSize(128, 128);  // old square image: 8 x 6 tiles of 16 px, last two rows empty
        int tile = 2 + 2 * 8;
        assertEquals(32f / 128, legacyFull.u(tile, 0f), 1e-6);
        assertEquals(48f / 128, legacyFull.u(tile, 1f), 1e-6);
        assertEquals(40f / 128, legacyFull.v(tile, 0.5f), 1e-6);

        ConnectingTexture pieced = new ConnectingTexture("x:y", Layout.PIECED, null, false, 1);
        pieced.setImageSize(80, 16);
        assertEquals(64f / 80, pieced.u(4, 0f), 1e-6);
        assertEquals(1f, pieced.v(4, 1f), 1e-6);
    }
}
