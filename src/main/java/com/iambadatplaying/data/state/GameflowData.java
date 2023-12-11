package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONObject;

import java.util.Optional;

public class GameflowData extends StateDataManager {

    private static final String lolGameflowV1SessionPattern = "/lol-gameflow/v1/session";

    public GameflowData(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    @Override
    public void doInitialize() {
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return lolGameflowV1SessionPattern.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Delete":
                resetState();
                break;
            case "Create":
            case "Update":
                Optional<JSONObject> updatedFEData = backendToFrontendGameflow(data);
                if (!updatedFEData.isPresent()) return;
                JSONObject updatedState = updatedFEData.get();
                if (updatedState.similar(currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    private Optional<JSONObject> backendToFrontendGameflow(JSONObject data) {
        JSONObject frontendData = new JSONObject();

        Optional<String> gameflowPhase = Util.getOptString(data, "phase");
        if (!gameflowPhase.isPresent()) return Optional.empty();
        frontendData.put("phase", gameflowPhase.get());

        Util.copyJsonAttrib("gameDodge", data, frontendData);

        return Optional.of(frontendData);
    }

    @Override
    public void doShutdown() {

    }

    @Override
    protected Optional<JSONObject> fetchCurrentState() {
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, lolGameflowV1SessionPattern));
        if (!data.has("errorCode")) return backendToFrontendGameflow(data);
        String phase = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-gameflow/v1/gameflow-phase"));
        if (phase == null) return Optional.empty();
        JSONObject fallbackData = new JSONObject();
        fallbackData.put("phase", phase.trim().replace("\"", ""));
        return Optional.of(fallbackData);
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(com.iambadatplaying.lcuHandler.DataManager.getEventDataString("GameflowPhaseUpdate", currentState));
    }
}
