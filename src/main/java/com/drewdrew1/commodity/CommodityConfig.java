package com.drewdrew1.commodity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommodityConfig {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Path configPath;

    public CommodityConfig(Path configPath) {
        this.configPath = configPath;
    }

    public Path configPath() {
        return configPath;
    }

    public CommoditySettings load() throws IOException {
        if (Files.notExists(configPath)) {
            writeDefault();
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            root = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        }
        return parse(root);
    }

    public void writeDefault() throws IOException {
        Files.createDirectories(configPath.getParent());
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(defaultJson(), writer);
        }
    }

    public static JsonObject defaultJson() {
        JsonObject root = new JsonObject();

        JsonObject database = new JsonObject();
        database.addProperty("name", "rpg_commodity");
        root.add("database", database);

        JsonObject offlineIncrease = new JsonObject();
        offlineIncrease.addProperty("unitSeconds", 60);
        offlineIncrease.addProperty("maxCatchupSeconds", 604800);
        root.add("offlineIncrease", offlineIncrease);

        JsonObject commodities = new JsonObject();
        for (CommodityDefinition definition : defaultDefinitions().values()) {
            JsonObject object = new JsonObject();
            object.addProperty("displayName", definition.displayName());
            object.addProperty("min", definition.min());
            object.addProperty("max", definition.max());
            object.addProperty("default", definition.defaultValue());
            object.addProperty("offlineIncrease", definition.offlineIncrease());

            JsonArray aliases = new JsonArray();
            for (String alias : defaultVisibleAliases(definition.key())) {
                aliases.add(alias);
            }
            object.add("aliases", aliases);
            commodities.add(definition.key(), object);
        }
        root.add("commodities", commodities);
        return root;
    }

    private CommoditySettings parse(JsonObject root) {
        Map<String, CommodityDefinition> defaults = defaultDefinitions();
        JsonObject database = object(root, "database");
        JsonObject offline = object(root, "offlineIncrease");
        JsonObject commodities = object(root, "commodities");

        String databaseName = string(database, "name", "rpg_commodity");
        int unitSeconds = (int) number(offline, "unitSeconds", 60L);
        long maxCatchupSeconds = number(offline, "maxCatchupSeconds", 604800L);

        Map<String, CommodityDefinition> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, CommodityDefinition> entry : defaults.entrySet()) {
            parsed.put(entry.getKey(), parseDefinition(entry.getKey(), object(commodities, entry.getKey()), entry.getValue()));
        }

        for (Map.Entry<String, JsonElement> entry : commodities.entrySet()) {
            if (parsed.containsKey(entry.getKey()) || !entry.getValue().isJsonObject()) {
                continue;
            }
            CommodityDefinition fallback = new CommodityDefinition(
                    entry.getKey(),
                    entry.getKey(),
                    0L,
                    999999999L,
                    0L,
                    0L,
                    List.of(entry.getKey())
            );
            parsed.put(entry.getKey(), parseDefinition(entry.getKey(), entry.getValue().getAsJsonObject(), fallback));
        }

        return new CommoditySettings(databaseName, unitSeconds, maxCatchupSeconds, new ArrayList<>(parsed.values()));
    }

    private CommodityDefinition parseDefinition(String key, JsonObject object, CommodityDefinition fallback) {
        List<String> aliases = new ArrayList<>(defaultVisibleAliases(key));
        JsonArray aliasArray = array(object, "aliases");
        for (JsonElement alias : aliasArray) {
            if (alias.isJsonPrimitive()) {
                aliases.add(alias.getAsString());
            }
        }

        return new CommodityDefinition(
                key,
                string(object, "displayName", fallback.displayName()),
                number(object, "min", fallback.min()),
                number(object, "max", fallback.max()),
                number(object, "default", fallback.defaultValue()),
                number(object, "offlineIncrease", fallback.offlineIncrease()),
                aliases
        );
    }

    private static Map<String, CommodityDefinition> defaultDefinitions() {
        Map<String, CommodityDefinition> definitions = new LinkedHashMap<>();
        definitions.put("money", definition("money", "돈", 0L, 999999999L, 0L, 0L));
        definitions.put("gems", definition("gems", "보석", 0L, 999999L, 0L, 0L));
        definitions.put("crystal", definition("crystal", "크리스탈", 0L, 999999L, 0L, 0L));
        definitions.put("village_contribution", definition("village_contribution", "마을 기여도", 0L, 1000000L, 0L, 0L));
        definitions.put("guild_contribution", definition("guild_contribution", "길드 기여도", 0L, 1000000L, 0L, 0L));
        definitions.put("energy", definition("energy", "에너지", 0L, 100L, 100L, 1L));
        return definitions;
    }

    private static CommodityDefinition definition(String key, String displayName, long min, long max, long defaultValue, long offlineIncrease) {
        return new CommodityDefinition(key, displayName, min, max, defaultValue, offlineIncrease, defaultVisibleAliases(key));
    }

    private static List<String> defaultVisibleAliases(String key) {
        return switch (key) {
            case "money" -> List.of("돈", "money", "cash");
            case "gems" -> List.of("보석", "gem", "gems");
            case "crystal" -> List.of("크리스탈", "crystal", "crystals");
            case "village_contribution" -> List.of("마을기여도", "마을_기여도", "village", "village_contribution");
            case "guild_contribution" -> List.of("길드기여도", "길드_기여도", "guild", "guild_contribution");
            case "energy" -> List.of("에너지", "energy");
            default -> List.of(key);
        };
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static long number(JsonObject parent, String key, long fallback) {
        JsonElement element = parent == null ? null : parent.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
