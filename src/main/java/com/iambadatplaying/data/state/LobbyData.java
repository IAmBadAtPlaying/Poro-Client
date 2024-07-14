package com.iambadatplaying.data.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.DataManager;
import com.iambadatplaying.data.map.GameNameManager;
import com.iambadatplaying.data.map.RegaliaManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyData extends StateDataManager {

    private static final String UPDATE_TYPE_LOBBY = DataManager.UPDATE_TYPE_LOBBY;

    private static final Pattern LOBBY_URI_PATTERN = Pattern.compile("/lol-lobby/v2/lobby$");

    public LobbyData(Starter starter) {
        super(starter);
    }

    @Override
    public void doInitialize() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-lobby/v2/lobby");
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(con);
        if (!data.has("errorCode")) return backendToFrontendLobby(data);
        log("Cant fetch current state, maybe not in a lobby ?: " + data.get("message").getAsString(), Starter.LOG_LEVEL.WARN);
        return Optional.empty();
    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return LOBBY_URI_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                resetState();
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
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
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_LOBBY, currentState));
    }

    private Optional<JsonObject> backendToFrontendLobby(JsonObject data) {
        JsonObject frontendData = new JsonObject();

        Util.copyJsonAttributes(data, frontendData, "partyId", "invitations", "gameConfig");

        Optional<JsonObject> optGameConfig = Util.getOptJSONObject(data, "gameConfig");
        if (!optGameConfig.isPresent()) {
            log("Failed to get gameConfig", Starter.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonObject gameConfig = optGameConfig.get();

        Optional<JsonObject> optLocalMember = Util.getOptJSONObject(data, "localMember");

        if (!optLocalMember.isPresent()) {
            log("Failed to get localMember", Starter.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonObject localMember = optLocalMember.get();
        JsonObject frontendLocalMember = backendToFrontendLobbyMember(localMember);

        Optional<JsonArray> optMembers = Util.getOptJSONArray(data, "members");
        if (!optMembers.isPresent()) {
            log("Failed to get members", Starter.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonArray allowablePremadeSizes = gameConfig.get("allowablePremadeSizes").getAsJsonArray();
        Integer maxLobbySize = 1;
        for (int i = 0; i < allowablePremadeSizes.size(); i++) {
            int currentSize = allowablePremadeSizes.get(i).getAsInt();
            if (currentSize > maxLobbySize) {
                maxLobbySize = currentSize;
            }
        }

        //Logic breaks in custom games
        JsonArray members = optMembers.get();
        JsonArray frontendMembers = new JsonArray();
        for (int i = 0; i < maxLobbySize; i++) {
            frontendMembers.add(new JsonObject());
        }
        int j = 0;
        frontendMembers.set(indexToFEIndex(0, maxLobbySize), frontendLocalMember);
        j++;
        for (int i = 0; i < members.size(); i++) {
            int actualIndex = indexToFEIndex(j, maxLobbySize);
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

        frontendData.add("members", frontendMembers);
        frontendData.add("localMember", frontendLocalMember);
        return Optional.of(frontendData);
    }

    private int indexToFEIndex(int preParsedIndex, int maxLobbySize) {
        int actualIndex = 0;
        int diff = indexDiff(preParsedIndex);

        actualIndex = maxLobbySize / 2 + diff;
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


        Optional<JsonObject> summonerByPuuid = starter.getDataManager().getMapManagers(GameNameManager.class).get(member.get("puuid").getAsString());
        summonerByPuuid.ifPresent(
                summoner -> {
                    String gameName = summoner.get("gameName").getAsString();
                    String tagLine = summoner.get("tagLine").getAsString();
                    frontendMember.addProperty("gameName", gameName);
                    frontendMember.addProperty("gameTag", tagLine);
                }
        );

        starter
                .getDataManager()
                .getMapManagers(RegaliaManager.class)
                .get(member.get("summonerId").getAsBigInteger())
                .ifPresent(
                        regalia -> frontendMember.add("regalia", regalia)
                );

        return frontendMember;
    }


    public void doShutdown() {

    }

    @Override
    public String getEventName() {
        return DataManager.UPDATE_TYPE_LOBBY;
    }
}
