package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import java.util.Optional;

public class LootData extends StateDataManager {

    private static final String LOOT_URI = "/lol-loot/v2/player-loot-map";

    public LootData(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return LOOT_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
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
        JsonObject data = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-loot/v2/player-loot-map"));
        if (!data.has("errorCode")) return backendToFrontendLoot(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(ReworkedDataManager.UPDATE_TYPE_LOOT, currentState));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_LOOT;
    }
}
