package net.manaz.vtp.compat;

import com.google.gson.*;
import java.util.Map;

/** Normalize legacy loot-function field names before shared deterministic validation. */
public final class TradeJson {
    private TradeJson() {}
    public static JsonElement normalize(JsonElement input) {
        if (input == null || input.isJsonPrimitive() || input.isJsonNull()) return input;
        if (input.isJsonArray()) {
            JsonArray array = new JsonArray();
            input.getAsJsonArray().forEach(value -> array.add(normalize(value)));
            return array;
        }
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : input.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (key.equals("conditions")) {
                if (entry.getValue().getAsJsonArray().isEmpty()) continue;
                key = "condition";
            } else if (key.equals("function")) key = "type";
            result.add(key, normalize(entry.getValue()));
        }
        return result;
    }
}
