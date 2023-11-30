package com.iambadatplaying.data;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.data.map.FriendManager;
import com.iambadatplaying.data.map.MapDataManager;
import com.iambadatplaying.data.state.*;
import com.iambadatplaying.data.map.RegaliaManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

public class ReworkedDataManager {

    private ChampSelectData champSelectData;
    private LobbyData lobbyData;

    private HashMap<String, StateDataManager> stateDataManagers;
    private HashMap<String, MapDataManager> mapDataManagers;

    public static final String INSTRUCTION_PREFIX = "INSTRUCTION_";

    private static final String DATA_STRING_EVENT = "event";

    private final MainInitiator mainInitiator;

    private boolean initialized = false;

    private ReworkedDataManager() {
        this.mainInitiator = null;
    }

    public ReworkedDataManager(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
        this.stateDataManagers = new HashMap<>();
        this.mapDataManagers = new HashMap<>();

        addStateManagers();
        addMapManagers();
    }

    private void addMapManagers() {
        addManager(new RegaliaManager(mainInitiator));
        addManager(new FriendManager(mainInitiator));
    }

    private void addStateManagers() {
        addManager(new LobbyData(mainInitiator));
        addManager(new ChampSelectData(mainInitiator));
        addManager(new GameflowData(mainInitiator));
        addManager(new ChatMeManager(mainInitiator));
        addManager(new LootData(mainInitiator));
        addManager(new PatcherData(mainInitiator));
    }

    private void addManager(StateDataManager manager) {
        stateDataManagers.put(manager.getClass().getSimpleName(), manager);
    }

    private void addManager(MapDataManager manager) {
        mapDataManagers.put(manager.getClass().getSimpleName(), manager);
    }

    public void init() {
        if (initialized) {
            log("Already initialized, wont have any effect", MainInitiator.LOG_LEVEL.WARN);
            return;
        }
        initialized = true;

        log("Initializing specific DataManagers", MainInitiator.LOG_LEVEL.INFO);
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.init();
        }

        for (MapDataManager manager : mapDataManagers.values()) {
            manager.init();
        }

        log("Specific DataManagers initialized!", MainInitiator.LOG_LEVEL.INFO);
    }

    public void shutdown() {
        log("Shutting down specific DataManagers", MainInitiator.LOG_LEVEL.INFO);
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.shutdown();
        }

        initialized = false;
    }

    public void update(JSONObject message) {
        try {
            JSONObject data = message.getJSONObject("data");
            String uri = message.getString("uri");
            String type = message.getString("eventType");
            log(type + " " + uri + ": " +data);
            doUpdate(uri, type, data);
        } catch (Exception e) {

        }
    }

    private void doUpdate(String uri, String type, JSONObject data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", MainInitiator.LOG_LEVEL.WARN);
            return;
        }

        if (data == null || data.isEmpty()) {
            log("Data is empty, wont have any effect", MainInitiator.LOG_LEVEL.WARN);
            return;
        }

        if (uri == null || uri.isEmpty()) {
            log("Uri is empty, or null, parsing error occurred", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        if (type == null || type.isEmpty()) {
            log("Type is empty, or null, parsing error occurred", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        for (StateDataManager manager : stateDataManagers.values()) {
            new Thread(() -> {
                manager.updateState(uri, type, data);
            }).start();
        }

        for (MapDataManager manager : mapDataManagers.values()) {
            new Thread(() -> {
                manager.updateMap(uri, type, data);
            }).start();
        }
    }

    public StateDataManager getStateManagers(String managerName) {
        return stateDataManagers.get(managerName);
    }

    public MapDataManager getMapManagers(String managerName) {
        return mapDataManagers.get(managerName);
    }

    public static String getEventDataString(String event, JSONObject data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put(DATA_STRING_EVENT, event);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    public static String getEventDataString(String event, JSONArray data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put(DATA_STRING_EVENT, event);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    private void log(Object o, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + o, level);
    }

    private void log(Object o) {
        log(o, MainInitiator.LOG_LEVEL.DEBUG);
    }
}
