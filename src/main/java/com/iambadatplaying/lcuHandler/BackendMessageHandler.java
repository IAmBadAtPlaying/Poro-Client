package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;

public class BackendMessageHandler {

    private MainInitiator mainInitiator;

    public BackendMessageHandler(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void handleMessage(String message) {
        JSONArray messageArray = null;
        if (message == null || message.isEmpty()) {
            return;
        }
        try {
            messageArray = new JSONArray(message);
            if (messageArray != null && !messageArray.isEmpty()) { //[8,"endpoint",{}]
                String endpoint = messageArray.getString(1);
                JSONObject jsonData = messageArray.getJSONObject(2);
                switch (endpoint) {
                    case "OnJsonApiEvent_lol-chat_v1_friends":
                        handle_lol_chat_v1_friends(jsonData);
                        break;
                    case "OnJsonApiEvent_lol-gameflow_v1_gameflow-phase":
                        handle_lol_gameflow_v1_gameflow_phase(jsonData);
                        break;
                    case "OnJsonApiEvent_lol-lobby_v2_lobby":
                        handle_lol_lobby_v2_lobby(jsonData);
                        break;
                    case "OnJsonApiEvent_lol-champ-select_v1_session":
                        handle_lol_champ_select_v1_session(jsonData);
                        break;
                    case "OnJsonApiEvent_lol-regalia_v2_summoners":
                        handle_lol_regalia_v2_summoners(jsonData);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            return;
        }
    }

    private void handle_lol_champ_select_v1_session(JSONObject jsonData) {
        String uri = jsonData.getString("eventType");
        JSONObject actualData = jsonData.getJSONObject("data");
        switch (uri) {
            case "Create":
            case "Update":
                JSONObject data = mainInitiator.getDataManager().updateFEChampSelectSession(actualData);

                if (data == null) return;
                mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("ChampSelectUpdate", data));
                break;
            case "Delete":
            break;
            default:
                log("OnJsonApiEvent_lol-champ-select_v1_session - Unkown URI: " +uri);
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
        String actualData = jsonData.getString("data");
        JSONObject data = mainInitiator.getDataManager().updateFEGameflowStatus(actualData);
        if (data == null || data.isEmpty()) return;
        mainInitiator.getServer().sendToAllSessions(mainInitiator.getDataManager().getEventDataString("GameflowPhaseUpdate",data));
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
        MainInitiator.log(this.getClass().getName() +": " + o, level);
    }

    private void log(Object o) {
        MainInitiator.log(this.getClass().getName() +": " +o);
    }
}
