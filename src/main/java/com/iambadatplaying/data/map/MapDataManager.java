package com.iambadatplaying.data.map;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.data.BasicDataManager;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class MapDataManager<T> extends BasicDataManager {

    protected MapDataManager(MainInitiator mainInitiator) {
        super(mainInitiator);
        map = Collections.synchronizedMap(new HashMap<>());
    }

    protected Map<T, JSONObject> map;

    public JSONObject get(T key) {
        if(map.containsKey(key)) return map.get(key);
        else return load(key);
    }
    public abstract JSONObject load(T key);

    public void edit(T key, JSONObject value) {
        map.put(key, value);
    }

    public JSONObject getMapAsJson() {
        JSONObject mapAsJson = new JSONObject();
        for (Map.Entry<T, JSONObject> entry : map.entrySet()) {
            mapAsJson.put(entry.getKey().toString(), entry.getValue());
        }
        return mapAsJson;
    }

    public void updateMap(String uri, String type, JSONObject data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", MainInitiator.LOG_LEVEL.WARN);
            return;
        }
        if (!isRelevantURI(uri)) return;
        doUpdateAndSend(uri, type, data);
    }
}
