package io.github.kallistox.framedfusion.render;

import de.bluecolored.bluemap.core.resources.adapter.ResourcesGson;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.util.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CamoRotationTest {

    /** The element of vanilla's cube_column_horizontal (lying logs). */
    private static final Element COLUMN = ResourcesGson.INSTANCE.fromJson("""
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
              "down": {"texture": "#down"}, "up": {"texture": "#up", "rotation": 180},
              "north": {"texture": "#north"}, "south": {"texture": "#south"},
              "west": {"texture": "#west"}, "east": {"texture": "#east"}}}""", Element.class);

    private static Variant variant(String json) {
        return ResourcesGson.INSTANCE.fromJson(json, Variant.class);
    }

    @Test
    void uprightBlockNeedsNoTurn() {
        Variant upright = variant("{\"model\": \"minecraft:block/oak_log\"}");
        assertNull(FramedModelRenderer.camoTextureUp(COLUMN, COLUMN.getFaces().get(Direction.EAST), Direction.EAST, upright));
    }

    @Test
    void logAlongZHasItsGrainAlongZ() {
        Variant alongZ = variant("{\"model\": \"minecraft:block/oak_log_horizontal\", \"x\": 90}");
        // the side faces of a log lying north-south show the grain (texture top) along z
        int[] east = FramedModelRenderer.camoTextureUp(COLUMN, COLUMN.getFaces().get(Direction.EAST), Direction.EAST, alongZ);
        assertEquals(0, east[0]);
        assertEquals(0, east[1]);
        assertEquals(1, Math.abs(east[2]));
        int[] top = FramedModelRenderer.camoTextureUp(COLUMN, COLUMN.getFaces().get(Direction.NORTH), Direction.NORTH, alongZ);
        assertEquals(1, Math.abs(top[2]));
    }

    @Test
    void logAlongXHasItsGrainAlongX() {
        Variant alongX = variant("{\"model\": \"minecraft:block/oak_log_horizontal\", \"x\": 90, \"y\": 90}");
        int[] side = FramedModelRenderer.camoTextureUp(COLUMN, COLUMN.getFaces().get(Direction.EAST), Direction.EAST, alongX);
        assertArrayEquals(new int[]{Math.abs(side[0]), 0, 0}, new int[]{1, side[1], side[2]});
    }
}
