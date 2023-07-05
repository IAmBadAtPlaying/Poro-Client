package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DataManager {

    private MainInitiator mainInitiator;

    private Map<String, JSONObject> synchronizedFriendListMap;
    private Map<BigInteger, JSONObject> regaliaMap;

    private static Integer MAX_LOBBY_SIZE = 5;
    private static Integer MAX_LOBBY_HALFS_INDEX = 2;

    private JSONObject currentLobbyState;
    private JSONObject currentGameflowState;

    public DataManager(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public static String REGALIA_REGEX = "/lol-regalia/v2/summoners/(.*?)/regalia/async";

    public JSONObject getFEGameflowStatus() {
        JSONObject feGameflowObject = new JSONObject();
        if (currentLobbyState == null) {
            String currentGameflowString = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-gameflow/v1/gameflow-phase"));
            feGameflowObject = beToFeGameflowInfo(currentGameflowString);
            currentGameflowState = feGameflowObject;
        }
        return currentGameflowState;
    }

    public JSONObject updateFEGameflowStatus(String beGameflow) {
        JSONObject updatedFEGameflowObject = beToFeGameflowInfo(beGameflow);
        if (updatedFEGameflowObject == null) return null;
        if (updatedFEGameflowObject.similar(currentGameflowState)) return null;
        currentGameflowState = updatedFEGameflowObject;
        return updatedFEGameflowObject;
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
                    feFriendObject.put(friendObject.getString("puuid"),friendObject);
                    synchronizedFriendListMap.put(friendObject.getString("puuid"), friendObject);
                }
            } catch (Exception e) {

            }
        } else {
            log ("FE Friend List already initialized, returning saved values");
            for (JSONObject json : synchronizedFriendListMap.values()) {
                feFriendObject.put(json.getString("puuid"),json);
            }
        }
        return feFriendObject;
    }

    private JSONObject beToFeFriendsInfo(JSONObject backendFriendObject) {
        JSONObject data = new JSONObject();
        try {
            String availability = backendFriendObject.getString("availability");
            String puuid = backendFriendObject.getString("puuid");
            if (puuid == null || puuid.isEmpty()) return null;
            String statusMessage = backendFriendObject.getString("statusMessage");
            String name = backendFriendObject.getString("name");
            if (name == null || name.isEmpty()) return null;
            Integer iconId = backendFriendObject.getInt("icon");
            if (iconId < 1) {
                iconId = 1;
            }
            BigInteger summonerId = backendFriendObject.getBigInteger("summonerId");
            data.put("puuid", puuid);
            data.put("statusMessage", statusMessage);
            data.put("name", name);
            data.put("iconId", iconId);
            data.put("summonerId", summonerId);
            data.put("availability", availability);

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
        JSONObject currentFEData = synchronizedFriendListMap.get(updatedFEData.getString("puuid"));
        if (updatedFEData.similar(currentFEData)) {
            return null;
        }
        synchronizedFriendListMap.put(updatedFEData.getString("puuid"), updatedFEData);
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
            if (currentMember.getString("puuid").equals(feLocalMember.getString("puuid"))) {
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

        log("Index original: " + preParsedIndex + "; Index now:" + actualIndex);
        return actualIndex;
    }

    private int indexDiff(int index) {
        if (index % 2 == 0) {
            index /= 2;
            return index;
        } else return -indexDiff(index + 1);
    }

    private void modifyMemberArrayForFE(JSONArray array) {
//        JSONObject fePos2 = array.getJSONObject(0);
//        JSONObject fePos1 = array.getJSONObject(1);
//        JSONObject fePos3 = array.getJSONObject(2);
//        JSONObject fePos0 = array.getJSONObject(3);
//        JSONObject fePos4 = array.getJSONObject(4);
//
//        array.put(0, fePos0);
//        array.put(1, fePos1);
//        array.put(2, fePos2);
//        array.put(3, fePos3);
//        array.put(4, fePos4);
    }

    public JSONObject updateFELobby(JSONObject data) {
        JSONObject updatedFEData = beToFeLobbyInfo(data);
        if (updatedFEData == null) {
            currentLobbyState = null;
            return new JSONObject();
        }
        if (updatedFEData.similar(currentLobbyState)) {
            log("No FE relevant Lobby update");
            return null;
        }
        currentLobbyState = updatedFEData;
        return updatedFEData;
    }

    public JSONObject updateFERegaliaInfo(BigInteger summonerId) {

        JSONObject regalia = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-regalia/v2/summoners/"+summonerId.toString()+"/regalia"));
        regaliaMap.put(summonerId, regalia);
        log(regalia);

        if (currentLobbyState == null) return regalia;
        JSONArray members = currentLobbyState.getJSONArray("members");
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            if (member != null && !member.isEmpty() && member.has("summonerId")) {
                if (!summonerId.equals(member.getBigInteger("summonerId"))) {
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
        copyJsonAttrib("puuid", member, feMember);
        copyJsonAttrib("summonerLevel",member,feMember);
        copyJsonAttrib("ready",member,feMember);
        copyJsonAttrib("summonerId",member,feMember);
        copyJsonAttrib("isLeader",member,feMember);
        copyJsonAttrib("summonerName",member,feMember);
        copyJsonAttrib("secondPositionPreference",member,feMember);
        copyJsonAttrib("firstPositionPreference",member,feMember);
        copyJsonAttrib("summonerIconId", member, feMember);

        feMember.put("regalia", getFERegaliaInfo(feMember.getBigInteger("summonerId")));
        return feMember;
    }

    private void copyJsonAttrib(String key, JSONObject src, JSONObject dst) {
        Object object = src.get(key);
        if (object != null) {
            dst.put(key, object);
        }
    }

    public JSONObject beToFeGameflowInfo(String currentGameflowPhase) {
        currentGameflowPhase = currentGameflowPhase.trim();
        currentGameflowPhase = currentGameflowPhase.replace("\"", "");
        JSONObject gameflowContainer = new JSONObject();
        gameflowContainer.put("GameflowPhase", currentGameflowPhase.trim());
        return gameflowContainer;
    }

    public String getEventDataString(String event, JSONObject data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put("event", event);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    public String getEventDataString(String event, JSONArray data) {
        JSONObject dataToSend = new JSONObject();
        dataToSend.put("event", event);
        dataToSend.put("data", data);
        return dataToSend.toString();
    }

    public void shutdown() {
        if (synchronizedFriendListMap != null) synchronizedFriendListMap.clear();
        synchronizedFriendListMap = null;
        if (currentLobbyState != null) currentLobbyState.clear();
        currentLobbyState = null;
        if (regaliaMap != null) regaliaMap.clear();
        regaliaMap = null;

    }

    public void init() {
        this.regaliaMap = Collections.synchronizedMap(new HashMap<BigInteger, JSONObject>());
    }


    private void log(Object o) {
        log(o, MainInitiator.LOG_LEVEL.DEBUG);
    }

    private void log(Object o, MainInitiator.LOG_LEVEL l) {
        if (o != null) {
            mainInitiator.log(this.getClass().getName() + ": " + o.toString());
        } else mainInitiator.log(this.getClass().getName() + ": " + null);

    }
}
