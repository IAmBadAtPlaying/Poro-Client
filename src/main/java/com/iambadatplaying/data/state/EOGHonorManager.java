package com.iambadatplaying.data.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.DataManager;
import com.iambadatplaying.data.map.GameNameManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EOGHonorManager extends StateDataManager {

    private static final String UPDATE_TYPE_HONOR_EOG = DataManager.UPDATE_TYPE_HONOR_EOG;

    private static final Pattern HONOR_BALLOT_PATTERN = Pattern.compile("/lol-honor-v2/v1/ballot$");

    public EOGHonorManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return HONOR_BALLOT_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonObject()) return;
                Optional<JsonObject> updatedState = backendHonorToFrontendHonor(data.getAsJsonObject());
                if (!updatedState.isPresent()) return;
                JsonObject newState = updatedState.get();
                if (Util.equalJsonElements(newState, currentState)) return;
                currentState = newState;
                sendCurrentState();
                break;
            case UPDATE_TYPE_DELETE:
                resetState();
                break;
        }
    }

    private Optional<JsonObject> backendHonorToFrontendHonor(JsonObject data) {
        JsonObject frontendData = new JsonObject();
        Util.copyJsonAttributes(data, frontendData, "gameId");
        JsonArray eligiblePlayers;
        if (Util.jsonKeysPresent(data, "eligiblePlayers")) {
            eligiblePlayers = data.getAsJsonArray("eligiblePlayers");
        } else if (Util.jsonKeysPresent(data, "eligibleAllies")) {
            eligiblePlayers = data.getAsJsonArray("eligibleAllies");
        } else {
            return Optional.empty();
        }
        JsonArray feEligiblePlayers = new JsonArray();
        for (int i = 0, size = eligiblePlayers.size(); i < size; i++) {
            JsonObject player = eligiblePlayers.get(i).getAsJsonObject();
            JsonObject fePlayer = new JsonObject();
            Util.copyJsonAttributes(player, fePlayer, "championName", "skinSplashPath", "summonerName", "puuid", "summonerId");
            String puuid = player.get("puuid").getAsString();
            starter.getDataManager()
                    .getMapManager(GameNameManager.class)
                    .get(puuid)
                    .ifPresent(
                            gameName ->
                                    fePlayer.addProperty(
                                            "gameName",
                                            gameName.get("gameName").getAsString()
                                    )
                    );
            feEligiblePlayers.add(fePlayer);
        }
        frontendData.add("eligiblePlayers", feEligiblePlayers);
        return Optional.of(frontendData);
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-honor-v2/v1/ballot");
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(con);
        if (data.has("errorCode")) return Optional.empty();
        return backendHonorToFrontendHonor(data);
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_HONOR_EOG, currentState));
    }

    @Override
    public String getEventName() {
        return DataManager.UPDATE_TYPE_HONOR_EOG;
    }
}
