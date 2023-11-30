package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import org.json.JSONObject;

import java.util.Optional;

public class PatcherData extends StateDataManager {

    private final static String PATCHER_URI = "/patcher/v1/products/league_of_legends/state";

    public PatcherData(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return PATCHER_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Delete":
                break;
            case "Create":
            case "Update":
                if (data.similar(currentState)) return;
                currentState = data;
                sendCurrentState();
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JSONObject> fetchCurrentState() {
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, PATCHER_URI));
        if (!data.has("errorCode")) return Optional.of(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("PatcherUpdate", currentState));
    }
}
