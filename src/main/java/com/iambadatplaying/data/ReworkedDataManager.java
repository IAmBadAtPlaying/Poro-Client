package com.iambadatplaying.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.map.FriendManager;
import com.iambadatplaying.data.map.MapDataManager;
import com.iambadatplaying.data.map.MessageManager;
import com.iambadatplaying.data.map.RegaliaManager;
import com.iambadatplaying.data.state.*;

import java.util.HashMap;

public class ReworkedDataManager {
    private HashMap<String, StateDataManager> stateDataManagers;
    private HashMap<String, MapDataManager> mapDataManagers;


    public static final String UPDATE_TYPE_SELF_PRESENCE = "SelfPresenceUpdate";
    public static final String UPDATE_TYPE_GAMEFLOW_PHASE = "GameflowPhaseUpdate";
    public static final String UPDATE_TYPE_LOBBY = "LobbyUpdate";
    public static final String UPDATE_TYPE_LOOT = "LootUpdate";
    public static final String UPDATE_TYPE_PATCHER = "PatcherUpdate";
    public static final String UPDATE_TYPE_CHAMP_SELECT = "ChampSelectUpdate";
    public static final String UPDATE_TYPE_FRIENDS = "FriendUpdate";
    public static final String UPDATE_TYPE_CONVERSATION = "ConversationUpdate";

    private static final String DATA_STRING_EVENT = "event";

    private final Starter starter;

    private boolean initialized = false;

    private ReworkedDataManager() {
        this.starter = null;
    }

    public ReworkedDataManager(Starter starter) {
        this.starter = starter;
        this.stateDataManagers = new HashMap<>();
        this.mapDataManagers = new HashMap<>();

        addStateManagers();
        addMapManagers();
    }

    private void addMapManagers() {
        addManager(new RegaliaManager(starter));
        addManager(new FriendManager(starter));
    }

    private void addStateManagers() {
        addManager(new MessageManager(starter));
        addManager(new LobbyData(starter));
        addManager(new GameflowData(starter));
        addManager(new ChatMeManager(starter));
        addManager(new LootData(starter));
        addManager(new PatcherData(starter));
        addManager(new ReworkedChampSelectData(starter));
    }

    private void addManager(StateDataManager manager) {
        stateDataManagers.put(manager.getClass().getName(), manager);
    }

    private void addManager(MapDataManager manager) {
        mapDataManagers.put(manager.getClass().getName(), manager);
    }

    public void init() {
        if (initialized) {
            log("Already initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }
        initialized = true;

        log("Initializing specific DataManagers", Starter.LOG_LEVEL.INFO);
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.init();
        }

        for (MapDataManager manager : mapDataManagers.values()) {
            manager.init();
        }

        log("Specific DataManagers initialized!", Starter.LOG_LEVEL.INFO);
    }

    public void shutdown() {
        log("Shutting down specific DataManagers", Starter.LOG_LEVEL.INFO);
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.shutdown();
        }

        initialized = false;
    }

    public void update(JsonObject message) {
        try {

            JsonElement data = message.get("data");
            String uri = message.get("uri").getAsString();
            String type = message.get("eventType").getAsString();
            log(type + " " + uri + ": " + data, Starter.LOG_LEVEL.LCU_MESSAGING);
            doUpdate(uri, type, data);
        } catch (Exception e) {

        }
    }

    private void doUpdate(String uri, String type, JsonElement data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }

        if (data == null || data.isJsonNull()) {
            data = new JsonObject();
        }

        if (!data.isJsonArray() && !data.isJsonObject()) {
            log("Data is not a JsonArray or JsonObject, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }

        if (uri == null || uri.isEmpty()) {
            log("Uri is empty, or null, parsing error occurred", Starter.LOG_LEVEL.ERROR);
            return;
        }

        if (type == null || type.isEmpty()) {
            log("Type is empty, or null, parsing error occurred", Starter.LOG_LEVEL.ERROR);
            return;
        }

        final JsonElement finalData = data;
        for (StateDataManager manager : stateDataManagers.values()) {
            new Thread(() -> {
                manager.updateState(uri, type, finalData);
            }).start();
        }

        for (MapDataManager manager : mapDataManagers.values()) {
            new Thread(() -> {
                manager.updateMap(uri, type, finalData);
            }).start();
        }
    }

    public StateDataManager getStateManagers(Class manager) {
        return stateDataManagers.get(manager.getName());
    }

    public MapDataManager getMapManagers(Class manager) {
        return mapDataManagers.get(manager.getName());
    }


    public static String getEventDataString(String event, JsonObject data) {
        JsonObject dataToSend = new JsonObject();
        dataToSend.addProperty(DATA_STRING_EVENT, event);
        dataToSend.add("data", data);
        return dataToSend.toString();
    }

    public static String getEventDataString(String event, JsonArray data) {
        JsonObject dataToSend = new JsonObject();
        dataToSend.addProperty(DATA_STRING_EVENT, event);
        dataToSend.add("data", data);
        return dataToSend.toString();
    }

    private void log(Object o, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + o, level);
    }

    private void log(Object o) {
        log(o, Starter.LOG_LEVEL.DEBUG);
    }
}
