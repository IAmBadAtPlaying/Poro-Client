package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import java.util.Optional;

public class LootData extends StateDataManager {

    private static final String LOOT_URI = "/lol-loot/v2/player-loot-map";

    public LootData(MainInitiator mainInitiator) {
        super(mainInitiator);
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
            case "Delete":
                break;
            case "Create":
            case "Update":
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
        JsonObject data = mainInitiator.getConnectionManager().getResponseBodyAsJsonObject(mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-loot/v2/player-loot-map"));
        if (!data.has("errorCode")) return backendToFrontendLoot(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("LootUpdate", currentState));
    }
}
