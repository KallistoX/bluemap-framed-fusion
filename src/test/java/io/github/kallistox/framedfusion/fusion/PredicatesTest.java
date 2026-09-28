package io.github.kallistox.framedfusion.fusion;

import com.google.gson.JsonParser;
import de.bluecolored.bluemap.core.world.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredicatesTest {

    /** The side rule of rechiseled:cobblestone_brick_pattern_connecting (shortened). */
    private static final String SIDE = """
            {"type": "fusion:or", "predicates": [
              {"type": "fusion:match_block", "block": "rechiseled:cobblestone_brick_pattern_connecting"},
              {"type": "fusion:and", "predicates": [
                {"type": "fusion:is_direction", "directions": ["top", "top_left", "top_right"]},
                {"type": "fusion:match_state", "block": "rechiseled:cobblestone_brick_pattern_slab_connecting",
                 "properties": {"type": ["bottom"]}}
              ]}
            ]}""";

    private static final BlockState SELF = new BlockState("rechiseled:cobblestone_brick_pattern_connecting");
    private static final BlockState AIR = BlockState.AIR;

    private static boolean test(ConnectionPredicate p, BlockState other, ConnectionDirection dir) {
        return p.test(new ConnectionPredicate.Context(SELF, other, AIR, dir));
    }

    @Test
    void rechiseledSideRule() {
        ConnectionPredicate side = Predicates.parse(JsonParser.parseString(SIDE));
        BlockState bottomSlab = new BlockState("rechiseled:cobblestone_brick_pattern_slab_connecting", Map.of("type", "bottom", "waterlogged", "false"));
        BlockState topSlab = new BlockState("rechiseled:cobblestone_brick_pattern_slab_connecting", Map.of("type", "top", "waterlogged", "false"));

        assertTrue(test(side, SELF, ConnectionDirection.LEFT));
        assertFalse(test(side, new BlockState("minecraft:stone"), ConnectionDirection.LEFT));
        assertTrue(test(side, bottomSlab, ConnectionDirection.TOP));      // bottom slab above connects
        assertFalse(test(side, bottomSlab, ConnectionDirection.BOTTOM));  // ... below does not
        assertFalse(test(side, topSlab, ConnectionDirection.TOP));
    }

    @Test
    void sameBlockAndUnknown() {
        ConnectionPredicate same = Predicates.parse(JsonParser.parseString("{\"type\": \"fusion:is_same_block\"}"));
        assertTrue(test(same, new BlockState(SELF.getFormatted(), Map.of("axis", "x")), ConnectionDirection.TOP));
        ConnectionPredicate never = Predicates.parse(JsonParser.parseString("{\"type\": \"false\"}"));
        assertFalse(test(never, SELF, ConnectionDirection.TOP));
        ConnectionPredicate unknown = Predicates.parse(JsonParser.parseString("{\"type\": \"somemod:whatever\"}"));
        assertFalse(test(unknown, SELF, ConnectionDirection.TOP));
    }
}
