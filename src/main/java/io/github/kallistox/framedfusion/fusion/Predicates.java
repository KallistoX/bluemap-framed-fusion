package io.github.kallistox.framedfusion.fusion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.world.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Parses Fusion's connection predicates. Unknown types never connect (and are logged once). */
public final class Predicates {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Predicates() {}

    public static ConnectionPredicate parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) return ConnectionPredicate.NEVER;
        JsonObject o = json.getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString() : "";
        int colon = type.indexOf(':');
        if (colon >= 0) type = type.substring(colon + 1);

        switch (type) {
            case "true": return ConnectionPredicate.ALWAYS;
            case "false": return ConnectionPredicate.NEVER;
            case "and": {
                List<ConnectionPredicate> ps = list(o);
                return c -> {
                    for (ConnectionPredicate p : ps) if (!p.test(c)) return false;
                    return true;
                };
            }
            case "or": {
                List<ConnectionPredicate> ps = list(o);
                return c -> {
                    for (ConnectionPredicate p : ps) if (p.test(c)) return true;
                    return false;
                };
            }
            case "not": {
                ConnectionPredicate p = parse(o.get("predicate"));
                return c -> !p.test(c);
            }
            case "is_same_block": return c -> sameBlock(c.self(), c.other());
            case "is_same_state": return ConnectionPredicate.SAME_STATE;
            case "is_direction": {
                Set<ConnectionDirection> dirs = EnumSet.noneOf(ConnectionDirection.class);
                for (String key : new String[]{"direction", "directions"}) {
                    for (String name : strings(o.get(key))) dirs.add(ConnectionDirection.byName(name));
                }
                return c -> dirs.contains(c.direction());
            }
            case "match_block": {
                Set<String> blocks = blocks(o);
                return c -> blocks.contains(c.other().getFormatted());
            }
            case "match_block_in_front": {
                Set<String> blocks = blocks(o);
                return c -> blocks.contains(c.inFront().getFormatted());
            }
            case "match_state": {
                Map<String, Map<String, Set<String>>> states = states(o);
                return c -> matches(states, c.other());
            }
            case "match_state_in_front": {
                Map<String, Map<String, Set<String>>> states = states(o);
                return c -> matches(states, c.inFront());
            }
            default:
                if (WARNED.add(type))
                    Logger.global.logWarning("[framed-fusion] Unknown Fusion connection predicate '" + type + "', treating it as false");
                return ConnectionPredicate.NEVER;
        }
    }

    private static List<ConnectionPredicate> list(JsonObject o) {
        List<ConnectionPredicate> ps = new ArrayList<>();
        if (o.get("predicates") instanceof JsonArray a) for (JsonElement e : a) ps.add(parse(e));
        return ps;
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null) return out;
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        else out.add(e.getAsString());
        return out;
    }

    private static String id(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static Set<String> blocks(JsonObject o) {
        Set<String> blocks = new HashSet<>();
        for (String key : new String[]{"block", "blocks"}) {
            for (String b : strings(o.get(key))) blocks.add(id(b));
        }
        return blocks;
    }

    /** block id -> property -> allowed values; an empty property map matches every state of the block. */
    private static Map<String, Map<String, Set<String>>> states(JsonObject o) {
        Map<String, Set<String>> properties = new HashMap<>();
        if (o.get("properties") instanceof JsonObject p) {
            for (var entry : p.entrySet()) properties.put(entry.getKey(), new HashSet<>(strings(entry.getValue())));
        }
        Map<String, Map<String, Set<String>>> states = new HashMap<>();
        for (String b : blocks(o)) states.put(b, properties);
        return states;
    }

    private static boolean matches(Map<String, Map<String, Set<String>>> states, BlockState state) {
        Map<String, Set<String>> properties = states.get(state.getFormatted());
        if (properties == null) return false;
        for (var entry : properties.entrySet()) {
            String value = state.getProperties().get(entry.getKey());
            if (value == null || !entry.getValue().contains(value)) return false;
        }
        return true;
    }

    private static boolean sameBlock(BlockState a, BlockState b) {
        return a.getFormatted().equals(b.getFormatted());
    }
}
