package com.iambadatplaying.data.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.BasicDataManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class MapDataManager<T> extends BasicDataManager {

    protected Map<T, JsonObject> map;

    protected MapDataManager(Starter starter) {
        super(starter);
        map = Collections.synchronizedMap(new HashMap<>());
    }

    public static <T> Map<T, JsonObject> getMapFromArray(JsonArray array, String identifier) {
        Map<T, JsonObject> map = Collections.synchronizedMap(new HashMap<>());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has(identifier)) continue;
            T key = (T) object.get(identifier);
            map.put(key, object);
        }
        return map;
    }

    public Optional<JsonObject> get(T key) {
        if (map.containsKey(key)) return Optional.of(map.get(key));
        Optional<JsonObject> value = load(key);
        value.ifPresent(jsonObject -> map.put(key, jsonObject));
        return value;
    }

    public abstract Optional<JsonObject> load(T key);

    public void edit(T key, JsonObject value) {
        map.put(key, value);
    }

    public JsonObject getMapAsJson() {
        JsonObject mapAsJson = new JsonObject();
        for (Map.Entry<T, JsonObject> entry : map.entrySet()) {
            mapAsJson.add(entry.getKey().toString(), entry.getValue());
        }
        return mapAsJson;
    }

    public void shutdown() {
        if (!initialized) return;
        initialized = false;
        doShutdown();
        map.clear();
    }
}
