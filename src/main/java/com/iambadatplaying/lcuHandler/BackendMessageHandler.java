package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.structs.messaging.Conversation;
import com.iambadatplaying.structs.messaging.Message;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackendMessageHandler {

    private MainInitiator mainInitiator;

    private final static String lolGameflowV1GameflowPhase = "/lol-gameflow/v1/session";
    private final static String lolLobbyV2Lobby = "/lol-lobby/v2/lobby";
    private final static String lolChampSelectV1Session = "/lol-champ-select/v1/session";
    private static final String lolLootV2PlayerLootMap = "/lol-loot/v2/player-loot-map";


    private final static String lolChatV1FriendsPattern = "/lol-chat/v1/friends/(.*)"; //Matching Group will return the puuid
    private final static String lolRegaliaV2SummonerPattern = "/lol-regalia/v2/summoners/(.*)/regalia/async"; //Matching Group will return the summonerId

    private Pattern lolChatV1FriendsPatternCompiled;
    private Pattern lolRegaliaV2SummonerPatternCompiled;

    private final static HashMap<String, JSONObject> summonersMap = new HashMap<>();

    public BackendMessageHandler(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
        initAllPatterns();
    }

    private void initAllPatterns() {
        lolChatV1FriendsPatternCompiled = Pattern.compile(lolChatV1FriendsPattern);
        lolRegaliaV2SummonerPatternCompiled = Pattern.compile(lolRegaliaV2SummonerPattern);
    }

    //TODO: Maybe just export as Observable
    public void handleMessage(String message) {
        JSONArray messageArray = null;
        if (message == null || message.isEmpty()) {
            return;
        }
        try {
            messageArray = new JSONArray(message);
            if (!messageArray.isEmpty()) { //[8,"endpoint",{}]
                JSONObject jsonData = messageArray.getJSONObject(2);
                String uri = jsonData.getString("uri");
                String type = jsonData.getString("eventType");
                log(type + " " + uri + ": " +jsonData.get("data"));
                        switch (uri) {
                            case lolGameflowV1GameflowPhase:
                                handle_lol_gameflow_v1_gameflow_phase(jsonData); //TODO: Doesnt work, replace with Gameflow session instead
                                break;
                            case lolLobbyV2Lobby: //Works as intended
                                handle_lol_lobby_v2_lobby(jsonData);
                                break;
                            case lolChampSelectV1Session: //Works as intended
                                handle_lol_champ_select_v1_session(jsonData);
                                break;
                            case lolLootV2PlayerLootMap:
                                handle_lol_loot_v2_player_loot_map(jsonData);
                                break;
                            case "/riot-messaging-service/v1/message/honor/post-game-ceremony":
                                testHonorReveal(jsonData);
                                break;
                            default:
                                if (uri.matches(lolChatV1FriendsPattern)) { //Works as intended
                                    handle_lol_chat_v1_friends(jsonData);
                                } else if (uri.matches(lolRegaliaV2SummonerPattern)) { //Works as intended
                                    handle_lol_regalia_v2_summoners(jsonData);
                                } else if (uri.startsWith("/lol-chat/v1/conversations/")) {
                                    messageTest(jsonData);
                                }
                                break;
                        }
                }
        } catch (Exception e) {
            log("[handleMessage]: " + e + " - "+message, MainInitiator.LOG_LEVEL.ERROR);
            return;
        }
    }

    private void handle_lol_loot_v2_player_loot_map(JSONObject jsonData) {
        JSONObject actualData = jsonData.getJSONObject("data");
        log(actualData.length());
        JSONObject updatedLoot = mainInitiator.getDataManager().updateFELootMap(actualData);
        if (updatedLoot == null) return;
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("LootUpdate", mainInitiator.getDataManager().getLoot()));
    }

    private void messageTest(JSONObject jsonData) {
        try {
            String uri = jsonData.getString("uri");
            if (uri.endsWith("/messages")) {
                String conversationId = extractConversationId(uri);
                if (conversationId == null) return;
                if (!conversationId.contains("@")) {
                    try {
                        URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
                    } catch (Exception e) {

                    }
                }

                Conversation conversation = mainInitiator.getDataManager().getConversation(conversationId);
                if (conversation == null) return;
                log("Conversation: " + conversation.getId(), MainInitiator.LOG_LEVEL.INFO);

                JSONArray messages = jsonData.getJSONArray("data");

                ArrayList<Message> messageList = Message.createMessageList(messages);
                //Replacement is needed to avoid bugs
                conversation.overrideMessages(messageList);

                //TODO: Update Frontend
                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("ConversationUpdate", conversation.toJsonObject()));
                log("Conversation: " + conversation.getId() + " is now complete", MainInitiator.LOG_LEVEL.INFO);
            } else {
                String[] messageInfo = extractMessageInfo(uri);
                if ((messageInfo[0] == null) || (messageInfo[1] == null)) return;
                if (!messageInfo[0].endsWith("pvp.net")) return;
                JSONObject actualData = jsonData.getJSONObject("data");
                if (!actualData.has("body")) return;
                String message = actualData.getString("body");
                String fromId = actualData.getString("fromId");
                String type = actualData.getString("type");


                mainInitiator.getDataManager().addConversationMessage(messageInfo[0], actualData);
            }
        } catch (Exception e) {
        }
    }

    //TODO: This method is very ugly, and may be prone to errors
    private String[] extractMessageInfo(String uri) {
        String regex = "/lol-chat/v1/conversations/([^/]+)/messages/([^/]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(uri);

        String[] result = new String[2];

        if (matcher.find()) {
            result[0] = matcher.group(1).trim(); // Capture group 1
            result[1] = matcher.group(2).trim(); // Capture group 2
        }
        return result;
    }


    private String extractConversationId(String uri) {
        String regex = "/lol-chat/v1/conversations/([^/]+)/messages";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(uri);

        String result = null;

        if (matcher.find()) {
            result = matcher.group(1).trim(); // Capture group 1
        }
        return result;
    }

    private void testHonorReveal(JSONObject jsonData) {
        try {
            JSONObject actualData = jsonData.getJSONObject("data");
            if (actualData.has("payload")) {
                String payload = actualData.getString("payload");
                JSONArray payloadArray = new JSONArray(payload);
                for (int i = 0; i < payloadArray.length(); i++) {
                    JSONObject payloadObject = payloadArray.getJSONObject(i);
                    String receiverPuuid = payloadObject.getString("puuid");

                    String receiverName;
                    if (summonersMap.get(receiverPuuid) == null) {
                        JSONObject receiverData = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-summoner/v2/summoners/puuid/"+ receiverPuuid));
                        summonersMap.put(receiverPuuid, receiverData);
                        receiverName = receiverData.getString("displayName");
                    } else {
                        receiverName = summonersMap.get(receiverPuuid).getString("displayName");
                    }
                    log(receiverName + " received the following honors:" , MainInitiator.LOG_LEVEL.INFO);
                    JSONArray honors = payloadObject.getJSONArray("honors");
                    for (int j = 0; j < honors.length(); j++) {
                        JSONObject honor = honors.getJSONObject(j);
                        String honorRelationship = honor.getString("voterRelationship");
                        String honorCategory = honor.getString("honorCategory");
                        String senderPuuid = honor.getString("senderPuuid");

                        String senderName;
                        if (summonersMap.get(senderPuuid) == null) {
                            JSONObject senderData = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-summoner/v2/summoners/puuid/"+ senderPuuid));
                            summonersMap.put(senderPuuid, senderData);
                            senderName = senderData.getString("displayName");
                        } else {
                            senderName = summonersMap.get(senderPuuid).getString("displayName");
                        }
                        log(honorCategory + " from " + senderName , MainInitiator.LOG_LEVEL.INFO);
                    }
                    log("--------------------" , MainInitiator.LOG_LEVEL.INFO);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //For OnJsonApiEvent_lol-loot_v2_player-loot-map; the updated / event Value is the new map;

    private void handle_lol_champ_select_v1_session(JSONObject jsonData) {
        return;
//        String uri = jsonData.getString("uri");
//        String type = jsonData.getString("eventType");
//        JSONObject actualData = jsonData.getJSONObject("data");
//        switch (type) {
//            case "Create":
//            case "Update":
//                log(actualData);
//                JSONObject data = mainInitiator.getDataManager().updateFEChampSelectSession(actualData);
//                if (data == null) return;
//                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("ChampSelectUpdate", data));
//                break;
//            case "Delete":
//                mainInitiator.getDataManager().resetChampSelectSession();
//                break;
//            default:
//                log("OnJsonApiEvent_lol-champ-select_v1_session - Unkown type: " +type);
//            break;
//        }
    }

    private void handle_lol_regalia_v2_summoners(JSONObject jsonData) {
        String uri = jsonData.getString("uri");
        String summonerIdStr = uri.replaceAll(DataManager.REGALIA_REGEX, "$1");

        summonerIdStr  = summonerIdStr .replaceAll("^/+", "").replaceAll("/+$", "");;
        BigInteger summonerId = new BigInteger(summonerIdStr);

        mainInitiator.getDataManager().updateFERegaliaInfo(summonerId);
        JSONObject currentState =  mainInitiator.getDataManager().getCurrentLobbyState();
        if (currentState == null) return;
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("LobbyUpdate", currentState));
    }

    private void handle_lol_chat_v1_friends(JSONObject jsonData) {
        JSONObject actualData = jsonData.getJSONObject("data");
        JSONObject data = mainInitiator.getDataManager().updateFEFriend(actualData);
        if (data == null || data.isEmpty()) return;
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("FriendListUpdate", data));
    }

    private void handle_lol_gameflow_v1_gameflow_phase(JSONObject jsonData) {
//        JSONObject actualData = jsonData.getJSONObject("data");
//        JSONObject data = mainInitiator.getDataManager().updateFEGameflowStatus(actualData);
//        if (data == null || data.isEmpty()) return;
//        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("GameflowPhaseUpdate",data));
    }

    private void handle_lol_lobby_v2_lobby(JSONObject jsonData) {
        String uri = jsonData.getString("uri");
        switch (uri) {
            case "/lol-lobby/v2/lobby":;
                String eventType = jsonData.getString("eventType");
                JSONObject data = null;
                if ("Delete".equals(eventType.trim())) {
                    data = mainInitiator.getDataManager().updateFELobby(new JSONObject());
                } else {
                    JSONObject actualData = jsonData.getJSONObject("data");
                    data = mainInitiator.getDataManager().updateFELobby(actualData);
                }
                if (data == null) return;
                mainInitiator.getServer().sendToAllSessions(mainInitiator.getDataManager().getEventDataString("LobbyUpdate", data));
            break;
            default:
            break;
        }
    }

    private void log(Object o, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + o, level);
    }

    private void log(Object o) {
        mainInitiator.log(this.getClass().getSimpleName() +": " +o);
    }
}
