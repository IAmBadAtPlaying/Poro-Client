package com.iambadatplaying.lcuHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.structs.messaging.Conversation;
import com.iambadatplaying.structs.messaging.Message;

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

    public static final String INSTRUCTION_PLAY_SOUND = "PlaySound";

    private static final String LOOT_COUNT = "count";

    private static final String SUMMONER_PUUID = "puuid";
    private static final String SUMMONER_SUMMONER_ID = "summonerId";

    private static final String TYPE_SYSTEM = "system";

    private enum ChampSelectState {
        PREPARATION,
        BANNING,
        AWAITING_BAN_RESULTS,
        AWAITING_PICK,
        PICKING_WITHOUT_BAN,
        PICKING_WITH_BAN,
        AWAITING_FINALIZATION,
        FINALIZATION;

        // Logic breaks in tournament draft
        public static ChampSelectState fromParameters(JsonObject parameters) {
            if (parameters == null) return null;
            String timerPhase = parameters.get("phase").getAsString();
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
                JsonObject pickAction = parameters.get("pickAction").getAsJsonObject();
                isPickInProgress = pickAction.get("isInProgress").getAsBoolean();
                isPickCompleted = pickAction.get("completed").getAsBoolean();
            }
            if (banExists) {
                JsonObject banAction = parameters.get("banAction").getAsJsonObject();
                isBanInProgress = banAction.get("isInProgress").getAsBoolean();
                isBanCompleted = banAction.get("completed").getAsBoolean();
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

    private final Starter starter;

    private static final Integer MAX_LOBBY_SIZE = 5;
    private static final Integer MAX_LOBBY_HALFS_INDEX = 2;

    private volatile boolean disenchantingInProgress = false;
    private volatile boolean shutdownInProgress = false;

    private HashMap<Integer, JsonObject> availableQueueMap;
    private HashMap<String, Conversation> conversationMap;

    private JsonObject lootJsonObject;

    private JsonObject platformConfigQueues;

    private JsonObject currentLobbyState;
    private JsonObject currentGameflowState;
    private JsonObject currentChampSelectState;

    private JsonObject chromaSkinId;
    private JsonObject championJson;
    private JsonObject summonerSpellJson;

    public DataManager(Starter starter) {
        this.starter = starter;
    }

    public static String REGALIA_REGEX = "/lol-regalia/v2/summoners/(.*?)/regalia/async";

    public void init() {
        this.availableQueueMap = new HashMap<>();
        this.chromaSkinId = new JsonObject();
        this.championJson = new JsonObject();
        this.summonerSpellJson = new JsonObject();
        this.conversationMap = new HashMap<>();

        initQueueMap();
        updateClientSystemStates();
//        createLootObject();
//        fetchChromaSkinId();
//        fetchChampionJson();
//        fetchSummonerSpells();
    }

    public Conversation getConversation(String conversationId) {
        if (!conversationId.contains("@")) {
            try {
                conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (conversationMap.get(conversationId) != null) {
            log("Conversation found in map");
            Conversation conversation = conversationMap.get(conversationId);
            if (Conversation.SCOPE.PEER_TO_PEER.equals(conversation.getScope()))
                setActiveConversationId(conversationId);
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
        if (Conversation.SCOPE.PEER_TO_PEER.equals(conversation.getScope()))
            setActiveConversationId(conversation.getId());
        return conversation;
    }

    private void setActiveConversationId(String conversationId) {
        log("Setting active conversation to " + conversationId);
        JsonObject activeConversation = new JsonObject();
        activeConversation.addProperty("id", conversationId);
        Integer respCode = (Integer) starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/lol-chat/v1/conversations/active", activeConversation.toString()));
        log("Response code: " + respCode);
    }

    private void postConversationScope(String conversationId) {
        log("[Conversation Scope]: Posting for " + conversationId);
        JsonObject conversationScope = new JsonObject();
        conversationScope.addProperty("id", conversationId);
        conversationScope.addProperty("type", "chat");
        Integer respCode = (Integer) starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations", conversationScope.toString()));
        log("[Conversation Scope]: Response code: " + respCode);
    }

    public void addConversationMessage(String conversationId, JsonObject jsonMessage) {
        if (!conversationId.contains("@")) {
            try {
                conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            } catch (Exception e) {

            }
        }

        Message message = Message.fromJsonObject(jsonMessage);

        if (conversationMap.containsKey(conversationId)) {
            Conversation conversation = conversationMap.get(conversationId);
            if (justLeftRoom(conversationId, message)) {
                log("Conversation left, clearing messages for " + conversationId, Starter.LOG_LEVEL.INFO);
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
        JsonObject sendMessageUpdate = new JsonObject();
        try {
            conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            sendMessageUpdate.addProperty("conversationId", conversationId);
            sendMessageUpdate.add("message", message.toJsonObject());
            starter.getServer().sendToAllSessions(DataManager.getEventDataString(EVENT_MESSAGE_UPDATE, sendMessageUpdate));
        } catch (Exception e) {
            return;
        }

    }

    public void sendConversationUpdate(Conversation conversation) {
        JsonObject sendMessageUpdate = new JsonObject();
        sendMessageUpdate.addProperty("conversationId", conversation.getId());
        Util.copyJsonAttrib("messages", conversation.toJsonObject(), sendMessageUpdate);
        starter.getServer().sendToAllSessions(DataManager.getEventDataString("ConversationUpdate", sendMessageUpdate));
    }

    private Conversation fetchConversationRoomInfo(String conversationId) {
        log("[Conversation Fetch] Fetching for " + conversationId);
        String url = "/lol-chat/v1/conversations/" + conversationId;
        JsonObject data = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, url));
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

        JsonArray data = starter.getConnectionManager().getResponseBodyAsJsonArray( starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, url));
        ArrayList<Message> messages = Message.createMessageList(data);
        conversation.addMessages(messages);
    }

//    public void addJsonMessages(Conversation conversation, JsonArray jsonMessages) {
//        if (conversation == null) return;
//        if (jsonMessages == null) return;
//
//        for (int i = 0; i < jsonMessages.size(); i++) {
//            JsonObject jsonMessage = jsonMessages.get(i).getAsJsonObject();
//            Message message = Message.fromJsonObject(jsonMessage);
//            if (message != null) {
//                if (message.isSystemMessage()) continue;
//                conversation.addMessage(message);
//            }
//        }
//    }

    public ArrayList<Message> getConversationMessages(String conversationId) {
        if (!conversationMap.containsKey(conversationId)) {
            return new ArrayList<>();
        }

        return conversationMap.get(conversationId).getMessages();
    }

    public void shutdown() {
        if (availableQueueMap != null) availableQueueMap.clear();
        availableQueueMap = null;

        this.championJson = null;
        this.chromaSkinId = null;
        this.summonerSpellJson = null;
        this.platformConfigQueues = null;
    }

    public void updateQueueMap() {
        availableQueueMap.clear();
        initQueueMap();
    }

    public void initQueueMap() {
        JsonArray queueArray = starter.getConnectionManager().getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-queues/v1/queues"));
        for (int i = 0; i < queueArray.size(); i++) {
            JsonObject currentQueue = queueArray.get(i).getAsJsonObject();
            if ("Available".equals(currentQueue.get("queueAvailability").getAsString())) {
                Integer queueId = currentQueue.get("id").getAsInt();
                availableQueueMap.put(queueId, currentQueue);
            }
        }
    }

//    public synchronized boolean rerollElements(JsonArray lootArray) {
//        if (lootArray == null || lootArray.isEmpty()) return false;
//        if (disenchantingInProgress) return false;
//        disenchantingInProgress = true;
//
//        String disenchantType = "";
//
//        //3 Skins shards per reroll
//        ArrayList<JsonArray> disenchantCollection = new ArrayList<>();
//
//        int elementCount = 0;
//        for (int i = 0; i < lootArray.size(); i++) {
//            JsonObject lootObject = lootArray.get(i).getAsJsonObject();
//            String type = lootObject.get("type").getAsString();
//            switch (type) {
//                case "SKIN_RENTAL":
//                    disenchantType = "SKIN";
//                    break;
//                case "WARDSKIN_RENTAL":
//                    disenchantType = "WARDSKIN";
//                    break;
//                default:
//                    log("Not a valid shard, skipping");
//                    continue;
//            }
//            String lootId = lootObject.get("lootId").getAsString();
//            Integer count = lootObject.get(LOOT_COUNT).getAsInt();
//            for (int j = 0; j < count; j++) {
//                if (elementCount % 3 == 0) {
//                    disenchantCollection.add(new JSONArray());
//                }
//                disenchantCollection.get(elementCount / 3).put(lootId);
//                elementCount++;
//            }
//        }
//        if (elementCount % 3 != 0) {
//            disenchantCollection.remove(disenchantCollection.size() - 1);
//        }
//
//        log("Rerolling " + (elementCount - (elementCount % 3)) + " elements");
//        long startTime = System.currentTimeMillis();
//        final String disenchantTypeFinal = disenchantType;
//        for (JSONArray disenchantArray : disenchantCollection) {
//            disenchantExecutor.execute(() -> {
//                mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/recipes/" + disenchantTypeFinal + "_reroll/craft?repeat=1", disenchantArray.toString()));
//                log(disenchantArray.toString());
//                log("Time for reroll: " + (System.currentTimeMillis() - startTime) + "ms");
//            });
//        }
//
//        TimerTask refetchLoot = new TimerTask() {
//            @Override
//            public void run() {
//                updateLootMap();
//                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(EVENT_LOOT_UPDATE, lootJsonObject));
//            }
//        };
//        Timer timer = new Timer();
//        timer.schedule(refetchLoot, 2000);
//        try {
//            if (disenchantExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
//                log("Successfully rerolled all elements in given time");
//            } else log("Some elements could not be rerolled in time");
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        disenchantingInProgress = false;
//
//        return true;
//    }

//    private JSONObject createDisenchantObject(JSONObject lootObject) {
//        JSONObject disenchantObject = new JSONObject();
//        disenchantObject.put("repeat", 1);
//        disenchantObject.put("recipeName", lootObject.getString("type") + "_disenchant");
//
//        JSONArray lootNames = new JSONArray();
//        lootNames.put(lootObject.getString("lootName"));
//        disenchantObject.put("lootNames", lootNames);
//        return disenchantObject;
//    }
//
//    public synchronized boolean disenchantElements(JSONArray lootArray) {
//        if (lootArray == null || lootArray.isEmpty()) return false;
//        if (disenchantingInProgress) return false;
//        disenchantingInProgress = true;
//
//
//        int MAXIMUM_DISENCHANT_COUNT = 50;
//        System.out.println(lootArray.toString());
//
//        //Integer division is intentional
//        ArrayList<JSONArray> disenchantCollection = new ArrayList<>();
//
//        int actualCount = 0;
//        for (int i = 0; i < lootArray.length(); i++) {
//            final JSONObject currentLoot = lootArray.getJSONObject(i);
//            log(currentLoot);
//            if (!currentLoot.has(LOOT_COUNT)) {
//                continue;
//            }
//            //TODO: This can be done better
//            int count = currentLoot.getInt(LOOT_COUNT);
//            for (int j = 0; j < count; j++) {
//                if (actualCount % MAXIMUM_DISENCHANT_COUNT == 0) {
//                    disenchantCollection.add(new JSONArray());
//                }
//                disenchantCollection.get(actualCount / MAXIMUM_DISENCHANT_COUNT).put(createDisenchantObject(currentLoot));
//                actualCount++;
//            }
//        }
//
//        log("Disenchanting " + actualCount + " elements");
//        for (JSONArray disenchantArray : disenchantCollection) {
//            try {
//                log(disenchantArray.toString());
//                String response = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/craft/mass", disenchantArray.toString()));
//                log(response);
//                Thread.sleep(1000);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//
//        TimerTask refetchLoot = new TimerTask() {
//            @Override
//            public void run() {
//                updateLootMap();
//                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(EVENT_LOOT_UPDATE, lootJsonObject));
//            }
//        };
//        Timer timer = new Timer();
//        timer.schedule(refetchLoot, 2000);
//        try {
//            if (disenchantExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
//                log("Successfully disenchanted all elements in given time");
//            } else log("Some elements could not be disenchanted in time");
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        disenchantingInProgress = false;
//        return true;
//    }

    private void updateLootMap() {
        JsonObject preFormattedLoot = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-loot/v1/player-loot"));
        lootJsonObject = preFormattedLoot.get("playerLoot").getAsJsonObject();
    }

    public JsonObject updateFELootMap(JsonObject lootObject) {
        log("Updating FE Loot Map");
        if (lootObject == null) return null;
        JsonObject bufferObject = lootObject.get("playerLoot").getAsJsonObject();
        if (bufferObject == null) return null;
        log("FE Loot Map updated");
        lootJsonObject = bufferObject;
        return lootJsonObject;
    }

//    private void createLootObject() {
//        if (lootJsonObject == null) {
//            lootJsonObject = new JsonObject();
//
//            updateLootMap();
//        }
//    }

    public JsonObject getAvailableQueues() {
        return platformConfigQueues;
    }

    public void updateClientSystemStates() {
        JsonObject clientSystemStates = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-platform-config/v1/namespaces/ClientSystemStates"));
        JsonArray enabledQueueIds = clientSystemStates.get("enabledQueueIdsList").getAsJsonArray();
        if (platformConfigQueues == null) {
            platformConfigQueues = new JsonObject();
            platformConfigQueues.add("PvP", new JsonObject());
            platformConfigQueues.add("VersusAi", new JsonObject());
        }
        for (int i = 0; i < enabledQueueIds.size(); i++) {
            Integer queueId = enabledQueueIds.get(i).getAsInt();
            if (availableQueueMap.containsKey(queueId)) {
                JsonObject queue = availableQueueMap.get(queueId);
                String category = queue.get("category").getAsString();
                String gameMode = queue.get("gameMode").getAsString();
                switch (category) {
                    case "PvP":
                        if (platformConfigQueues.get("PvP").getAsJsonObject().has(gameMode)) {
                            (platformConfigQueues.get("PvP").getAsJsonObject().get(gameMode)).getAsJsonArray().add(queue);
                        } else {
                            JsonArray gameModeArray = new JsonArray();
                            gameModeArray.add(queue);
                            platformConfigQueues.get("PvP").getAsJsonObject().add(gameMode, gameModeArray);
                        }
                        break;
                    case "VersusAi":
                        if (platformConfigQueues.get("VersusAi").getAsJsonObject().has(gameMode)) {
                            platformConfigQueues.get("VersusAi").getAsJsonObject().get(gameMode).getAsJsonArray().add(queue);
                        } else {
                            JsonArray gameModeArray = new JsonArray();
                            gameModeArray.add(queue);
                            platformConfigQueues.get("VersusAi").getAsJsonObject().add(gameMode, gameModeArray);
                        }
                        break;
                    default:
                        log("Unknown category: " + category);
                        break;
                }
            }
        }
    }

//    public JsonObject getFEGameflowStatus() {
//        JsonObject feGameflowObject;
//        if (currentLobbyState == null) {
//            String currentGameflowString = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-gameflow/v1/gameflow-phase"));
//            currentGameflowString = currentGameflowString.replace("\"", "");
//            JsonObject currentGameflowObject = new JsonObject();
//            currentGameflowObject.addProperty("phase", currentGameflowString.trim());
//            feGameflowObject = beToFeGameflowInfo(currentGameflowObject);
//            currentGameflowState = feGameflowObject;
//        }
//        return currentGameflowState;
//    }

//    public JsonObject updateFEGameflowStatus(JsonObject beGameflow) {
//        JsonObject updatedFEGameflowObject = beToFeGameflowInfo(beGameflow);
//        if (updatedFEGameflowObject == null) return null;
//        if (Util.equalJsonElements(updatedFEGameflowObject, currentGameflowState)) return null;
//        currentGameflowState = updatedFEGameflowObject;
//        return updatedFEGameflowObject;
//    }


        private JsonObject beToFeFriendsInfo(JsonObject backendFriendObject) {
            JsonObject data = new JsonObject();
            try {
                String availability = backendFriendObject.get("availability").getAsString();
                String puuid = backendFriendObject.get(SUMMONER_PUUID).getAsString();
                if (puuid == null || puuid.isEmpty()) return null;
                String statusMessage = backendFriendObject.get("statusMessage").getAsString();
                String name = backendFriendObject.get("gameName").getAsString();
                String id = backendFriendObject.get("id").getAsString();
                if (name == null || name.isEmpty()) return null;
                Integer iconId = backendFriendObject.get("icon").getAsInt();
                if (iconId < 1) {
                    iconId = 1;
                }
                BigInteger summonerId = backendFriendObject.get(SUMMONER_SUMMONER_ID).getAsBigInteger();
                data.addProperty(SUMMONER_PUUID, puuid);
                data.addProperty("statusMessage", statusMessage);
                data.addProperty("name", name);
                data.addProperty("iconId", iconId);
                data.addProperty("id", id);
                data.addProperty(SUMMONER_SUMMONER_ID, summonerId);
                data.addProperty("availability", availability);
                Util.copyJsonAttrib("groupId", backendFriendObject, data);
                Util.copyJsonAttrib("lol", backendFriendObject, data);

                return data;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return data;
        }



        public JsonObject getCurrentLobbyState () {
            return currentLobbyState;
        }

//        private JsonObject beToFeLobbyInfo (JsonObject data){
//            if (data == null || data.isEmpty()) {
//                return null;
//            }
//
//            JsonObject feData = new JsonObject();
//
//            Util.copyJsonAttrib("partyId", data, feData);
//            Util.copyJsonAttrib("invitations", data, feData);
//
//            JsonObject gameConfig = data.get("gameConfig").getAsJsonObject();
//            JsonObject feGameConfig = new JsonObject();
//
//            //TODO: Add Custom Game support; add TFT Support
//            Util.copyJsonAttributes(gameConfig, feGameConfig, "queueId", "showPositionSelector", "isCustom", "maxLobbySize", "allowablePremadeSizes", "mapId", "gameMode");
//
//            JsonObject localMember = data.get("localMember").getAsJsonObject();
//            JsonObject feLocalMember = beLobbyMemberToFeLobbyMember(localMember);
//
//            JsonArray members = data.get("members").getAsJsonArray();
//            JsonArray feMembers = new JsonArray();
//            int j = 0;
//            feMembers.put(indexToFEIndex(0), feLocalMember);
//            j++;
//            for (int i = 0; i < members.length(); i++) {
//                int actualIndex = indexToFEIndex(j);
//                JSONObject currentMember = beLobbyMemberToFeLobbyMember(members.getJSONObject(i));
//                if (currentMember.getString(SUMMONER_PUUID).equals(feLocalMember.getString(SUMMONER_PUUID))) {
//                    continue;
//                }
//                feMembers.put(actualIndex, currentMember);
//                j++;
//            }
//            for (; j < MAX_LOBBY_SIZE; j++) {
//                feMembers.put(indexToFEIndex(j), new JSONObject());
//            }
//
//            feData.put("gameConfig", feGameConfig);
//            feData.put("localMember", feLocalMember);
//            feData.put("members", feMembers);
//            return feData;
//        }

//        private int indexToFEIndex ( int preParsedIndex){
//            int actualIndex = 0;
//            int diff = indexDiff(preParsedIndex);
//
//            actualIndex = MAX_LOBBY_HALFS_INDEX + diff;
//            return actualIndex;
//        }
//
//        private int indexDiff ( int index){
//            if (index % 2 == 0) {
//                index /= 2;
//                return index;
//            } else return -indexDiff(index + 1);
//        }
//
//        public JSONObject updateFELobby (JSONObject data){
//            JSONObject updatedFEData = beToFeLobbyInfo(data);
//            if (updatedFEData == null) {
//                currentLobbyState = null;
//                return null;
//            }
//            if (updatedFEData.similar(currentLobbyState)) {
//                log("No FE relevant Lobby update");
//                return null;
//            }
//            currentLobbyState = updatedFEData;
//            return updatedFEData;
//        }
//
//        //DONE
//        public JSONObject updateFEChampSelectSession (JSONObject data){
//            JSONObject updatedFEData = beToFEChampSelectSession(data);
//            if (updatedFEData == null) {
//                return null;
//            }
//            if (updatedFEData.similar(currentChampSelectState)) {
//                return null;
//            }
//            currentChampSelectState = updatedFEData;
//            return updatedFEData;
//        }

//        public JSONObject beToFEChampSelectSession (JSONObject data){
//            //Idea: Store every member of "myTeam" and "theirTeam" in a HashMap, lookup via actorCellId
//            //This would allow modifying each entry in myTeam and their team via the action tab => Hovering / Pick Intent / Ban Hover
//            JSONObject feChampSelect = new JSONObject();
//
//            copyJsonAttrib("isCustomGame", data, feChampSelect); //This might need to trigger further changes
//            copyJsonAttrib("localPlayerCellId", data, feChampSelect);
//            copyJsonAttrib("gameId", data, feChampSelect);
//            copyJsonAttrib("hasSimultaneousBans", data, feChampSelect);
//            copyJsonAttrib("skipChampionSelect", data, feChampSelect);
//            copyJsonAttrib("benchEnabled", data, feChampSelect);
//            copyJsonAttrib("rerollsRemaining", data, feChampSelect);
//
//            //Handle Actions
//            //We only need to update via the last action / NO doest work
//            copyJsonAttrib("actions", data, feChampSelect);
//            JSONArray actions = data.getJSONArray("actions");
//            beActionToFEAction(actions);
//
//            JSONObject feTimer = new JSONObject();
//            JSONObject timer = data.getJSONObject("timer");
//
//            //MyTeam
//            int localPlayerCellId = data.getInt("localPlayerCellId");
//            JSONArray feMyTeam = data.getJSONArray("myTeam");
//            for (int i = 0; i < feMyTeam.length(); i++) {
//                int playerCellId = feMyTeam.getJSONObject(i).getInt("cellId");
//                JSONObject playerObject = teamMemberToSessionMap(feMyTeam.getJSONObject(i), timer);
//                if (playerCellId == localPlayerCellId) {
//                    feChampSelect.put("localPlayerPhase", playerObject.getString("state"));
//                }
//                feMyTeam.put(i, playerObject);
//            }
//            feChampSelect.put("myTeam", feMyTeam);
//
//            //TheirTeam
//            copyJsonAttrib("theirTeam", data, feChampSelect);
//            JSONArray feTheirTeam = data.getJSONArray("myTeam");
//            for (int i = 0; i < feMyTeam.length(); i++) {
//                log(feTheirTeam.getJSONObject(i), MainInitiator.LOG_LEVEL.INFO);
//                feTheirTeam.put(i, enemyMemberToSessionMap(feTheirTeam.getJSONObject(i), timer));
//            }
//
//            copyJsonAttrib("phase", timer, feTimer);
//            copyJsonAttrib("isInfinite", timer, feTimer);
//
//            feChampSelect.put("timer", feTimer);
//
//            JSONObject bans = data.getJSONObject("bans");
//            JSONObject feBans = new JSONObject();
//
//            copyJsonAttrib("theirTeamBans", bans, feBans);
//            copyJsonAttrib("myTeamBans", bans, feBans);
//            copyJsonAttrib("numBans", bans, feBans);
//
//
//            feChampSelect.put("bans", feBans);
//
//            return feChampSelect;
//        }
//
//        private JSONObject teamMemberToSessionMap (JSONObject feMember, JSONObject timer){
//            Integer cellId = feMember.getInt("cellId");
//            JSONObject mappedAction = cellIdActionMap.get(cellId);
//            if (mappedAction == null || mappedAction.isEmpty()) {
//                log("No fitting action found for cellId: " + cellId);
//            } else {
//
//                copyJsonAttrib("pickAction", mappedAction, feMember);
//                copyJsonAttrib("banAction", mappedAction, feMember);
//            }
//
//            JSONObject phaseObject = new JSONObject();
//            copyJsonAttrib("phase", timer, phaseObject);
//            copyJsonAttrib("pickAction", mappedAction, phaseObject);
//            copyJsonAttrib("banAction", mappedAction, phaseObject);
//
//            ChampSelectState state = ChampSelectState.fromParameters(phaseObject);
//            if (state == null) {
//                log(feMember);
//                log(timer);
//                log("No fitting state found for cellId: " + cellId);
//            } else {
//
//                feMember.put("state", state.name());
//            }
//            cellIdMemberMap.put(cellId, feMember);
//            return feMember;
//        }
//
//        private JSONObject enemyMemberToSessionMap (JSONObject feMember, JSONObject timer){
//            Integer cellId = feMember.getInt("cellId");
//            JSONObject mappedAction = cellIdActionMap.get(cellId);
//            if (mappedAction == null || mappedAction.isEmpty()) {
//                log("[ENEMY]: No fitting action found for cellId: " + cellId);
//            } else {
//                copyJsonAttrib("pickAction", mappedAction, feMember);
//                copyJsonAttrib("banAction", mappedAction, feMember);
//            }
//
//            JSONObject phaseObject = new JSONObject();
//            copyJsonAttrib("phase", timer, phaseObject);
//            copyJsonAttrib("pickAction", mappedAction, phaseObject);
//            copyJsonAttrib("banAction", mappedAction, phaseObject);
//
//            ChampSelectState state = ChampSelectState.fromParameters(phaseObject);
//            if (state == null) {
//                log(feMember);
//                log(timer);
//                log("No fitting state found for cellId: " + cellId);
//            } else {
//                feMember.put("state", state.name());
//            }
//            cellIdMemberMap.put(cellId, feMember);
//            return feMember;
//        }
//
//        private void updateInternalActionMapping (JSONObject singleAction){
//            Integer actorCellId = singleAction.getInt("actorCellId");
//            Boolean completed = singleAction.getBoolean("completed");
//            Boolean inProgress = singleAction.getBoolean("isInProgress");
//            Boolean isAllyAction = singleAction.getBoolean("isAllyAction");
//            Integer championId = singleAction.getInt("championId");
//            Integer id = singleAction.getInt("id");
//            String type = singleAction.getString("type");
//            cellIdActionMap.compute(actorCellId, (k, v) -> {
//                if (v == null || v.isEmpty()) {
//                    v = new JSONObject();
//                }
//                JSONObject currentAction = new JSONObject();
//                if (!isAllyAction) {
//                    log("[CHAMP-SELECT-DEBUG]: ENEMY ACTION AT ID: " + id, MainInitiator.LOG_LEVEL.WARN);
//                    log("[CHAMP-SELECT-DEBUG]: ENEMY ACTION TYPE: " + type, MainInitiator.LOG_LEVEL.WARN);
//                }
//                currentAction.put("type", type);
//                currentAction.put("completed", completed);
//                currentAction.put("isInProgress", inProgress);
//                currentAction.put("championId", championId);
//                currentAction.put("id", id);
//                switch (type) {
//                    case "pick":
//                        v.put("pickAction", currentAction);
//                        break;
//                    case "ban":
//                        v.put("banAction", currentAction);
//                        break;
//                    default:
//                        log("Unkown Type: " + type, MainInitiator.LOG_LEVEL.ERROR);
//                        break;
//                }
//                return v;
//            });
//
//        }
//
//        private int performChampionAction (String actionType, Integer championId,boolean lockIn) throws IOException {
//            if (currentChampSelectState == null || currentChampSelectState.isEmpty() || championId == null) {
//                return -1;
//            }
//
//            if (!currentChampSelectState.has("localPlayerCellId")) {
//                return -1;
//            }
//
//            Integer localPlayerCellId = currentChampSelectState.getInt("localPlayerCellId");
//            JSONObject actionBundle = cellIdActionMap.get(localPlayerCellId);
//
//            if (actionBundle == null || actionBundle.isEmpty()) {
//                return -1;
//            }
//
//            JSONObject actionObject;
//
//            if (actionType.equals("pick")) {
//                actionObject = actionBundle.getJSONObject("pickAction");
//            } else if (actionType.equals("ban")) {
//                actionObject = actionBundle.getJSONObject("banAction");
//            } else {
//                return -1;
//            }
//
//            int actionId = actionObject.getInt("id");
//            JSONObject hoverAction = new JSONObject();
//            hoverAction.put("championId", championId);
//
//            if (lockIn) {
//                if ("pick".equals(actionType)) {
//                    JSONObject soundObject = new JSONObject();
//                    soundObject.put("source", "/lol-game-data/assets/v1/champion-sfx-audios/" + championId + ".ogg");
//                    mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(INSTRUCTION_PLAY_SOUND, soundObject));
//                    new Timer().schedule(new TimerTask() {
//                        @Override
//                        public void run() {
//                            JSONObject voiceLineObject = new JSONObject();
//                            voiceLineObject.put("source", "/lol-game-data/assets/v1/champion-choose-vo/" + championId + ".ogg");
//                            mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(INSTRUCTION_PLAY_SOUND, voiceLineObject));
//                        }
//                    }, 300);
//                } //else {
////                JSONObject soundObject = new JSONObject();
////                soundObject.put("source", "/lol-game-data/assets/v1/champion-sfx-audios/"+ championId+".ogg");
////                JSONObject voiceLineObject = new JSONObject();
////                voiceLineObject.put("source", "/lol-game-data/assets/v1/champion-ban-vo/"+ championId+".ogg");
////                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(INSTRUCTION_PLAY_SOUND, soundObject));
////                new Timer().schedule(new TimerTask() {
////                    @Override
////                    public void run() {
////                        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(INSTRUCTION_PLAY_SOUND, voiceLineObject));
////                    }
////                }, 300);
////            }
//                hoverAction.put("completed", true);
//            }
//
//            String request = "/lol-champ-select/v1/session/actions/" + actionId;
//            HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, request, hoverAction.toString());
//            return con.getResponseCode();
//        }
//
//        public int pickChampion (Integer championId,boolean lockIn) throws IOException {
//            return performChampionAction("pick", championId, lockIn);
//        }
//
//        public int banChampion (Integer championId,boolean lockIn) throws IOException {
//            return performChampionAction("ban", championId, lockIn);
//        }
//
//        private void beActionToFEAction (JSONArray action){
//            if (action == null || action.isEmpty()) return;
//            for (int i = 0; i < action.length(); i++) {
//                JSONArray subAction = action.getJSONArray(i);
//                if (subAction == null || subAction.isEmpty()) continue;
//                for (int j = 0; j < subAction.length(); j++) {
//                    JSONObject singleAction = subAction.getJSONObject(j);
//                    updateInternalActionMapping(singleAction);
//                }
//            }
//        }
//
//        public JSONObject updateFERegaliaInfo (BigInteger summonerId){
//
//            JSONObject regalia = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-regalia/v2/summoners/" + summonerId.toString() + "/regalia"));
//            regaliaMap.put(summonerId, regalia);
//            log(regalia);
//
//            if (currentLobbyState == null) return regalia;
//            JSONArray members = currentLobbyState.getJSONArray("members");
//            for (int i = 0; i < members.length(); i++) {
//                JSONObject member = members.getJSONObject(i);
//                if (member != null && !member.isEmpty() && member.has(SUMMONER_SUMMONER_ID)) {
//                    if (!summonerId.equals(member.getBigInteger(SUMMONER_SUMMONER_ID))) {
//                        continue;
//                    }
//                    member.put("regalia", regalia);
//                    break;
//                }
//            }
//            currentLobbyState.put("members", members);
//
//            return regalia;
//        }
//
//
//        public JSONObject getFERegaliaInfo (BigInteger summonerId){
//            JSONObject regaliaObject = regaliaMap.get(summonerId);
//            if (regaliaObject == null) {
//                return updateFERegaliaInfo(summonerId);
//            }
//            return regaliaObject;
//        }
//
//        private JSONObject beLobbyMemberToFeLobbyMember (JSONObject member){
//            JSONObject feMember = new JSONObject();
//            if (member == null) return feMember;
//            Util.copyJsonAttrib("isLeader", member, feMember);
//            copyJsonAttrib("isBot", member, feMember);
//            copyJsonAttrib(SUMMONER_PUUID, member, feMember);
//            copyJsonAttrib("summonerLevel", member, feMember);
//            copyJsonAttrib("ready", member, feMember);
//            copyJsonAttrib(SUMMONER_SUMMONER_ID, member, feMember);
//            copyJsonAttrib("isLeader", member, feMember);
//            copyJsonAttrib("summonerName", member, feMember);
//            copyJsonAttrib("secondPositionPreference", member, feMember);
//            copyJsonAttrib("firstPositionPreference", member, feMember);
//            copyJsonAttrib("summonerIconId", member, feMember);
//
//            feMember.put("regalia", getFERegaliaInfo(feMember.getBigInteger(SUMMONER_SUMMONER_ID)));
//            return feMember;
//        }
//
//        private void copyJsonAttrib (String key, JSONObject src, JSONObject dst){
//            if (src == null || dst == null) return;
//            if (src.has(key)) {
//                Object object = src.get(key);
//                if (object != null) {
//                    dst.put(key, object);
//                }
//            }
//        }

//        public JSONObject beToFeGameflowInfo (JSONObject currentGameflowPhase){
//            String phase = currentGameflowPhase.getString("phase");
//            JSONObject gameflowContainer = new JSONObject();
//            gameflowContainer.put("GameflowPhase", phase);
//            return gameflowContainer;
//        }

//        private void fetchChampionJson () {
//            JsonArray championJson = mainInitiator.getConnectionManager().getResponseBodyAsJsonArray(mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-data/assets/v1/champion-summary.json"));
//            JsonObject parsedChampionJson = new JsonObject();
//            if (championJson != null && !championJson.isEmpty()) {
//                for (int i = 0; i < championJson.size(); i++) {
//                    JsonObject champion = championJson.get(i).getAsJsonObject();
//                    int id = champion.get("id").getAsInt();
//                    parsedChampionJson.add(Integer.toString(id), champion);
//                }
//            }
//            this.championJson = parsedChampionJson;
//        }

//        private void fetchSummonerSpells () {
//
//            JSONArray summonerSpells = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-data/assets/v1/summoner-spells.json"));
//            JSONObject parsedSummonerSpells = new JSONObject();
//            if (summonerSpells != null && !summonerSpells.isEmpty()) {
//                for (int i = 0; i < summonerSpells.length(); i++) {
//                    JSONObject summonerSpell = summonerSpells.getJSONObject(i);
//                    int id = summonerSpell.getInt("id");
//                    parsedSummonerSpells.put(Integer.toString(id), summonerSpell);
//                }
//            }
//            this.summonerSpellJson = parsedSummonerSpells;
//
//
//        }

//        private void fetchChromaSkinId () {
//            JSONObject chromaSkinId = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-data/assets/v1/skins.json"));
//            JSONObject parsedChromaSkinId = new JSONObject();
//            if (chromaSkinId != null && !chromaSkinId.isEmpty()) {
//                for (String s : chromaSkinId.keySet()) {
//                    JSONObject skin = chromaSkinId.getJSONObject(s);
//                    int id = skin.getInt("id");
//                    boolean hasChromas = skin.has("chromas");
//                    if (hasChromas) {
//                        JSONArray chromas = skin.getJSONArray("chromas");
//                        if (chromas != null && chromas.length() > 0) {
//                            for (int i = 0; i < chromas.length(); i++) {
//                                JSONObject chroma = chromas.getJSONObject(i);
//                                if (chroma != null && chroma.has("id")) {
//                                    int chromaId = chroma.getInt("id");
//                                    parsedChromaSkinId.put("" + chromaId, id);
//                                }
//                            }
//                        }
//                    }
//                    parsedChromaSkinId.put("" + id, id);
//                }
//            }
//            this.chromaSkinId = parsedChromaSkinId;
//
//        }

        public static String getEventDataString (String event, JsonObject data){
            JsonObject dataToSend = new JsonObject();
            dataToSend.addProperty("event", event);
            dataToSend.add("data", data);
            return dataToSend.toString();
        }

        public static String getEventDataString (String event, JsonArray data){
            JsonObject dataToSend = new JsonObject();
            dataToSend.addProperty("event", event);
            dataToSend.add("data", data);
            return dataToSend.toString();
        }

        @Deprecated
        public static String getDataTransferString (String dataType, JsonObject data){
            JsonObject dataToSend = new JsonObject();
            dataToSend.addProperty("event", "DataTransfer");

            JsonObject dataTransfer = new JsonObject();
            dataTransfer.addProperty("dataType", dataType);
            dataTransfer.add("data", data);

            dataToSend.add("data", dataTransfer);
            return dataToSend.toString();
        }

        public static String getDataTransferString (String dataType, JsonArray data){
            JsonObject dataToSend = new JsonObject();
            dataToSend.addProperty("event", "DataTransfer");

            JsonObject dataTransfer = new JsonObject();
            dataTransfer.addProperty("dataType", dataType);
            dataToSend.add("data", data);
            return dataToSend.toString();
        }

        public JsonObject getLoot () {
            return lootJsonObject;
        }

        public JsonObject getChromaSkinId () {
            return chromaSkinId;
        }

        public JsonObject getChampionJson () {
            return championJson;
        }

        public JsonObject getSummonerSpellJson () {
            return summonerSpellJson;
        }

        private void log (Object o){
            log(o, Starter.LOG_LEVEL.DEBUG);
        }

        private void log (Object o, Starter.LOG_LEVEL l){
            starter.log(this.getClass().getSimpleName() + ": " + o);
        }
    }
