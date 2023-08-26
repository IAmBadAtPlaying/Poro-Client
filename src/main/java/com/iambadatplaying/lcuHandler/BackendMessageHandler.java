package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.regex.Pattern;

public class BackendMessageHandler {

    private MainInitiator mainInitiator;

    private final static String lolGameflowV1GameflowPhase = "/lol-gameflow/v1/session";
    private final static String lolLobbyV2Lobby = "/lol-lobby/v2/lobby";
    private final static String lolChampSelectV1Session = "/lol-champ-select/v1/session";

    private final static String lolChatV1FriendsPattern = "/lol-chat/v1/friends/(.*)"; //Matching Group will return the puuid
    private final static String lolRegaliaV2SummonerPattern = "/lol-regalia/v2/summoners/(.*)/regalia/async"; //Matching Group will return the summonerId

    private Pattern lolChatV1FriendsPatternCompiled;
    private Pattern lolRegaliaV2SummonerPatternCompiled;

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
            if (messageArray != null && !messageArray.isEmpty()) { //[8,"endpoint",{}]
                JSONObject jsonData = messageArray.getJSONObject(2);
                String uri = jsonData.getString("uri");
                log(uri +"; " +jsonData.getJSONObject("data").toString());
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
                            default:
                                if (uri.matches(lolChatV1FriendsPattern)) { //Works as intended
                                    handle_lol_chat_v1_friends(jsonData);
                                } else if (uri.matches(lolRegaliaV2SummonerPattern)) { //Works as intended
                                    handle_lol_regalia_v2_summoners(jsonData);
                                } else {

                                }
                                break;
                        }
                }
        } catch (Exception e) {
            return;
        }
    }


    //For OnJsonApiEvent_lol-loot_v2_player-loot-map; the updated / event Value is the new map;

    private void handle_lol_champ_select_v1_session(JSONObject jsonData) {
        String uri = jsonData.getString("uri");
        String type = jsonData.getString("eventType");
        JSONObject actualData = jsonData.getJSONObject("data");
        switch (type) {
            case "Create":
            case "Update":
                log(actualData);
                JSONObject data = mainInitiator.getDataManager().updateFEChampSelectSession(actualData);
                if (data == null) return;
                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("ChampSelectUpdate", data));
                break;
            case "Delete":
                mainInitiator.getDataManager().resetChampSelectSession();
                break;
            default:
                log("OnJsonApiEvent_lol-champ-select_v1_session - Unkown type: " +type);
            break;
        }
    }

    private void handle_lol_regalia_v2_summoners(JSONObject jsonData) {
        String uri = jsonData.getString("uri");
        String summonerIdStr = uri.replaceAll(DataManager.REGALIA_REGEX, "$1");

        summonerIdStr  = summonerIdStr .replaceAll("^/+", "").replaceAll("/+$", "");;
        BigInteger summonerId = new BigInteger(summonerIdStr);

        mainInitiator.getDataManager().updateFERegaliaInfo(summonerId);
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("LobbyUpdate", mainInitiator.getDataManager().getCurrentLobbyState()));
    }

    private void handle_lol_chat_v1_friends(JSONObject jsonData) {
        JSONObject actualData = jsonData.getJSONObject("data");
        JSONObject data = mainInitiator.getDataManager().updateFEFriend(actualData);
        if (data == null || data.isEmpty()) return;
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("FriendListUpdate", data));
    }

    private void handle_lol_gameflow_v1_gameflow_phase(JSONObject jsonData) {
        JSONObject actualData = jsonData.getJSONObject("data");
        JSONObject data = mainInitiator.getDataManager().updateFEGameflowStatus(actualData);
        if (data == null || data.isEmpty()) return;
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("GameflowPhaseUpdate",data));
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
        mainInitiator.log(this.getClass().getName() +": " + o, level);
    }

    private void log(Object o) {
        mainInitiator.log(this.getClass().getName() +": " +o);
    }
}
