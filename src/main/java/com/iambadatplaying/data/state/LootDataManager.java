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

public class LootDataManager extends StateDataManager {

    private static final Pattern LOOT_URI_PATTERN = Pattern.compile("/lol-loot/v2/player-loot-map$");

    public LootDataManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return LOOT_URI_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonObject()) return;
                Optional<JsonObject> updatedFEData = backendToFrontendLoot(data.getAsJsonObject());
                if (!updatedFEData.isPresent()) return;
                JsonObject updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    private Optional<JsonObject> backendToFrontendLoot(JsonObject data) {
        return Util.getOptJSONObject(data, "playerLoot");
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-loot/v2/player-loot-map"));
        if (!data.has("errorCode")) return backendToFrontendLoot(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(DataManager.UPDATE_TYPE_LOOT, currentState));
    }

    @Override
    public String getEventName() {
        return DataManager.UPDATE_TYPE_LOOT;
    }
}
