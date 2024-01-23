package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import java.util.Optional;

public class PatcherData extends StateDataManager {

    private final static String PATCHER_URI = "/patcher/v1/products/league_of_legends/state";

    public PatcherData(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return PATCHER_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case "Delete":
                break;
            case "Create":
            case "Update":
                if (!data.isJsonObject()) return;
                JsonObject updatedState = data.getAsJsonObject();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        JsonObject data = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, PATCHER_URI));
        if (!data.has("errorCode")) return Optional.of(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(ReworkedDataManager.UPDATE_TYPE_PATCHER, currentState));
    }
}
