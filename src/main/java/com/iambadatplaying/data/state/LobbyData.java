package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.map.RegaliaManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;
import java.util.Timer;

public class LobbyData extends StateDataManager {

    private static final String UPDATE_TYPE_LOBBY = "LobbyUpdate";

    private static final String LOBBY_URI = "/lol-lobby/v2/lobby";

    private static final int MAX_LOBBY_HALFS_INDEX = 2;

    public LobbyData(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    @Override
    public void doInitialize() {

    }

    @Override
    protected Optional<JSONObject> fetchCurrentState() {
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-lobby/v2/lobby");
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, con);
        if (!data.has("errorCode")) return backendToFrontendLobby(data);
        log("Cant fetch current state, maybe not in a lobby ?: " + data.getString("message"), MainInitiator.LOG_LEVEL.WARN);
        return Optional.empty();
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return LOBBY_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type,JSONObject data) {
        switch (type) {
            case "Delete":
                resetState();
                break;
            case "Create":
            case "Update":
                Optional<JSONObject> updatedFEData = backendToFrontendLobby(data);
                if (!updatedFEData.isPresent()) return;
                JSONObject updatedState = updatedFEData.get();
                if (updatedState.similar(currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
            default:
                log("Unknown Type " + type);
                break;
        }
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_LOBBY, currentState));
    }

    private Optional<JSONObject> backendToFrontendLobby(JSONObject data) {
        JSONObject frontendData = new JSONObject();

        Util.copyJsonAttributes(data, frontendData, "partyId", "invitations");

        Optional<JSONObject> optGameConfig = Util.getJSONObject(data, "gameConfig");
        if (!optGameConfig.isPresent()) {
            log("Failed to get gameConfig", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JSONObject gameConfig = optGameConfig.get();
        JSONObject frontendGameConfig = new JSONObject();

        Util.copyJsonAttributes(gameConfig, frontendGameConfig, "queueId", "showPositionSelector", "isCustom", "maxLobbySize", "allowablePremadeSizes", "mapId", "gameMode");

        Optional<JSONObject> optLocalMember = Util.getJSONObject(data, "localMember");

        if (!optLocalMember.isPresent()) {
            log("Failed to get localMember", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JSONObject localMember = optLocalMember.get();
        JSONObject frontendLocalMember = backendToFrontendLobbyMember(localMember);

        Optional<JSONArray> optMembers = Util.getJSONArray(data, "members");
        if (!optMembers.isPresent()) {
            log("Failed to get members", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JSONArray members = optMembers.get();
        JSONArray frontendMembers = new JSONArray();
        int j = 0;
        frontendMembers.put(indexToFEIndex(0), frontendLocalMember);
        j++;
        for (int i = 0; i < members.length(); i++) {
            int actualIndex = indexToFEIndex(j);
            JSONObject currentMember = backendToFrontendLobbyMember(members.getJSONObject(i));
            if (currentMember.getString("puuid").equals(frontendLocalMember.getString("puuid"))) {
                continue;
            }
            frontendMembers.put(actualIndex, currentMember);
            j++;
        }
        for (; j < 5; j++) {
            frontendMembers.put(indexToFEIndex(j), new JSONObject());
        }

        frontendData.put("gameConfig", frontendGameConfig);
        frontendData.put("members", frontendMembers);
        frontendData.put("localMember", frontendLocalMember);
        return Optional.of(frontendData);
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

    private JSONObject backendToFrontendLobbyMember(JSONObject member) {
        JSONObject frontendMember = member;

       JSONObject regalia = (JSONObject) mainInitiator.getReworkedDataManager().getMapManagers(RegaliaManager.class.getSimpleName()).get(member.getBigInteger("summonerId"));
       frontendMember.put("regalia", regalia);

       return frontendMember;
    }


    public void doShutdown() {

    }
}
