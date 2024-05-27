package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameflowData extends StateDataManager {


    private static final Pattern lolGameflowV1SessionPattern = Pattern.compile("/lol-gameflow/v1/session$");

    public GameflowData(Starter starter) {
        super(starter);
    }

    @Override
    public void doInitialize() {
    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return lolGameflowV1SessionPattern.matcher(uri);
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
                Optional<JsonObject> updatedFEData = backendToFrontendGameflow(data.getAsJsonObject());
                if (!updatedFEData.isPresent()) return;
                JsonObject updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    private Optional<JsonObject> backendToFrontendGameflow(JsonObject data) {
        JsonObject frontendData = new JsonObject();

        Optional<String> gameflowPhase = Util.getOptString(data, "phase");
        if (!gameflowPhase.isPresent()) return Optional.empty();
        frontendData.addProperty("phase", gameflowPhase.get());

        Util.copyJsonAttrib("gameDodge", data, frontendData);

        return Optional.of(frontendData);
    }

    @Override
    public void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-gameflow/v1/session"));
        if (!data.has("errorCode")) return backendToFrontendGameflow(data);
        String phase = (String) starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-gameflow/v1/gameflow-phase"));
        if (phase == null) return Optional.empty();
        JsonObject fallbackData = new JsonObject();
        fallbackData.addProperty("phase", phase.trim().replace("\"", ""));
        return Optional.of(fallbackData);
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(ReworkedDataManager.UPDATE_TYPE_GAMEFLOW_PHASE, currentState));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_GAMEFLOW_PHASE;
    }
}
