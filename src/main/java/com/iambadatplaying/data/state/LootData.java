package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import org.json.JSONObject;

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
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Delete":
                break;
            case "Create":
            case "Update":
                Optional<JSONObject> updatedFEData = backendToFrontendLoot(data);
                if (!updatedFEData.isPresent()) return;
                JSONObject updatedState = updatedFEData.get();
                if (updatedState.similar(currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    private Optional<JSONObject> backendToFrontendLoot(JSONObject data) {
        return Util.getOptJSONObject(data, "playerLoot");
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JSONObject> fetchCurrentState() {
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-loot/v2/player-loot-map"));
        if (!data.has("errorCode")) return backendToFrontendLoot(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("LootUpdate", currentState));
    }
}
