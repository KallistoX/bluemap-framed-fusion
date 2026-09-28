package io.github.kallistox.framedfusion.fusion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * A model with {@code "loader": "fusion:model"} and type {@code connecting}.
 *
 * @param parent      the model's parent ("ns:block/x"), to inherit connections from Fusion parents
 * @param connections key -> {@link ConnectionPredicate}, or {@link String} for an alias like {@code "#side"}
 */
public record FusionModel(String parent, Map<String, Object> connections) {

    public static final String DEFAULT_KEY = "default";

    static FusionModel parse(JsonObject json) {
        if (!json.has("loader") || !json.get("loader").getAsString().equals("fusion:model")) return null;
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if (!type.equals("connecting") && !type.equals("fusion:connecting")) return null;

        Map<String, Object> connections = new HashMap<>();
        if (json.get("connections") instanceof JsonObject c) {
            for (Map.Entry<String, JsonElement> entry : c.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    String alias = value.getAsString();
                    connections.put(entry.getKey(), alias.startsWith("#") ? alias.substring(1) : alias);
                } else {
                    connections.put(entry.getKey(), Predicates.parse(value));
                }
            }
        }
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        if (parent != null && parent.indexOf(':') < 0) parent = "minecraft:" + parent;
        return new FusionModel(parent, connections);
    }
}
