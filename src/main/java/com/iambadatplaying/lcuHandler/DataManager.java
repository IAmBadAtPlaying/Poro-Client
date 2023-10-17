package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.structs.messaging.Conversation;
import com.iambadatplaying.structs.messaging.Message;
import com.sun.istack.internal.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DataManager {

    //FRONTEND EVENTS
    public static final String EVENT_MESSAGE_UPDATE = "MessageUpdate";
    public static final String EVENT_LOOT_UPDATE = "LootUpdate";


    private static final String LOOT_COUNT = "count";
    
    private static final String SUMMONER_PUUID = "puuid";
    private static final String SUMMONER_SUMMONER_ID = "summonerId";

    private static final String TYPE_SYSTEM = "system";

    private enum ChampSelectState {
        PREPARATION(10),
        BANNING(11),
        AWAITING_BAN_RESULTS(21),
        AWAITING_PICK(22),
        PICKING_WITHOUT_BAN(12),
        PICKING_WITH_BAN(13),
        AWAITING_FINALIZATION(25),
        FINALIZATION(15);

        private int value;

        private ChampSelectState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ChampSelectState fromValue(int value) {
            for (ChampSelectState state : ChampSelectState.values()) {
                if (state.getValue() == value) {
                    return state;
                }
            }
            return null;
        }

        // Logic breaks in tournament draft
        public static ChampSelectState fromParameters(JSONObject parameters) {
            if (parameters == null) return null;
            String timerPhase = parameters.getString("phase");
            if (timerPhase == null) {
                timerPhase = "UNKNOWN";
            }
            boolean banExists = parameters.has("banAction");
            boolean pickExists = parameters.has("pickAction");

            boolean isPickInProgress = false;
            boolean isPickCompleted = false;

            boolean isBanInProgress = false;
            boolean isBanCompleted = false;

            if (pickExists) {
                JSONObject pickAction = parameters.getJSONObject("pickAction");
                isPickInProgress = pickAction.getBoolean("isInProgress");
                isPickCompleted = pickAction.getBoolean("completed");
            }
            if (banExists) {
                JSONObject banAction = parameters.getJSONObject("banAction");
                isBanInProgress = banAction.getBoolean("isInProgress");
                isBanCompleted = banAction.getBoolean("completed");
            }
            switch (timerPhase) {
                case "PLANNING":
                    return PREPARATION;
                case "BAN_PICK":
                    if (banExists) {
                        if (isBanInProgress) {
                            return BANNING;
                        } else {
                            if (isBanCompleted) {
                                if (pickExists) {
                                    if (isPickInProgress) {
                                        return PICKING_WITH_BAN;
                                    } else {
                                        if (isPickCompleted) {
                                            return AWAITING_FINALIZATION;
                                        } else {
                                            return AWAITING_PICK;
                                        }
                                    }
                                } else {
                                    return AWAITING_FINALIZATION;
                                }
                            } else {
                                return AWAITING_BAN_RESULTS;
                            }
                        }
                    } else {
                        if (pickExists) {
                            if (isPickInProgress) {
                                return PICKING_WITHOUT_BAN;
                            } else {
                                if (isPickCompleted) {
                                    return AWAITING_FINALIZATION;
                                } else {
                                    return AWAITING_PICK;
                                }
                            }
                        } else {
                            return AWAITING_FINALIZATION;
                        }
                    }
                case "FINALIZATION":
                    return FINALIZATION;
                default:
                    return null;
            }
        }
    }

    private final MainInitiator mainInitiator;

    private Map<String, JSONObject> synchronizedFriendListMap;
    private Map<BigInteger, JSONObject> regaliaMap;
    private Map<BigInteger, String> nameMap;
    private Map<Integer, JSONObject> cellIdMemberMap;
    private Map<Integer, JSONObject> cellIdActionMap;

    ExecutorService disenchantExecutor;

    private static final Integer MAX_LOBBY_SIZE = 5;
    private static final Integer MAX_LOBBY_HALFS_INDEX = 2;

    private volatile boolean disenchantingInProgress = false;
    private volatile boolean shutdownInProgress = false;

    private HashMap<Integer, JSONObject> availableQueueMap;
    private HashMap<String, Conversation> conversationMap;

    private JSONObject lootJsonObject;

    private JSONObject platformConfigQueues;

    private JSONObject currentLobbyState;
    private JSONObject currentGameflowState;
    private JSONObject currentChampSelectState;

    private JSONObject chromaSkinId;
    private JSONObject championJson;
    private JSONObject summonerSpellJson;

    public DataManager(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public static String REGALIA_REGEX = "/lol-regalia/v2/summoners/(.*?)/regalia/async";

    public void init() {
        this.regaliaMap = Collections.synchronizedMap(new HashMap<BigInteger, JSONObject>());
        this.cellIdMemberMap = Collections.synchronizedMap(new HashMap<>());
        this.cellIdActionMap = Collections.synchronizedMap(new HashMap<>());
        this.availableQueueMap = new HashMap<>();
        this.chromaSkinId = new JSONObject();
        this.championJson = new JSONObject();
        this.summonerSpellJson = new JSONObject();
        this.conversationMap = new HashMap<>();

        this.disenchantExecutor = Executors.newFixedThreadPool(4);

        initQueueMap();
        updateClientSystemStates();
        createLootObject();
        fetchChromaSkinId();
        fetchChampionJson();
        fetchSummonerSpells();
    }

    public Conversation getConversation(String conversationId) {
        if(!conversationId.contains("@")) {
            try {
                conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (conversationMap.get(conversationId) != null) {
            log("Conversation found in map");
            Conversation conversation = conversationMap.get(conversationId);
            if(Conversation.SCOPE.PEER_TO_PEER.equals(conversation.getScope())) setActiveConversationId(conversationId);
            return conversationMap.get(conversationId);
        } else {
            log("Conversation not found in map, fetching info and posting active conversation");
            Conversation conversation = initializeConversation(conversationId);
            conversationMap.put(conversationId, conversation);
            return conversation;
        }
    }

    private Conversation initializeConversation(String conversationId) {
        postConversationScope(conversationId);
        Conversation conversation = fetchConversationRoomInfo(conversationId);
        fetchPreviousMessages(conversation);
        if (conversation == null) return null;
        if (Conversation.SCOPE.PEER_TO_PEER.equals(conversation.getScope())) setActiveConversationId(conversation.getId());
        return conversation;
    }

    private void setActiveConversationId(String conversationId) {
        log("Setting active conversation to " + conversationId);
        JSONObject activeConversation = new JSONObject();
        activeConversation.put("id", conversationId);
        Integer respCode = (Integer) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/lol-chat/v1/conversations/active", activeConversation.toString()));
        log("Response code: " + respCode);
    }

    private void postConversationScope(String conversationId) {
        log("[Conversation Scope]: Posting for " + conversationId);
        JSONObject conversationScope = new JSONObject();
        conversationScope.put("id", conversationId);
        conversationScope.put("type", "chat");
        Integer respCode = (Integer) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations", conversationScope.toString()));
        log("[Conversation Scope]: Response code: " + respCode);
    }

    public void addConversationMessage(String conversationId, JSONObject jsonMessage) {
        if (!conversationId.contains("@")) {
            try {
                conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            } catch (Exception e) {

            }
        }

        Message message = Message.fromJsonObject(jsonMessage);

        if(conversationMap.containsKey(conversationId)) {
            Conversation conversation = conversationMap.get(conversationId);
            if (justLeftRoom(conversationId, message)) {
                log("Conversation left, clearing messages for " + conversationId, MainInitiator.LOG_LEVEL.INFO);
                conversationMap.computeIfPresent(conversationId, (k, v) -> {
                    v.getMessages().clear();
                    //This will effectively do conversationMap.remove(conversationId);
                    return null;
                });
                return;
            }
            conversation.addMessage(message);
            sendMessageUpdate(conversationId, message);
        }
        //TODO: Overthink the approach for messages in not initialized Messages. For now we just ignore them
    }

    private void sendMessageUpdate(String conversationId, Message message) {
        if (message == null) return;
        JSONObject sendMessageUpdate = new JSONObject();
        try {
            conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            sendMessageUpdate.put("conversationId", conversationId);
            sendMessageUpdate.put("message", message.toJsonObject());
            mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(EVENT_MESSAGE_UPDATE, sendMessageUpdate));
        } catch (Exception e) {
            return;
        }

    }

    public void sendConversationUpdate(Conversation conversation) {
        JSONObject sendMessageUpdate = new JSONObject();
        sendMessageUpdate.put("conversationId", conversation.getId());
        copyJsonAttrib("messages", conversation.toJsonObject(), sendMessageUpdate);
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("ConversationUpdate", sendMessageUpdate));
    }

    private Conversation fetchConversationRoomInfo(@NotNull String conversationId) {
            log("[Conversation Fetch] Fetching for " + conversationId);
            String url = "/lol-chat/v1/conversations/" + conversationId;
            JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, url));
            System.out.println(data);
            if (data != null) {
                log(data.toString());
                return Conversation.fromJsonObject(data);
            } else {
                log("Error fetching conversation info");
            }
            return null;
    }

    private void logConversation(String conversationId) {
        if (!conversationMap.containsKey(conversationId)) {
            log("Conversation not found");
            return;
        }
        System.out.println(conversationMap.get(conversationId).toJsonObject());
    }

    //Todo check if Author id is the same as the current user
    private boolean olderMessagesExist(String conversationId, Message message) {
        if (message == null) return true;
        String messageType = message.getType();
        if (TYPE_SYSTEM.equals(messageType)) {
            String messageBody = message.getBody();
            if (Message.MESSAGE_JOINED.equals(messageBody)) {
                return false;
            }
        }
        return true;
    }

    //Todo check if Author id is the same as the current user
    private boolean justLeftRoom(String conversationId, Message message) {
        if (message == null) return false;
        String type = message.getType();
        if (TYPE_SYSTEM.equals(type)) {
            String messageBody = message.getBody();
            if (Message.MESSAGE_LEFT.equals(messageBody)) {
                return true;
            }
        }
        return false;
    }

    private void fetchPreviousMessages(Conversation conversation) {
        if (conversation == null) return;
        String url = "/lol-chat/v1/conversations/" + conversation.getId() + "/messages";
        JSONArray data = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, url));
        ArrayList<Message> messages = Message.createMessageList(data);
        conversation.addMessages(messages);
    }

    public void addJsonMessages(Conversation conversation, JSONArray jsonMessages) {
        if (conversation == null) return;
        if (jsonMessages == null) return;

        for (int i = 0; i < jsonMessages.length(); i++) {
            JSONObject jsonMessage = jsonMessages.getJSONObject(i);
            Message message = Message.fromJsonObject(jsonMessage);
            if (message != null) {
                if(message.isSystemMessage()) continue;
                conversation.addMessage(message);
            }
        }
    }

    public ArrayList<Message> getConversationMessages(String conversationId) {
        if (!conversationMap.containsKey(conversationId)) {
            return new ArrayList<>();
        }

        return conversationMap.get(conversationId).getMessages();
    }

    public void shutdown() {
        shutdownInProgress = true;
        try {
            if (!disenchantExecutor.isTerminated()) {
                disenchantExecutor.shutdownNow();
                if(disenchantExecutor.awaitTermination(3,  TimeUnit.SECONDS)) {
                    log("Executor shutdown successful");
                }else log("Executor shutdown failed");
            }
        } catch (InterruptedException e) {
            log("Executor termination failed: " + e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
        }
        disenchantExecutor = null;
        if (synchronizedFriendListMap != null) synchronizedFriendListMap.clear();
        synchronizedFriendListMap = null;
        if (currentLobbyState != null) currentLobbyState.clear();
        currentLobbyState = null;
        if (regaliaMap != null) regaliaMap.clear();
        regaliaMap = null;
        if (cellIdMemberMap != null) cellIdMemberMap.clear();
        cellIdMemberMap = null;
        if (cellIdActionMap != null) cellIdActionMap.clear();
        cellIdActionMap = null;
        if(availableQueueMap != null) availableQueueMap.clear();
        availableQueueMap = null;

        this.championJson = null;
        this.chromaSkinId = null;
        this.summonerSpellJson = null;
    }

    public void updateQueueMap() {
        availableQueueMap.clear();
        initQueueMap();
    }

    public void initQueueMap() {
        JSONArray queueArray = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-game-queues/v1/queues"));
        for (int i = 0; i < queueArray.length(); i++) {
            JSONObject currentQueue = queueArray.getJSONObject(i);
            if ("Available".equals(currentQueue.getString("queueAvailability"))) {
                Integer queueId = currentQueue.getInt("id");
                availableQueueMap.put(queueId, currentQueue);
            }
        }
    }

    public synchronized boolean rerollElements(JSONArray lootArray) {
        if (lootArray == null || lootArray.isEmpty()) return false;
        if (disenchantingInProgress) return false;
        disenchantingInProgress = true;

        String disenchantType = "";

        //3 Skins shards per reroll
        ArrayList<JSONArray> disenchantCollection = new ArrayList<>();

        int elementCount = 0;
        for (int i = 0; i < lootArray.length(); i++) {
            JSONObject lootObject = lootArray.getJSONObject(i);
            String type = lootObject.getString("type");
            switch (type) {
                case "SKIN_RENTAL":
                    disenchantType = "SKIN";
                    break;
                case  "WARDSKIN_RENTAL":
                    disenchantType = "WARDSKIN";
                    break;
                default:
                    log("Not a valid shard, skipping");
                    continue;
            }
            String lootId = lootObject.getString("lootId");
            Integer count = lootObject.getInt(LOOT_COUNT);
            for (int j = 0; j < count; j++) {
                if (elementCount % 3 == 0) {
                    disenchantCollection.add(new JSONArray());
                }
                disenchantCollection.get(elementCount / 3).put(lootId);
                elementCount++;
            }
        }
        if (elementCount % 3 != 0) {
            disenchantCollection.remove(disenchantCollection.size() - 1);
        }

        log("Rerolling " + (elementCount - (elementCount % 3)) + " elements");
        long startTime = System.currentTimeMillis();
        final String disenchantTypeFinal = disenchantType;
        for (JSONArray disenchantArray : disenchantCollection) {
            disenchantExecutor.execute(() -> {
                mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST,"/lol-loot/v1/recipes/"+disenchantTypeFinal+"_reroll/craft?repeat=1", disenchantArray.toString()));
                log(disenchantArray.toString());
                log("Time for reroll: " + (System.currentTimeMillis() - startTime) + "ms");
            });
        }

        TimerTask refetchLoot = new TimerTask() {
            @Override
            public void run() {
                updateLootMap();
                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(EVENT_LOOT_UPDATE, lootJsonObject));
            }
        };
        Timer timer = new Timer();
        timer.schedule(refetchLoot, 2000);
        try {
            if(disenchantExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                log("Successfully rerolled all elements in given time");
            } else log("Some elements could not be rerolled in time");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        disenchantingInProgress = false;

        return true;
    }

    private JSONObject createDisenchantObject(JSONObject lootObject) {
        JSONObject disenchantObject = new JSONObject();
        disenchantObject.put("repeat", 1);
        disenchantObject.put("recipeName", lootObject.getString("type") + "_disenchant");

        JSONArray lootNames = new JSONArray();
        lootNames.put(lootObject.getString("lootName"));
        disenchantObject.put("lootNames", lootNames);
        return disenchantObject;
    }

    public synchronized boolean disenchantElements(JSONArray lootArray) {
        if (lootArray == null || lootArray.isEmpty()) return false;
        if (disenchantingInProgress) return false;
        disenchantingInProgress = true;


        int MAXIMUM_DISENCHANT_COUNT = 50;

        //Integer division is intentional
        ArrayList<JSONArray> disenchantCollection = new ArrayList<>();

        int actualCount = 0;
        for (int i = 0; i < lootArray.length(); i++) {
            final JSONObject currentLoot = lootArray.getJSONObject(i);
            log(currentLoot);
            if (!currentLoot.has(LOOT_COUNT)) {
                continue;
            }
            //TODO: This can be done better
            int count = currentLoot.getInt(LOOT_COUNT);
            for (int j = 0; j < count; j++) {
                if (actualCount % MAXIMUM_DISENCHANT_COUNT == 0) {
                    disenchantCollection.add(new JSONArray());
                }
                disenchantCollection.get(actualCount / MAXIMUM_DISENCHANT_COUNT).put(createDisenchantObject(currentLoot));
                actualCount++;
            }
        }

        log("Disenchanting " + actualCount + " elements");
        for (JSONArray disenchantArray : disenchantCollection) {
            try {
                log(disenchantArray.toString());
                String response = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST,"/lol-loot/v1/craft/mass", disenchantArray.toString()));
                log(response);
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        TimerTask refetchLoot = new TimerTask() {
            @Override
            public void run() {
                updateLootMap();
                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(EVENT_LOOT_UPDATE, lootJsonObject));
            }
        };
        Timer timer = new Timer();
        timer.schedule(refetchLoot, 2000);
        try {
            if(disenchantExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                log("Successfully disenchanted all elements in given time");
            } else log("Some elements could not be disenchanted in time");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        disenchantingInProgress = false;
        return true;
    }

    private void updateLootMap() {
        JSONObject preFormattedLoot = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-loot/v2/player-loot-map"));
        lootJsonObject = preFormattedLoot.getJSONObject("playerLoot");
    }

    public JSONObject updateFELootMap(JSONObject lootObject) {
        log("Updating FE Loot Map");
        if (lootObject == null) return null;
        JSONObject bufferObject = lootObject.getJSONObject("playerLoot");
        if (bufferObject == null) return null;
        log("FE Loot Map updated");
        lootJsonObject = bufferObject;
        return lootJsonObject;
    }

    private void createLootObject() {
        if (lootJsonObject == null) {
            lootJsonObject = new JSONObject();

            updateLootMap();
        }
    }

    public JSONObject getAvailableQueues() {
        return platformConfigQueues;
    }

    public void updateClientSystemStates() {
        JSONObject clientSystemStates = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-platform-config/v1/namespaces/ClientSystemStates"));
        JSONArray enabledQueueIds = clientSystemStates.getJSONArray("enabledQueueIdsList");
        if (platformConfigQueues == null) {
            platformConfigQueues = new JSONObject();
            platformConfigQueues.put("PvP", new JSONObject());
            platformConfigQueues.put("VersusAi", new JSONObject());
        }
        for (int i = 0; i < enabledQueueIds.length(); i++) {
            Integer queueId = enabledQueueIds.getInt(i);
            if (availableQueueMap.containsKey(queueId)) {
                JSONObject queue = availableQueueMap.get(queueId);
                String category = queue.getString("category");
                String gameMode = queue.getString("gameMode");
                switch (category) {
                    case "PvP":
                        if (platformConfigQueues.getJSONObject("PvP").has(gameMode)) {
                            ((JSONArray) platformConfigQueues.getJSONObject("PvP").get(gameMode)).put(queue);
                        } else platformConfigQueues.getJSONObject("PvP").put(gameMode, new JSONArray().put(queue));
                        break;
                    case "VersusAi":
                        if (platformConfigQueues.getJSONObject("VersusAi").has(gameMode)) {
                            ((JSONArray) platformConfigQueues.getJSONObject("VersusAi").get(gameMode)).put(queue);
                        } else platformConfigQueues.getJSONObject("VersusAi").put(gameMode, new JSONArray().put(queue));
                        break;
                    default:
                        log("Unknown category: " + category);
                        break;
                }
            }
        }
    }

    public JSONObject getFEGameflowStatus() {
        JSONObject feGameflowObject;
        if (currentLobbyState == null) {
            String currentGameflowString = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-gameflow/v1/gameflow-phase"));
            currentGameflowString = currentGameflowString.replace("\"", "");
            JSONObject currentGameflowObject = new JSONObject().put("phase", currentGameflowString.trim());
            feGameflowObject = beToFeGameflowInfo(currentGameflowObject);
            currentGameflowState = feGameflowObject;
        }
        return currentGameflowState;
    }

    public JSONObject updateFEGameflowStatus(JSONObject beGameflow) {
        JSONObject updatedFEGameflowObject = beToFeGameflowInfo(beGameflow);
        if (updatedFEGameflowObject == null) return null;
        if (updatedFEGameflowObject.similar(currentGameflowState)) return null;
        currentGameflowState = updatedFEGameflowObject;
        return updatedFEGameflowObject;
    }

    public void resetChampSelectSession() {
        //Maybe change this to null ?!
        currentChampSelectState = new JSONObject();
        cellIdMemberMap.clear();
        cellIdActionMap.clear();
    }

    public JSONObject getFEFriendObject() {
        JSONObject feFriendObject = new JSONObject();
        if (synchronizedFriendListMap == null) {
            log ("FE Friend List not initialized, creating..");
            this.synchronizedFriendListMap = Collections.synchronizedMap(new HashMap<String, JSONObject>());
            try {
                JSONArray friendArray = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/friends"));
                for (int i = 0; i < friendArray.length(); i++) {
                    JSONObject friendObject = beToFeFriendsInfo(friendArray.getJSONObject(i));
                    if (friendObject == null || friendObject.isEmpty()) continue;
                    feFriendObject.put(friendObject.getString(SUMMONER_PUUID),friendObject);
                    synchronizedFriendListMap.put(friendObject.getString(SUMMONER_PUUID), friendObject);
                }
            } catch (Exception e) {

            }
        } else {
            log ("FE Friend List already initialized, returning saved values");
            for (JSONObject json : synchronizedFriendListMap.values()) {
                feFriendObject.put(json.getString(SUMMONER_PUUID),json);
            }
        }
        return feFriendObject;
    }

    private JSONObject beToFeFriendsInfo(JSONObject backendFriendObject) {
        JSONObject data = new JSONObject();
        try {
            String availability = backendFriendObject.getString("availability");
            String puuid = backendFriendObject.getString(SUMMONER_PUUID);
            if (puuid == null || puuid.isEmpty()) return null;
            String statusMessage = backendFriendObject.getString("statusMessage");
            String name = backendFriendObject.getString("name");
            String id = backendFriendObject.getString("id");
            if (name == null || name.isEmpty()) return null;
            Integer iconId = backendFriendObject.getInt("icon");
            if (iconId < 1) {
                iconId = 1;
            }
            BigInteger summonerId = backendFriendObject.getBigInteger(SUMMONER_SUMMONER_ID);
            data.put(SUMMONER_PUUID, puuid);
            data.put("statusMessage", statusMessage);
            data.put("name", name);
            data.put("iconId", iconId);
            data.put("id", id);
            data.put(SUMMONER_SUMMONER_ID, summonerId);
            data.put("availability", availability);
            copyJsonAttrib("groupId", backendFriendObject, data);
            copyJsonAttrib("lol", backendFriendObject, data);

            return data;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    public JSONObject getFELobbyObject() {
        JSONObject feLobbyObject = new JSONObject();
        if (currentLobbyState == null) {
            log("No Lobby State available, creating...");
            try {
                JSONObject lobbyObject = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-lobby/v2/lobby"));
                log(lobbyObject);
                if (lobbyObject.has("errorCode")) {
                    return feLobbyObject;
                }
                feLobbyObject = beToFeLobbyInfo(lobbyObject);
                currentLobbyState = feLobbyObject;
                return feLobbyObject;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return currentLobbyState;
    }

    public JSONObject updateFEFriend(JSONObject data) {
        JSONObject updatedFEData = beToFeFriendsInfo(data);
        if (updatedFEData == null) {
            return null;
        }
        JSONObject currentFEData = synchronizedFriendListMap.get(updatedFEData.getString(SUMMONER_PUUID));
        if (updatedFEData.similar(currentFEData)) {
            return null;
        }
        synchronizedFriendListMap.put(updatedFEData.getString(SUMMONER_PUUID), updatedFEData);
        return updatedFEData;
    }

    public JSONObject getCurrentLobbyState() {
        return currentLobbyState;
    }

    private JSONObject beToFeLobbyInfo(JSONObject data) {
        if (data == null ||data.isEmpty()) {
            return null;
        }

        JSONObject feData = new JSONObject();

        copyJsonAttrib("partyId", data, feData);
        copyJsonAttrib("invitations", data, feData);

        JSONObject gameConfig = data.getJSONObject("gameConfig");
        JSONObject feGameConfig = new JSONObject();

        //TODO: Add Custom Game support; add TFT Support
        copyJsonAttrib("queueId", gameConfig, feGameConfig);
        copyJsonAttrib("showPositionSelector", gameConfig, feGameConfig);
        copyJsonAttrib("isCustom", gameConfig,feGameConfig);
        copyJsonAttrib("maxLobbySize", gameConfig, feGameConfig);
        copyJsonAttrib("allowablePremadeSizes", gameConfig, feGameConfig);
        copyJsonAttrib("mapId", gameConfig, feGameConfig);
        copyJsonAttrib("gameMode", gameConfig, feGameConfig);

        JSONObject localMember = data.getJSONObject("localMember");
        JSONObject feLocalMember = beLobbyMemberToFeLobbyMember(localMember);

        JSONArray members = data.getJSONArray("members");
        JSONArray feMembers = new JSONArray();
        int j = 0;
        feMembers.put(indexToFEIndex(0),feLocalMember);
        j++;
        for (int i = 0; i < members.length(); i++) {
            int actualIndex = indexToFEIndex(j);
            JSONObject currentMember = beLobbyMemberToFeLobbyMember(members.getJSONObject(i));
            if (currentMember.getString(SUMMONER_PUUID).equals(feLocalMember.getString(SUMMONER_PUUID))) {
                continue;
            }
            feMembers.put(actualIndex, currentMember);
            j++;
        }
        for (; j < MAX_LOBBY_SIZE; j++) {
            feMembers.put(indexToFEIndex(j), new JSONObject());
        }

        feData.put("gameConfig", feGameConfig);
        feData.put("localMember", feLocalMember);
        feData.put("members", feMembers);
        return feData;
    }

    private int indexToFEIndex(int preParsedIndex) {
        int actualIndex = 0;
        int diff = indexDiff(preParsedIndex);

        actualIndex = MAX_LOBBY_HALFS_INDEX + diff;
        return actualIndex;
    }

    private int indexDiff(int index) {
        if (index % 2 == 0) {
            index /= 2;
            return index;
        } else return -indexDiff(index + 1);
    }

    public JSONObject updateFELobby(JSONObject data) {
        JSONObject updatedFEData = beToFeLobbyInfo(data);
        if (updatedFEData == null) {
            currentLobbyState = null;
            return null;
        }
        if (updatedFEData.similar(currentLobbyState)) {
            log("No FE relevant Lobby update");
            return null;
        }
        currentLobbyState = updatedFEData;
        return updatedFEData;
    }

    public JSONObject updateFEChampSelectSession (JSONObject data) {
        JSONObject updatedFEData = beToFEChampSelectSession(data);
        if (updatedFEData == null) {
            return null;
        }
        if (updatedFEData.similar(currentChampSelectState)) {
            return null;
        }
        currentChampSelectState = updatedFEData;
        return updatedFEData;
    }

    public JSONObject beToFEChampSelectSession(JSONObject data) {
        //Idea: Store every member of "myTeam" and "theirTeam" in a HashMap, lookup via actorCellId
        //This would allow modifying each entry in myTeam and their team via the action tab => Hovering / Pick Intent / Ban Hover
        JSONObject feChampSelect = new JSONObject();

        copyJsonAttrib("isCustomGame", data, feChampSelect); //This might need to trigger further changes
        copyJsonAttrib("localPlayerCellId", data, feChampSelect);
        copyJsonAttrib("gameId", data, feChampSelect);
        copyJsonAttrib("hasSimultaneousBans", data, feChampSelect);
        copyJsonAttrib("skipChampionSelect", data, feChampSelect);
        copyJsonAttrib("benchEnabled", data, feChampSelect);
        copyJsonAttrib("rerollsRemaining", data, feChampSelect);

        //Handle Actions
        //We only need to update via the last action / NO doest work
        copyJsonAttrib("actions", data, feChampSelect);
        JSONArray actions = data.getJSONArray("actions");
        beActionToFEAction(actions);

        JSONObject feTimer = new JSONObject();
        JSONObject timer = data.getJSONObject("timer");

        //MyTeam
        int localPlayerCellId = data.getInt("localPlayerCellId");
        JSONArray feMyTeam = data.getJSONArray("myTeam");
        for (int i = 0; i < feMyTeam.length(); i++) {
            int playerCellId = feMyTeam.getJSONObject(i).getInt("cellId");
            JSONObject playerObject = teamMemberToSessionMap(feMyTeam.getJSONObject(i), timer);
            if (playerCellId == localPlayerCellId) {
                feChampSelect.put("localPlayerPhase", playerObject.getString("stateDebug"));
            }
            feMyTeam.put(i, playerObject);
        }
        feChampSelect.put("myTeam", feMyTeam);

        //TheirTeam
        copyJsonAttrib("theirTeam", data, feChampSelect);
        JSONArray feTheirTeam = data.getJSONArray("myTeam");
        for (int i = 0; i < feMyTeam.length(); i++) {
            feTheirTeam.put(i, teamMemberToSessionMap(feTheirTeam.getJSONObject(i),timer));
        }

        copyJsonAttrib("phase", timer, feTimer);
        copyJsonAttrib("isInfinite", timer, feTimer);
        copyJsonAttrib("isInfinite", timer, feTimer);

        feChampSelect.put("timer",feTimer);

        JSONObject bans = data.getJSONObject("bans");
        JSONObject feBans = new JSONObject();

        copyJsonAttrib("theirTeamBans", bans, feBans);
        copyJsonAttrib("myTeamBans", bans, feBans);
        copyJsonAttrib("numBans", bans, feBans);


        feChampSelect.put("bans", feBans);

        return feChampSelect;
    }

    private JSONObject teamMemberToSessionMap(JSONObject feMember, JSONObject timer) {
        Integer cellId = feMember.getInt("cellId");
        JSONObject mappedAction = cellIdActionMap.get(cellId);
        if (mappedAction == null || mappedAction.isEmpty()) {
            log("No fitting action found for cellId: " + cellId);
        } else {

            copyJsonAttrib("pickAction", mappedAction, feMember);
            copyJsonAttrib("banAction", mappedAction, feMember);
        }

        JSONObject phaseObject = new JSONObject();
        copyJsonAttrib("phase", timer, phaseObject);
        copyJsonAttrib("pickAction", mappedAction, phaseObject);
        copyJsonAttrib("banAction", mappedAction, phaseObject);

        ChampSelectState state = ChampSelectState.fromParameters(phaseObject);
        if (state == null) {
            log(feMember);
            log(timer);
            log("No fitting state found for cellId: " + cellId);
        } else {
            System.out.println("CellId: " + cellId + "; State: " + state + "; Value: " + state.getValue());

            feMember.put("state", state.getValue());

            //TODO: DEBUG, remove this
            feMember.put("stateDebug", state.name());
        }
        cellIdMemberMap.put(cellId, feMember);
        return feMember;
    }

    private void updateInternalActionMapping(JSONObject singleAction) {
        Integer actorCellId = singleAction.getInt("actorCellId");
        Boolean completed = singleAction.getBoolean("completed");
        Boolean inProgress = singleAction.getBoolean("isInProgress");
        Integer championId = singleAction.getInt("championId");
        Integer id = singleAction.getInt("id");
        String type = singleAction.getString("type");
        cellIdActionMap.compute(actorCellId, (k, v) -> {
            if (v == null || v.isEmpty()) {
                v = new JSONObject();
            }
            JSONObject currentAction = new JSONObject();

            currentAction.put("type", type);
            currentAction.put("completed", completed);
            currentAction.put("isInProgress", inProgress);
            currentAction.put("championId", championId);
            currentAction.put("id", id);
            switch (type) {
                case "pick":
                    v.put("pickAction", currentAction);
                break;
                case "ban":
                    v.put("banAction",currentAction);
                break;
                default:
                    log("Unkown Type: " + type, MainInitiator.LOG_LEVEL.ERROR);
                break;
            }
            return v;
        });

    }

    private int performChampionAction(String actionType, Integer championId, boolean lockIn) throws IOException {
        if (currentChampSelectState == null || currentChampSelectState.isEmpty()) {
            return -1;
        }

        if (!currentChampSelectState.has("localPlayerCellId")) {
            return -1;
        }

        Integer localPlayerCellId = currentChampSelectState.getInt("localPlayerCellId");
        JSONObject actionBundle = cellIdActionMap.get(localPlayerCellId);

        if (actionBundle == null || actionBundle.isEmpty()) {
            return -1;
        }

        JSONObject actionObject;

        if (actionType.equals("pick")) {
            actionObject = actionBundle.getJSONObject("pickAction");
        } else if (actionType.equals("ban")) {
            actionObject = actionBundle.getJSONObject("banAction");
        } else {
            return -1;
        }

        int actionId = actionObject.getInt("id");
        JSONObject hoverAction = new JSONObject();
        hoverAction.put("championId", championId);

        if (lockIn) {
            hoverAction.put("completed", true);
        }

        String request = "/lol-champ-select/v1/session/actions/" + actionId;
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, request, hoverAction.toString());
        return con.getResponseCode();
    }

    public int pickChampion(Integer championId, boolean lockIn) throws IOException {
        return performChampionAction("pick", championId, lockIn);
    }

    public int banChampion(Integer championId, boolean lockIn) throws IOException {
        return performChampionAction("ban", championId, lockIn);
    }

    private void beActionToFEAction(JSONArray action) {
        if (action == null || action.isEmpty()) return;
        for (int i = 0; i < action.length(); i++) {
            JSONArray subAction = action.getJSONArray(i);
            if (subAction == null || subAction.isEmpty()) continue;
            for (int j = 0; j < subAction.length(); j++) {
                JSONObject singleAction = subAction.getJSONObject(j);
                updateInternalActionMapping(singleAction);
            }
        }
    }

    public JSONObject updateFERegaliaInfo(BigInteger summonerId) {

        JSONObject regalia = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-regalia/v2/summoners/"+summonerId.toString()+"/regalia"));
        regaliaMap.put(summonerId, regalia);
        log(regalia);

        if (currentLobbyState == null) return regalia;
        JSONArray members = currentLobbyState.getJSONArray("members");
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            if (member != null && !member.isEmpty() && member.has(SUMMONER_SUMMONER_ID)) {
                if (!summonerId.equals(member.getBigInteger(SUMMONER_SUMMONER_ID))) {
                    continue;
                }
                member.put("regalia", regalia);
            }
        }
        currentLobbyState.put("members", members);

        return regalia;
    }


    public JSONObject getFERegaliaInfo(BigInteger summonerId) {
        JSONObject regaliaObject = regaliaMap.get(summonerId);
        if (regaliaObject == null) {
            return updateFERegaliaInfo(summonerId);
        }
        return regaliaObject;
    }

    private JSONObject beLobbyMemberToFeLobbyMember(JSONObject member) {
        JSONObject feMember = new JSONObject();
        if (member == null) return feMember;
        copyJsonAttrib("isLeader", member, feMember);
        copyJsonAttrib("isBot", member, feMember);
        copyJsonAttrib(SUMMONER_PUUID, member, feMember);
        copyJsonAttrib("summonerLevel",member,feMember);
        copyJsonAttrib("ready",member,feMember);
        copyJsonAttrib(SUMMONER_SUMMONER_ID,member,feMember);
        copyJsonAttrib("isLeader",member,feMember);
        copyJsonAttrib("summonerName",member,feMember);
        copyJsonAttrib("secondPositionPreference",member,feMember);
        copyJsonAttrib("firstPositionPreference",member,feMember);
        copyJsonAttrib("summonerIconId", member, feMember);

        feMember.put("regalia", getFERegaliaInfo(feMember.getBigInteger(SUMMONER_SUMMONER_ID)));
        return feMember;
    }

    private void copyJsonAttrib(String key, JSONObject src, JSONObject dst) {
            if (src == null || dst == null) return;
            if (src.has(key)) {
                Object object = src.get(key);
                if (object != null) {
                    dst.put(key, object);
                }
            }
    }

    public JSONObject beToFeGameflowInfo(JSONObject currentGameflowPhase) {
        String phase = currentGameflowPhase.getString("phase");
        JSONObject gameflowContainer = new JSONObject();
        gameflowContainer.put("GameflowPhase", phase);
        return gameflowContainer;
    }

    private void fetchChampionJson() {
            JSONArray championJson = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-data/assets/v1/champion-summary.json"));
            JSONObject parsedChampionJson = new JSONObject();
            if (championJson != null && !championJson.isEmpty()) {
                for (int i = 0; i < championJson.length(); i++) {
                    JSONObject champion = championJson.getJSONObject(i);
                    int id = champion.getInt("id");
                    parsedChampionJson.put(Integer.toString(id), champion);
                }
            }
            this.championJson = parsedChampionJson;
    }

    private void fetchSummonerSpells() {

            JSONArray summonerSpells = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-data/assets/v1/summoner-spells.json"));
            JSONObject parsedSummonerSpells = new JSONObject();
            if (summonerSpells != null && !summonerSpells.isEmpty()) {
                for (int i = 0; i < summonerSpells.length(); i++) {
                    JSONObject summonerSpell = summonerSpells.getJSONObject(i);
                    int id = summonerSpell.getInt("id");
                    parsedSummonerSpells.put(Integer.toString(id), summonerSpell);
                }
            }
            this.summonerSpellJson = parsedSummonerSpells;


    }

    private void fetchChromaSkinId() {
            JSONObject chromaSkinId = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-data/assets/v1/skins.json"));
            JSONObject parsedChromaSkinId = new JSONObject();
            if (chromaSkinId != null && !chromaSkinId.isEmpty()) {
                for (String s : chromaSkinId.keySet()) {
                    JSONObject skin = chromaSkinId.getJSONObject(s);
                    int id = skin.getInt("id");
                    boolean hasChromas = skin.has("chromas");
                    if (hasChromas) {
                        JSONArray chromas = skin.getJSONArray("chromas");
                        if (chromas != null && chromas.length() > 0) {
                            for(int i = 0; i < chromas.length(); i++) {
                                JSONObject chroma = chromas.getJSONObject(i);
                                if (chroma != null && chroma.has("id")) {
                                    int chromaId = chroma.getInt("id");
                                    parsedChromaSkinId.put(""+chromaId, id);
                                }
                            }
                        }
                    }
                    parsedChromaSkinId.put(""+id, id);
                }
            }
            this.chromaSkinId = parsedChromaSkinId;

    }

    public static String getEventDataString(String event, JSONObject data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put("event", event);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    public static String getEventDataString(String event, JSONArray data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put("event", event);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    public static String getDataTransferString(String dataType, JSONObject data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put("event", "DataTransfer");

        JSONObject dataTransfer = new JSONObject();
        dataTransfer.put("dataType", dataType);
        dataTransfer.put("data", data);

        dataToSend.put("data", dataTransfer);
        return dataToSend.toString();
    }

    public static String getDataTransferString(String dataType, JSONArray data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put("event", "DataTransfer");

        JSONObject dataTransfer = new JSONObject();
        dataTransfer.put("dataType", dataType);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    public JSONObject getLoot() {
        return lootJsonObject;
    }

    public JSONObject getChromaSkinId() {
        return chromaSkinId;
    }

    public JSONObject getChampionJson() {
        return championJson;
    }

    public JSONObject getSummonerSpellJson() {
        return summonerSpellJson;
    }

    private void log(Object o) {
        log(o, MainInitiator.LOG_LEVEL.DEBUG);
    }

    private void log(Object o, MainInitiator.LOG_LEVEL l) {
            mainInitiator.log(this.getClass().getSimpleName() + ": " + o);
    }
}
