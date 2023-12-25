package com.iambadatplaying.data.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.map.RegaliaManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;

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
    protected Optional<JsonObject> fetchCurrentState() {
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-lobby/v2/lobby");
        JsonObject data = mainInitiator.getConnectionManager().getResponseBodyAsJsonObject(con);
        if (!data.has("errorCode")) return backendToFrontendLobby(data);
        log("Cant fetch current state, maybe not in a lobby ?: " + data.get("message").getAsString(), MainInitiator.LOG_LEVEL.WARN);
        return Optional.empty();
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return LOBBY_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case "Delete":
                resetState();
                break;
            case "Create":
            case "Update":
                if (!data.isJsonObject()) return;
                Optional<JsonObject> updatedFEData = backendToFrontendLobby(data.getAsJsonObject());
                if (!updatedFEData.isPresent()) return;
                JsonObject updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
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

    private Optional<JsonObject> backendToFrontendLobby(JsonObject data) {
        JsonObject frontendData = new JsonObject();

        Util.copyJsonAttributes(data, frontendData, "partyId", "invitations");

        Optional<JsonObject> optGameConfig = Util.getOptJSONObject(data, "gameConfig");
        if (!optGameConfig.isPresent()) {
            log("Failed to get gameConfig", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonObject gameConfig = optGameConfig.get();
        JsonObject frontendGameConfig = new JsonObject();

        Util.copyJsonAttributes(gameConfig, frontendGameConfig, "queueId", "showPositionSelector", "isCustom", "maxLobbySize", "allowablePremadeSizes", "mapId", "gameMode");

        Optional<JsonObject> optLocalMember = Util.getOptJSONObject(data, "localMember");

        if (!optLocalMember.isPresent()) {
            log("Failed to get localMember", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonObject localMember = optLocalMember.get();
        JsonObject frontendLocalMember = backendToFrontendLobbyMember(localMember);

        Optional<JsonArray> optMembers = Util.getOptJSONArray(data, "members");
        if (!optMembers.isPresent()) {
            log("Failed to get members", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonArray allowablePremadeSizes = frontendGameConfig.get("allowablePremadeSizes").getAsJsonArray();
        Integer maxLobbySize = 1;
        for (int i = 0; i < allowablePremadeSizes.size(); i++) {
            int currentSize = allowablePremadeSizes.get(i).getAsInt();
            if (currentSize > maxLobbySize) {
                maxLobbySize = currentSize;
            }
        }

        JsonArray members = optMembers.get();
        JsonArray frontendMembers = new JsonArray();
        for (int i = 0; i < maxLobbySize; i++) {
            frontendMembers.add(new JsonObject());
        }
        int j = 0;
        frontendMembers.set(indexToFEIndex(0, maxLobbySize), frontendLocalMember);
        j++;
        for (int i = 0; i < members.size(); i++) {
            int actualIndex = indexToFEIndex(j,maxLobbySize);
            JsonObject currentMember = backendToFrontendLobbyMember(members.get(i).getAsJsonObject());
            if (currentMember.get("puuid").getAsString().equals(frontendLocalMember.get("puuid").getAsString())) {
                continue;
            }
            frontendMembers.set(actualIndex, currentMember);
            j++;
        }
        for (; j < maxLobbySize; j++) {
            frontendMembers.set(indexToFEIndex(j, maxLobbySize), new JsonObject());
        }

        frontendData.add("gameConfig", frontendGameConfig);
        frontendData.add("members", frontendMembers);
        frontendData.add("localMember", frontendLocalMember);
        return Optional.of(frontendData);
    }

    private int indexToFEIndex(int preParsedIndex, int maxLobbySize) {
        int actualIndex = 0;
        int diff = indexDiff(preParsedIndex);

        actualIndex = maxLobbySize/2 + diff;
        return actualIndex;
    }

    private int indexDiff(int index) {
        if (index % 2 == 0) {
            index /= 2;
            return index;
        } else return -indexDiff(index + 1);
    }

    private JsonObject backendToFrontendLobbyMember(JsonObject member) {
        JsonObject frontendMember = member;

        JsonObject regalia = mainInitiator.getReworkedDataManager().getMapManagers(RegaliaManager.class).get(member.get("summonerId").getAsBigInteger());
        frontendMember.add("regalia", regalia);

       return frontendMember;
    }


    public void doShutdown() {

    }
}
