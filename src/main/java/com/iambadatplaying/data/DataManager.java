package com.iambadatplaying.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.array.*;
import com.iambadatplaying.data.map.*;
import com.iambadatplaying.data.state.*;
import com.iambadatplaying.frontendHandler.Socket;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.HashMap;

public class DataManager {
    public static final String UPDATE_TYPE_SELF_PRESENCE = "SelfPresenceUpdate";
    public static final String UPDATE_TYPE_GAMEFLOW_PHASE = "GameflowPhaseUpdate";
    public static final String UPDATE_TYPE_LOBBY = "LobbyUpdate";
    public static final String UPDATE_TYPE_LOOT = "LootUpdate";
    public static final String UPDATE_TYPE_PATCHER = "PatcherUpdate";
    public static final String UPDATE_TYPE_CHAMP_SELECT = "ChampSelectUpdate";
    public static final String UPDATE_TYPE_FRIENDS = "FriendUpdate";
    public static final String UPDATE_TYPE_FRIEND_GROUPS = "FriendGroupUpdate";
    public static final String UPDATE_TYPE_FRIEND_HOVERCARD = "FriendHovercardUpdate";
    public static final String UPDATE_TYPE_CONVERSATION = "ConversationUpdate";
    public static final String UPDATE_TYPE_HONOR_EOG = "HonorEndOfGameUpdate";
    public static final String UPDATE_TYPE_TICKER_MESSAGES = "TickerMessageUpdate";
    public static final String UPDATE_TYPE_STATS_EOG = "StatsEndOfGameUpdate";
    public static final String UPDATE_TYPE_INVITATIONS = "InvitationsUpdate";
    public static final String UPDATE_TYPE_MATCHMAKING_SEARCH_STATE = "MatchmakingSearchStateUpdate";
    public static final String UPDATE_TYPE_INTERNAL_STATE = "InternalStateUpdate";
    public static final String UPDATE_TYPE_OWNED_SKINS = "OwnedSkinsUpdate";
    public static final String UPDATE_TYPE_OWNED_CHAMPIONS = "OwnedChampionsUpdate";
    public static final String UPDATE_TYPE_CURRENT_SUMMONER = "CurrentSummonerUpdate";
    public static final String UPDATE_TYPE_QUEUE = "QueueUpdate";

    public static final String UPDATE_TYPE_ALL_INITIAL_DATA_LOADED = "DataLoaded";

    private static final String DATA_STRING_EVENT = "event";

    private final Starter starter;
    private final HashMap<String, StateDataManager> stateDataManagers;
    private final HashMap<String, MapDataManager<?>> mapDataManagers;
    private final HashMap<String, ArrayDataManager> arrayDataManagers;

    private boolean initialized = false;

    public DataManager(Starter starter) {
        this.starter = starter;
        this.stateDataManagers = new HashMap<>();
        this.mapDataManagers = new HashMap<>();
        this.arrayDataManagers = new HashMap<>();

        addStateManagers();
        addMapManagers();
        addArrayManagers();
    }

    public static String getEventDataString(String event, JsonElement data) {
        JsonObject dataToSend = new JsonObject();
        dataToSend.addProperty(DATA_STRING_EVENT, event);
        dataToSend.add("data", data);
        return dataToSend.toString();
    }

    public static String getInitialDataString(String event, JsonElement data) {
        JsonObject dataToSend = new JsonObject();
        dataToSend.addProperty(DATA_STRING_EVENT, "Initial" + event);
        dataToSend.add("data", data);
        return dataToSend.toString();
    }

    private void addArrayManagers() {
        addManager(new TickerMessageManager(starter));
        addManager(new InvitationManager(starter));
        addManager(new ChampionInventoryManager(starter));
        addManager(new SkinInventoryManager(starter));
    }

    private void addMapManagers() {
        addManager(new RegaliaManager(starter));
        addManager(new FriendManager(starter));
        addManager(new GameNameManager(starter));
        addManager(new MessageManager(starter));
        addManager(new FriendGroupManager(starter));
        addManager(new QueueManager(starter));
        addManager(new FriendHovercardManager(starter));
        addManager(new SummonerIdToPuuidManager(starter));
    }

    private void addStateManagers() {
        addManager(new LobbyData(starter));
        addManager(new GameflowData(starter));
        addManager(new ChatMeManager(starter));
        addManager(new LootDataManager(starter));
        addManager(new PatcherData(starter));
        addManager(new ReworkedChampSelectData(starter));
        addManager(new EOGHonorManager(starter));
        addManager(new MatchmakingSearchManager(starter));
        addManager(new CurrentSummonerManager(starter));
    }

    private void addManager(ArrayDataManager manager) {
        arrayDataManagers.put(manager.getClass().getName(), manager);
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

        for (ArrayDataManager manager : arrayDataManagers.values()) {
            manager.init();
        }

        log("Specific DataManagers initialized!", Starter.LOG_LEVEL.INFO);
    }

    public void shutdown() {
        log("Shutting down specific DataManagers", Starter.LOG_LEVEL.INFO);
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.shutdown();
        }

        for (MapDataManager manager : mapDataManagers.values()) {
            manager.shutdown();
        }

        for (ArrayDataManager manager : arrayDataManagers.values()) {
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
            e.printStackTrace();
        }
    }

    //This method doesnt utilize Threads to ensure that the initial data is sent before any other data
    public void sendInitialDataBlocking(Socket socket) {
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.getCurrentState().ifPresent(
                    state -> socket.sendMessage(
                            DataManager.getInitialDataString(manager.getEventName(), state
                            )
                    )
            );
        }

        for (ArrayDataManager manager : arrayDataManagers.values()) {
            manager.getCurrentState().ifPresent(
                    state -> socket.sendMessage(
                            DataManager.getInitialDataString(manager.getEventName(), state)
                    )
            );
        }
    }

    private void doUpdate(String uri, String type, JsonElement data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }

        if (uri == null || uri.isEmpty()) {
            log("Uri is empty, or null, parsing error occurred", Starter.LOG_LEVEL.ERROR);
            return;
        }

        if (ConnectionManager.isProtectedRessource(uri)) {
            log("Update from protected Ressource, wont fire update", Starter.LOG_LEVEL.INFO);
            return;
        }

        if (data == null || data.isJsonNull()) {
            data = new JsonObject();
        }

        if (!data.isJsonArray() && !data.isJsonObject()) {
            log("Data is not a JsonArray or JsonObject, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }

        if (type == null || type.isEmpty()) {
            log("Type is empty, or null, parsing error occurred", Starter.LOG_LEVEL.ERROR);
            return;
        }

        final JsonElement finalData = data;
        for (StateDataManager manager : stateDataManagers.values()) {
            manager.update(uri, type, finalData);
        }

        for (MapDataManager manager : mapDataManagers.values()) {
            manager.update(uri, type, finalData);
        }

        for (ArrayDataManager manager : arrayDataManagers.values()) {
            manager.update(uri, type, finalData);
        }
    }

    public StateDataManager getStateManager(Class manager) {
        return stateDataManagers.get(manager.getName());
    }

    public <T> MapDataManager<T> getMapManager(Class manager) {
        return (MapDataManager<T>) mapDataManagers.get(manager.getName());
    }

    public ArrayDataManager getArrayManager(Class manager) {
        return arrayDataManagers.get(manager.getName());
    }

    private void log(Object o, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + o, level);
    }

    private void log(Object o) {
        log(o, Starter.LOG_LEVEL.DEBUG);
    }
}
