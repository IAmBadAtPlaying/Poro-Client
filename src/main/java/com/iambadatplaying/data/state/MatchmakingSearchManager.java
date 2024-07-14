package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.DataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchmakingSearchManager extends StateDataManager {

    private static final Pattern MATCHMAKING_SEARCH_PATTERN = Pattern.compile("/lol-matchmaking/v1/search$");

    public MatchmakingSearchManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return MATCHMAKING_SEARCH_PATTERN.matcher(uri);
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
                Optional<JsonObject> updatedFEData = backendToFrontendMatchmakingSearch(data.getAsJsonObject());
                if (!updatedFEData.isPresent()) return;
                JsonObject updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }

    private Optional<JsonObject> backendToFrontendMatchmakingSearch(JsonObject data) {
        JsonObject frontendData = new JsonObject();

        if (!Util.jsonKeysPresent(data, "dodgeData", "estimatedQueueTime", "isCurrentlyInQueue", "readyCheck", "searchState", "timeInQueue"))
            return Optional.empty();
        Util.copyJsonAttributes(data, frontendData, "dodgeData", "estimatedQueueTime", "isCurrentlyInQueue", "readyCheck", "searchState", "timeInQueue", "lowPriorityData");

        return Optional.of(frontendData);
    }


    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-matchmaking/v1/search"));
        if (!data.has("errorCode")) return Optional.of(data);

        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(getEventName(), currentState));
    }

    @Override
    public String getEventName() {
        return DataManager.UPDATE_TYPE_MATCHMAKING_SEARCH_STATE;
    }
}
