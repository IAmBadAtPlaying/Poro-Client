package com.iambadatplaying.data.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.BasicDataManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class MapDataManager<T> extends BasicDataManager {

    protected MapDataManager(Starter starter) {
        super(starter);
        map = Collections.synchronizedMap(new HashMap<>());
    }

    protected Map<T, JsonObject> map;

    public JsonObject get(T key) {
        if(map.containsKey(key)) return map.get(key);
        JsonObject value = load(key);
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
        return value;
    }
    public abstract JsonObject load(T key);

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

    public void updateMap(String uri, String type, JsonElement data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }
        if (!isRelevantURI(uri)) return;
        doUpdateAndSend(uri, type, data);
    }
}
