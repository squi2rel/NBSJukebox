package com.github.squi2rel.nbs.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NBSUtil {
    public static Map<String, Sound> buildReverseMap(InputStream jsonStream) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(jsonStream)).getAsJsonObject();

        Map<String, Sound> reverse = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String eventName = entry.getKey();
            JsonObject eventObj = entry.getValue().getAsJsonObject();

            if (!eventObj.has("sounds")) continue;

            JsonArray arr = eventObj.getAsJsonArray("sounds");

            for (JsonElement e : arr) {
                String path;

                if (e.isJsonPrimitive()) {
                    path = e.getAsString();
                } else {
                    JsonObject obj = e.getAsJsonObject();
                    path = obj.get("name").getAsString();
                }

                String full = "minecraft/" + path + ".ogg";
                Sound sound = Registry.SOUND_EVENT.get(Objects.requireNonNull(NamespacedKey.fromString(eventName)));
                if (sound == null) continue;

                reverse.put(full, sound);
            }
        }
        return reverse;
    }

}
